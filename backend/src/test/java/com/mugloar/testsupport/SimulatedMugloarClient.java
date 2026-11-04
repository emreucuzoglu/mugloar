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
import java.util.EnumMap;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
  private static final int REWARD_PER_LEVEL = 6;
  private static final int FIRST_TENS_OF_LEVELS = 10;
  private static final int SECOND_TENS_OF_LEVELS = 20;
  private static final int THIRD_TENS_OF_LEVELS = 30;
  private static final double STEPS_PER_LEVEL_BELOW_10 = 0.115;
  private static final double STEPS_PER_LEVEL_BELOW_20 = 0.038;
  private static final double STEPS_PER_LEVEL_BELOW_30 = 0.003;
  private static final int TURNS_PER_BAND = 10;

  /**
   * Labels of new jobs per ten turns before turn 40, as an index into {@link #LABELS}: their mean
   * and spread in the games that rotated the big items.
   */
  private static final double[] EARLY_LABEL_MEAN_BY_BAND = {2.74, 3.52, 4.73, 3.82};

  private static final double[] EARLY_LABEL_SPREAD_BY_BAND = {2.27, 2.62, 3.11, 2.78};

  private static final double LABEL_AT_TURN_ZERO = -3.185;

  private static final double LABEL_PER_TURN = 0.1792;
  private static final double LABEL_PER_ROUND = 1.427;
  private static final double LATE_LABEL_SPREAD = 2.17;
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
  private long gold;
  private int level;
  private long score;
  private int turn;
  private int nextId;
  private double stateReputation;

  public int solveCalls;
  public int purchases;
  public int trapsTaken;
  public int avoidableTraps;
  public int callsAfterGameOver;
  public int refusedPurchases;
  public int lostReplies;

  private final List<String> bought = new ArrayList<>();
  private final Map<Item, Integer> bigItemCounts = new EnumMap<>(Item.class);
  private final Random faults;
  private final double serverErrorRate;
  private final double lostReplyRate;

  public SimulatedMugloarClient(long seed) {
    this(seed, 0);
  }

  public SimulatedMugloarClient(long seed, double serverErrorRate) {
    this(seed, serverErrorRate, 0);
  }

  /**
   * @param serverErrorRate share of calls that fail before the server acts on them
   * @param lostReplyRate share of moves the server makes but whose answer never arrives
   */
  public SimulatedMugloarClient(long seed, double serverErrorRate, double lostReplyRate) {
    super(null, null, null);
    this.random = new Random(seed);
    this.faults = new Random(seed);
    this.serverErrorRate = serverErrorRate;
    this.lostReplyRate = lostReplyRate;
  }

  public double stateReputation() {
    return stateReputation;
  }

  public long gold() {
    return gold;
  }

  public int lives() {
    return lives;
  }

  public int level() {
    return level;
  }

  public int turn() {
    return turn;
  }

  /** How many more times the most bought +2 item was bought than the least bought one. */
  public int bigItemSpread() {
    IntSummaryStatistics counts =
        Arrays.stream(Item.values())
            .filter(item -> item.levelsGained() == BIG_ITEM_LEVELS)
            .mapToInt(item -> bigItemCounts.getOrDefault(item, 0))
            .summaryStatistics();
    return counts.getMax() - counts.getMin();
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
    maybeLoseTheReply();
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
      if (item.levelsGained() == BIG_ITEM_LEVELS) {
        bigItemCounts.merge(item, 1, Integer::sum);
      }
      gold -= item.cost();
      lives += item.livesGained();
      gainLevels(item.levelsGained());
    } else {
      refusedPurchases++;
    }
    advanceTurn();
    maybeLoseTheReply();
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

  private int roundsOfBigItems() {
    return Arrays.stream(Item.values())
        .filter(item -> item.levelsGained() == BIG_ITEM_LEVELS)
        .mapToInt(item -> bigItemCounts.getOrDefault(item, 0))
        .min()
        .orElse(0);
  }

  private double difficultyOfANewJob() {
    int band = turn / TURNS_PER_BAND;
    if (band < EARLY_LABEL_MEAN_BY_BAND.length) {
      return EARLY_LABEL_MEAN_BY_BAND[band]
          + random.nextGaussian() * EARLY_LABEL_SPREAD_BY_BAND[band];
    }
    return LABEL_AT_TURN_ZERO
        + LABEL_PER_TURN * turn
        - LABEL_PER_ROUND * roundsOfBigItems()
        + random.nextGaussian() * LATE_LABEL_SPREAD;
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

  /** Draws only when lost replies are on, so seeds without them play exactly as before. */
  private void maybeLoseTheReply() {
    if (lostReplyRate > 0 && faults.nextDouble() < lostReplyRate) {
      lostReplies++;
      throw new MugloarApiException(SERVICE_UNAVAILABLE, "simulated lost reply");
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
