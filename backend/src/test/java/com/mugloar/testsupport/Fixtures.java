package com.mugloar.testsupport;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Item;
import com.mugloar.domain.Probability;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestEvaluation;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.SolveResult;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.MugloarGateway;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.gateway.dto.GameStartDTO;
import com.mugloar.gateway.dto.PurchaseResultDTO;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.gateway.dto.ReputationDTO;
import com.mugloar.gateway.dto.ShopItemDTO;
import com.mugloar.gateway.dto.SolveResultDTO;
import com.mugloar.solver.AutoPlayer;
import com.mugloar.solver.TurnEvent;
import com.mugloar.solver.strategy.BalancedShoppingStrategy;
import com.mugloar.solver.strategy.RiskAwareQuestSelector;
import com.mugloar.solver.strategy.SelectorSettings;
import com.mugloar.solver.strategy.ShoppingSettings;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.Setter;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class Fixtures {

  public static final String GAME_ID = "g1";

  public static final GameState GAME_STATE = aGameState().build();
  public static final Quest QUEST = aQuest().build();
  public static final ShopItem POTION = new ShopItem("hpot", "Healing potion", 50);
  public static final List<ShopItem> SHOP_ITEMS =
      Arrays.stream(Item.values()).map(Fixtures::shopItem).toList();

  public static final GameStartDTO GAME_START_DTO = aGameStartDto().build();
  public static final QuestDTO QUEST_DTO = aQuestDto().build();
  public static final ShopItemDTO POTION_DTO = new ShopItemDTO("hpot", "Healing potion", 50);
  public static final SolveResultDTO SOLVE_RESULT_DTO = aSolveResultDto().build();
  public static final PurchaseResultDTO PURCHASE_RESULT_DTO = aPurchaseResultDto().build();
  public static final ReputationDTO REPUTATION_DTO = aReputationDto().build();

  public static final RiskAwareQuestSelector QUEST_SELECTOR =
      new RiskAwareQuestSelector(SelectorSettings.defaults());
  public static final BalancedShoppingStrategy SHOPPING_STRATEGY =
      new BalancedShoppingStrategy(ShoppingSettings.defaults());

  /** The game server's own text for a quest that has gone. */
  public static final MugloarApiException REJECTED =
      new MugloarApiException(400, "No ad by this ID exists");

  public static final MugloarApiException SERVER_DOWN = new MugloarApiException(503, "down");
  public static final GameOverException GAME_OVER = new GameOverException(GAME_ID);

  public static MugloarGateway gateway(MugloarClient client) {
    return new MugloarGateway(client);
  }

  public static AutoPlayer autoPlayer(MugloarClient client) {
    return new AutoPlayer(gateway(client), QUEST_SELECTOR, SHOPPING_STRATEGY);
  }

  public static ShopItem shopItem(String id, String name, int cost) {
    return new ShopItem(id, name, cost);
  }

  private static ShopItem shopItem(Item item) {
    return new ShopItem(item.id(), item.itemName(), item.cost());
  }

  public static String b64(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  public static GameStateBuilder aGameState() {
    return new GameStateBuilder();
  }

  public static QuestBuilder aQuest() {
    return new QuestBuilder();
  }

  public static QuestEvaluationBuilder aQuestEvaluation() {
    return new QuestEvaluationBuilder();
  }

  public static SolveResultBuilder aSolveResult() {
    return new SolveResultBuilder();
  }

  public static PurchaseResultBuilder aPurchaseResult() {
    return new PurchaseResultBuilder();
  }

  public static ReputationBuilder aReputation() {
    return new ReputationBuilder();
  }

  public static TurnEventBuilder aTurnEvent() {
    return new TurnEventBuilder();
  }

  public static GameStartDtoBuilder aGameStartDto() {
    return new GameStartDtoBuilder();
  }

  public static QuestDtoBuilder aQuestDto() {
    return new QuestDtoBuilder();
  }

  public static SolveResultDtoBuilder aSolveResultDto() {
    return new SolveResultDtoBuilder();
  }

  public static PurchaseResultDtoBuilder aPurchaseResultDto() {
    return new PurchaseResultDtoBuilder();
  }

  public static ReputationDtoBuilder aReputationDto() {
    return new ReputationDtoBuilder();
  }

  public static SelectorSettingsBuilder aSelectorSettings() {
    return new SelectorSettingsBuilder();
  }

  public static ShoppingSettingsBuilder aShoppingSettings() {
    return new ShoppingSettingsBuilder();
  }

  @Setter
  public static final class GameStateBuilder {
    private String gameId = GAME_ID;
    private int lives = 3;
    private long gold;
    private int level;
    private long score;
    private long highScore;
    private int turn;
    private double stateReputation;

    public GameState build() {
      return new GameState(gameId, lives, gold, level, score, highScore, turn, stateReputation);
    }
  }

  @Setter
  public static final class QuestBuilder {
    private String questId = "a";
    private String rawQuestId;
    private String message = "Job";
    private int reward = 10;
    private int expiresIn = 1;
    private Probability probability = Probability.SURE_THING;
    private boolean encrypted;

    public Quest build() {
      String raw = rawQuestId == null ? questId : rawQuestId;
      return new Quest(questId, raw, message, reward, expiresIn, probability, encrypted);
    }
  }

  @Setter
  public static final class QuestEvaluationBuilder {
    private Quest quest = aQuest().build();
    private double successChance = 0.97;
    private double expectedValue = 17.6;
    private boolean acceptable = true;

    public QuestEvaluation build() {
      return new QuestEvaluation(quest, successChance, expectedValue, acceptable);
    }
  }

  @Setter
  public static final class SolveResultBuilder {
    private boolean success = true;
    private int lives = 3;
    private long gold = 20;
    private long score = 20;
    private long highScore = 20;
    private int turn = 1;
    private String message = "ok";

    public SolveResult build() {
      return new SolveResult(success, lives, gold, score, highScore, turn, message);
    }
  }

  @Setter
  public static final class PurchaseResultBuilder {
    private boolean shoppingSuccess = true;
    private long gold = 10;
    private int lives = 3;
    private int level;
    private int turn = 1;

    public PurchaseResult build() {
      return new PurchaseResult(shoppingSuccess, gold, lives, level, turn);
    }
  }

  @Setter
  public static final class ReputationBuilder {
    private double people;
    private double state;
    private double underworld;

    public Reputation build() {
      return new Reputation(people, state, underworld);
    }
  }

  @Setter
  public static final class TurnEventBuilder {
    private TurnEvent.Type type = TurnEvent.Type.SOLVE;
    private boolean success = true;
    private String message = "Succeeded";
    private GameState state = aGameState().build();

    public TurnEvent build() {
      return new TurnEvent(type, success, message, state);
    }
  }

  @Setter
  public static final class GameStartDtoBuilder {
    private String gameId = GAME_ID;
    private int lives = 3;
    private long gold;
    private int level;
    private long score;
    private long highScore;
    private int turn;

    public GameStartDTO build() {
      return new GameStartDTO(gameId, lives, gold, level, score, highScore, turn);
    }
  }

  @Setter
  public static final class QuestDtoBuilder {
    private String questId = "a";
    private String message = "Job";
    private int reward = 20;
    private int expiresIn = 5;
    private Integer encrypted;
    private String probability = "Sure thing";

    public QuestDTO build() {
      return new QuestDTO(questId, message, reward, expiresIn, encrypted, probability);
    }
  }

  @Setter
  public static final class SolveResultDtoBuilder {
    private boolean success = true;
    private int lives = 3;
    private long gold = 20;
    private long score = 20;
    private long highScore = 20;
    private int turn = 1;
    private String message = "ok";

    public SolveResultDTO build() {
      return new SolveResultDTO(success, lives, gold, score, highScore, turn, message);
    }
  }

  @Setter
  public static final class PurchaseResultDtoBuilder {
    private boolean shoppingSuccess = true;
    private long gold = 10;
    private int lives = 3;
    private int level;
    private int turn = 1;

    public PurchaseResultDTO build() {
      return new PurchaseResultDTO(shoppingSuccess, gold, lives, level, turn);
    }
  }

  @Setter
  public static final class ReputationDtoBuilder {
    private double people;
    private double state;
    private double underworld;

    public ReputationDTO build() {
      return new ReputationDTO(people, state, underworld);
    }
  }

  @Setter
  public static final class SelectorSettingsBuilder {
    private double minChanceHealthy = 0.6;
    private double minChanceWounded = 0.7;
    private double minChanceCritical = 0.9;
    private double lifeCost = 60;

    public SelectorSettings build() {
      return new SelectorSettings(minChanceHealthy, minChanceWounded, minChanceCritical, lifeCost);
    }
  }

  @Setter
  public static final class ShoppingSettingsBuilder {
    private int healBelowLives = 3;
    private int goldReserve = 50;
    private int smallUpgradesUntilTurn;
    private int harvestAtSureThings = 7;

    public ShoppingSettings build() {
      return new ShoppingSettings(
          healBelowLives, goldReserve, smallUpgradesUntilTurn, harvestAtSureThings);
    }
  }
}
