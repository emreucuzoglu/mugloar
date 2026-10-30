package com.mugloar.testsupport;

import com.mugloar.domain.Item;
import com.mugloar.domain.Probability;
import com.mugloar.domain.QuestType;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.gateway.dto.GameStartDTO;
import com.mugloar.gateway.dto.PurchaseResultDTO;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.gateway.dto.ReputationDTO;
import com.mugloar.gateway.dto.ShopItemDTO;
import com.mugloar.gateway.dto.SolveResultDTO;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SimulatedMugloarClient extends MugloarClient {

  private static final Probability[] LABELS = {
    Probability.SURE_THING,
    Probability.PIECE_OF_CAKE,
    Probability.WALK_IN_THE_PARK,
    Probability.QUITE_LIKELY,
    Probability.HMMM,
    Probability.GAMBLE,
    Probability.RISKY,
    Probability.RATHER_DETRIMENTAL,
    Probability.PLAYING_WITH_FIRE,
    Probability.SUICIDE_MISSION,
    Probability.IMPOSSIBLE
  };

  private static final int BOARD_SIZE = 10;
  private static final double TRAP_SHARE = 0.38;
  private static final int REWARD_PER_LEVEL = 5;
  private static final int FIRST_TENS_OF_LEVELS = 10;
  private static final int SECOND_TENS_OF_LEVELS = 20;
  private static final int THIRD_TENS_OF_LEVELS = 30;
  private static final double STEPS_PER_LEVEL_BELOW_10 = 0.115;
  private static final double STEPS_PER_LEVEL_BELOW_20 = 0.038;
  private static final double STEPS_PER_LEVEL_BELOW_30 = 0.003;
  private static final int BIG_ITEMS_IN_THE_SET = 5;
  private static final int SET_EFFECT_FIRST_TURN = 40;
  private static final int SET_EFFECT_LAST_TURN = 69;
  private static final double SET_EFFECT_LABEL_STEPS = 1;
  private static final int TURNS_PER_BAND = 10;

  /**
   * Labels of new jobs per ten turns, from the games that bought one of each big item: their mean
   * and spread as an index into {@link #LABELS}. From turn 140 every job is "Impossible".
   */
  private static final double[] LABEL_MEAN_BY_BAND = {
    2.70, 3.46, 4.76, 3.88, 3.16, 4.70, 6.37, 7.75, 8.70, 9.32, 9.63, 9.84, 9.94, 9.99, 10
  };

  private static final double[] LABEL_SPREAD_BY_BAND = {
    2.25, 2.57, 3.07, 2.81, 2.18, 2.48, 2.38, 1.91, 1.35, 0.80, 0.51, 0.38, 0.23, 0.11, 0
  };
  private static final int BIG_ITEM_LEVELS = 2;
  private static final int SERVICE_UNAVAILABLE = 503;

  public static final List<ShopItemDTO> SHOP =
      Arrays.stream(Item.values())
          .map(item -> new ShopItemDTO(item.id(), item.itemName(), item.cost()))
          .toList();

  private final Random random;
  private final String gameId = "SIMGAME1";
  private final List<Entry> board = new ArrayList<>();
  private int lives = 3;
  private int gold;
  private int level;
  private int score;
  private int turn;
  private int nextId;
  private double stateReputation;

  public int solveCalls;
  public int purchases;
  public int trapsTaken;
  public int avoidableTraps;
  public int callsAfterGameOver;

  private final List<String> bought = new ArrayList<>();
  private final Random faults;
  private final double serverErrorRate;

  public SimulatedMugloarClient(long seed) {
    this(seed, 0);
  }

  public SimulatedMugloarClient(long seed, double serverErrorRate) {
    super(null, null, null);
    this.random = new Random(seed);
    this.faults = new Random(seed);
    this.serverErrorRate = serverErrorRate;
  }

  public double stateReputation() {
    return stateReputation;
  }

  public List<String> bigItemsBought() {
    return bought.stream()
        .filter(id -> Item.fromId(id).orElseThrow().levelsGained() == BIG_ITEM_LEVELS)
        .toList();
  }

  @Override
  public GameStartDTO startGame() {
    refill();
    return new GameStartDTO(gameId, lives, gold, level, score, 0, turn);
  }

  @Override
  public List<QuestDTO> getMessages(String id) {
    requireLive(id);
    return board.stream().map(Entry::toWire).toList();
  }

  @Override
  public SolveResultDTO solve(String id, String questId) {
    requireLive(id);
    Entry quest =
        board.stream()
            .filter(e -> e.id.equals(questId))
            .findFirst()
            .orElseThrow(() -> new MugloarApiException(400, "No ad by this ID exists"));
    solveCalls++;
    board.remove(quest);
    boolean trap = quest.type.isTrap();
    trapsTaken += trap ? 1 : 0;
    avoidableTraps += trap && board.stream().anyMatch(e -> !e.type.isTrap()) ? 1 : 0;
    boolean success = !trap && random.nextDouble() < quest.label().chance();
    if (success) {
      gold += quest.reward;
      score += quest.reward;
      stateReputation += quest.type.reputationChange().state();
    } else {
      lives--;
    }
    advanceTurn();
    String message =
        trap
            ? "You fell into a trap"
            : success ? "You successfully solved the mission!" : "You failed on the mission!";
    return new SolveResultDTO(success, lives, gold, score, score, turn, message);
  }

  @Override
  public List<ShopItemDTO> getShop(String id) {
    requireLive(id);
    return SHOP;
  }

  /** Like the live game, a refused or unknown purchase is not an error and still costs a turn. */
  @Override
  public PurchaseResultDTO buy(String id, String itemId) {
    requireLive(id);
    Item item = Item.fromId(itemId).filter(i -> gold >= i.cost()).orElse(null);
    if (item != null) {
      purchases++;
      bought.add(item.id());
      gold -= item.cost();
      lives += item.livesGained();
      gainLevels(item.levelsGained());
    }
    advanceTurn();
    return new PurchaseResultDTO(item != null, gold, lives, level, turn);
  }

  @Override
  public ReputationDTO investigateReputation(String id) {
    requireLive(id);
    advanceTurn();
    return new ReputationDTO(0, stateReputation, 0);
  }

  private void gainLevels(int levels) {
    for (int i = 0; i < levels; i++) {
      double steps = labelStepsAtLevel(level);
      board.stream().filter(e -> !e.type.isTrap()).forEach(e -> e.difficulty -= steps);
      level++;
    }
  }

  private static double labelStepsAtLevel(int level) {
    if (level < FIRST_TENS_OF_LEVELS) {
      return STEPS_PER_LEVEL_BELOW_10;
    }
    if (level < SECOND_TENS_OF_LEVELS) {
      return STEPS_PER_LEVEL_BELOW_20;
    }
    return level < THIRD_TENS_OF_LEVELS ? STEPS_PER_LEVEL_BELOW_30 : 0;
  }

  private double missingSetPenalty() {
    boolean ownsTheSet = bigItemsBought().stream().distinct().count() >= BIG_ITEMS_IN_THE_SET;
    boolean inWindow = turn >= SET_EFFECT_FIRST_TURN && turn <= SET_EFFECT_LAST_TURN;
    return !ownsTheSet && inWindow ? SET_EFFECT_LABEL_STEPS : 0;
  }

  private double difficultyOfANewJob() {
    int band = Math.min(turn / TURNS_PER_BAND, LABEL_MEAN_BY_BAND.length - 1);
    return LABEL_MEAN_BY_BAND[band]
        + random.nextGaussian() * LABEL_SPREAD_BY_BAND[band]
        + missingSetPenalty();
  }

  private void requireLive(String id) {
    if (!gameId.equals(id) || lives <= 0) {
      callsAfterGameOver++;
      throw new GameOverException(id);
    }
    if (faults.nextDouble() < serverErrorRate) {
      throw new MugloarApiException(SERVICE_UNAVAILABLE, "simulated outage");
    }
  }

  private void advanceTurn() {
    turn++;
    for (Iterator<Entry> it = board.iterator(); it.hasNext(); ) {
      Entry e = it.next();
      if (--e.expiresIn <= 0) {
        it.remove();
      }
    }
    refill();
  }

  private void refill() {
    while (board.size() < BOARD_SIZE) {
      board.add(newEntry());
    }
  }

  private Entry newEntry() {
    boolean trap =
        stateReputation <= QuestType.TRAP_STATE_REPUTATION && random.nextDouble() < TRAP_SHARE;
    QuestType type = trap ? QuestType.STEAL_DIAMOND : randomTypeForThisTurn();
    double difficulty = trap ? random.nextInt(2) : difficultyOfANewJob();
    int reward =
        type.rewardMin()
            + random.nextInt(type.rewardMax() - type.rewardMin() + 1)
            + level * REWARD_PER_LEVEL;
    boolean encrypted =
        type == QuestType.INVESTIGATE || type == QuestType.INFILTRATE || type == QuestType.KILL;
    int encoding = !encrypted ? 0 : random.nextInt(8) == 0 ? 2 : 1;
    return new Entry(
        "quest" + (nextId++),
        messageFor(type, nextId),
        reward,
        encrypted ? 3 : 7,
        type,
        difficulty,
        encoding);
  }

  private QuestType randomTypeForThisTurn() {
    List<QuestType> available =
        Arrays.stream(QuestType.values())
            .filter(type -> type != QuestType.UNKNOWN && !type.isTrap())
            .filter(type -> type.firstTurn() <= turn && turn <= type.lastTurn())
            .toList();
    return available.get(random.nextInt(available.size()));
  }

  private static String messageFor(QuestType type, int n) {
    return switch (type) {
      case FIX -> "Help Someone" + n + " to fix their bucket";
      case CLEAN -> "Help Someone" + n + " to clean their dog";
      case NEGOTIATE -> "Help Someone" + n + " to reach an agreement";
      case TRANSPORT -> "Help Someone" + n + " to transport a magic cat";
      case SELL -> "Help Someone" + n + " to sell an unordinary squirrel";
      case WRITE -> "Help Someone" + n + " to write their biographical novel";
      case ADVERTISE -> "Create an advertisement campaign for Someone" + n;
      case ESCORT -> "Escort Someone" + n + " to pasture";
      case RESCUE -> "Rescue Someone" + n + " from a village";
      case INVESTIGATE -> "Investigate Someone" + n + " and find out their relation";
      case INFILTRATE -> "Infiltrate Group" + n + " and recover their secrets.";
      case KILL -> "Kill Someone" + n + " with pot";
      case DEFEND -> "Help defending peninsula " + n + " from the intruders";
      case STEAL_DIAMOND -> "Steal super awesome diamond mug from Someone" + n;
      case STEAL_DELIVERY -> "Steal water delivery to Someone" + n;
      case UNKNOWN -> throw new IllegalArgumentException("never generated");
    };
  }

  private static final class Entry {
    final String id;
    final String message;
    final int reward;
    int expiresIn;
    final QuestType type;
    double difficulty;
    final int encoding;

    Entry(
        String id,
        String message,
        int reward,
        int expiresIn,
        QuestType type,
        double difficulty,
        int encoding) {
      this.id = id;
      this.message = message;
      this.reward = reward;
      this.expiresIn = expiresIn;
      this.type = type;
      this.difficulty = difficulty;
      this.encoding = encoding;
    }

    Probability label() {
      int index = (int) Math.round(difficulty);
      return LABELS[Math.max(0, Math.min(LABELS.length - 1, index))];
    }

    QuestDTO toWire() {
      String label = label().label();
      return switch (encoding) {
        case 1 -> new QuestDTO(b64(id), b64(message), reward, expiresIn, 1, b64(label));
        case 2 -> new QuestDTO(rot13(id), rot13(message), reward, expiresIn, 2, rot13(label));
        default -> new QuestDTO(id, message, reward, expiresIn, null, label);
      };
    }

    private static String b64(String s) {
      return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String rot13(String s) {
      StringBuilder out = new StringBuilder();
      for (char c : s.toCharArray()) {
        if (Character.isLowerCase(c)) {
          out.append((char) ('a' + (c - 'a' + 13) % 26));
        } else if (Character.isUpperCase(c)) {
          out.append((char) ('A' + (c - 'A' + 13) % 26));
        } else {
          out.append(c);
        }
      }
      return out.toString();
    }
  }
}
