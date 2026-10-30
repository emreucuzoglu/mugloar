package com.mugloar.solver.strategy;

import static com.mugloar.testsupport.Fixtures.QUEST_SELECTOR;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aQuestEvaluation;
import static com.mugloar.testsupport.Fixtures.aSelectorSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestEvaluation;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RiskAwareQuestSelectorTest {

  private static final int RANDOM_BOARDS = 500;
  private static final int MAX_BOARD = 10;
  private static final int MAX_REWARD = 400;
  private static final int MAX_REPUTATION_LOSS = 14;

  private static final String TRAP_MESSAGE =
      "Steal super awesome diamond beer mug from Ketzi'ah Huddleson";

  private final RiskAwareQuestSelector selector =
      new RiskAwareQuestSelector(
          aSelectorSettings()
              .minChanceHealthy(0.60)
              .minChanceWounded(0.75)
              .minChanceCritical(0.88)
              .build());

  private static GameState lives(int lives) {
    return aGameState().lives(lives).build();
  }

  private static Quest quest(String id, Probability p, int reward) {
    return quest(id, p, reward, 5, "Help someone");
  }

  private static Quest quest(String id, Probability p, int reward, int expiresIn, String message) {
    return aQuest()
        .questId(id)
        .message(message)
        .reward(reward)
        .expiresIn(expiresIn)
        .probability(p)
        .build();
  }

  private static GameState atState(double stateReputation) {
    return aGameState().stateReputation(stateReputation).build();
  }

  @Test
  void bestIsTheTopQuestWhenNoneIsAcceptable() {
    QuestEvaluation first =
        aQuestEvaluation()
            .quest(quest("a", Probability.RISKY, 90))
            .successChance(0.4)
            .expectedValue(0.0)
            .acceptable(false)
            .build();
    QuestEvaluation second =
        aQuestEvaluation()
            .quest(quest("b", Probability.IMPOSSIBLE, 90))
            .successChance(0.02)
            .expectedValue(-50.0)
            .acceptable(false)
            .build();

    assertThat(selector.best(List.of(first, second))).contains(first);
  }

  @Test
  void bestIsTheFirstAcceptableQuestEvenBelowARankedOne() {
    QuestEvaluation first =
        aQuestEvaluation()
            .quest(quest("a", Probability.RISKY, 90))
            .successChance(0.4)
            .expectedValue(0.0)
            .acceptable(false)
            .build();
    QuestEvaluation second =
        aQuestEvaluation().quest(quest("b", Probability.SURE_THING, 20)).build();

    assertThat(selector.best(List.of(first, second))).contains(second);
  }

  @Test
  void refusesAStealThatWouldBringTraps() {
    Quest steal =
        quest("steal", Probability.PIECE_OF_CAKE, 80, 5, "Steal dog delivery from someone");
    Quest job = quest("job", Probability.WALK_IN_THE_PARK, 10);

    assertThat(selector.choose(List.of(steal, job), atState(-6)).orElseThrow().quest())
        .isEqualTo(steal);
    assertThat(selector.choose(List.of(steal, job), atState(-8)).orElseThrow().quest())
        .isEqualTo(job);
  }

  @Test
  void stealsAgainOnceStateIsAlreadyPastTheTrapLevel() {
    Quest steal =
        quest("steal", Probability.PIECE_OF_CAKE, 80, 5, "Steal dog delivery from someone");

    assertThat(selector.evaluate(steal, atState(-10)).acceptable()).isTrue();
  }

  @Test
  void takesAStealThatWouldBringTrapsWhenNothingElseIsLeft() {
    Quest steal =
        quest("steal", Probability.PIECE_OF_CAKE, 80, 5, "Steal dog delivery from someone");

    assertThat(selector.choose(List.of(steal), atState(-8)).orElseThrow().quest()).isEqualTo(steal);
  }

  @Test
  void prefersHigherExpectedValueAmongSafeQuests() {
    List<Quest> board =
        List.of(
            quest("small", Probability.PIECE_OF_CAKE, 3),
            quest("big", Probability.PIECE_OF_CAKE, 37),
            quest("medium", Probability.WALK_IN_THE_PARK, 24));

    assertThat(selector.choose(board, lives(3)).orElseThrow().quest().questId()).isEqualTo("big");
  }

  @Test
  void skipsRiskyQuestEvenWhenItPaysMore() {
    List<Quest> board =
        List.of(
            quest("risky", Probability.RISKY, 300),
            quest("safe", Probability.WALK_IN_THE_PARK, 61));

    QuestEvaluation choice = selector.choose(board, lives(3)).orElseThrow();

    assertThat(choice.quest().questId()).isEqualTo("safe");
    assertThat(choice.acceptable()).isTrue();
  }

  @Test
  void getsMoreCarefulAsLivesDrop() {
    Quest quiteLikely = quest("q", Probability.QUITE_LIKELY, 50);

    assertThat(selector.evaluate(quiteLikely, lives(3)).acceptable()).isTrue();
    assertThat(selector.evaluate(quiteLikely, lives(2)).acceptable()).isTrue();
    assertThat(selector.evaluate(quiteLikely, lives(1)).acceptable()).isFalse();
  }

  @Test
  void lastLifeMakesFailureMoreExpensive() {
    Quest gamble = quest("g", Probability.GAMBLE, 100);

    double healthy = selector.evaluate(gamble, lives(3)).expectedValue();
    double critical = selector.evaluate(gamble, lives(1)).expectedValue();

    // 0.49 * 100 - 0.51 * 60 = 18.4 ; 0.49 * 100 - 0.51 * 180 = -42.8
    assertThat(healthy).isCloseTo(18.4, within(1e-9));
    assertThat(critical).isCloseTo(-42.8, within(1e-9));
  }

  @Test
  void levelDoesNotChangeTheOddsALabelGives() {
    Quest cake = quest("c", Probability.PIECE_OF_CAKE, 10);

    double atZero = selector.evaluate(cake, aGameState().build()).successChance();
    double atFifty = selector.evaluate(cake, aGameState().level(50).build()).successChance();

    assertThat(atZero).isEqualTo(0.96);
    assertThat(atFifty).isEqualTo(0.96);
  }

  @Test
  void ordinaryStealIsAsSafeAsItsLabel() {
    Quest steal =
        quest(
            "s",
            Probability.PIECE_OF_CAKE,
            48,
            5,
            "Steal sheep delivery to Châu Franklyn and share some of the profits with the people.");

    QuestEvaluation evaluation = selector.evaluate(steal, lives(3));

    assertThat(evaluation.successChance()).isEqualTo(0.96);
    assertThat(evaluation.acceptable()).isTrue();
  }

  @Test
  void diamondTrapIsNeverAcceptableWhateverItsLabelSays() {
    Quest trap = quest("t", Probability.SURE_THING, 344, 5, TRAP_MESSAGE);

    QuestEvaluation evaluation = selector.evaluate(trap, lives(3));

    assertThat(evaluation.successChance()).isZero();
    assertThat(evaluation.acceptable()).isFalse();
  }

  @Test
  void takesAPoorJobOverATrapThatPaysFarMore() {
    List<Quest> board =
        List.of(
            quest("trap", Probability.SURE_THING, 344, 5, TRAP_MESSAGE),
            quest("poor", Probability.SUICIDE_MISSION, 1));

    assertThat(selector.choose(board, lives(3)).orElseThrow().quest().questId()).isEqualTo("poor");
  }

  @Test
  void takesATrapOnlyWhenTheWholeBoardIsTraps() {
    List<Quest> board =
        List.of(
            quest("later", Probability.SURE_THING, 344, 5, TRAP_MESSAGE),
            quest("sooner", Probability.SURE_THING, 100, 2, TRAP_MESSAGE));

    QuestEvaluation choice = selector.choose(board, lives(3)).orElseThrow();

    assertThat(choice.quest().questType().isTrap()).isTrue();
    assertThat(choice.acceptable()).isFalse();
    assertThat(choice.quest().questId()).isEqualTo("sooner");
  }

  @Test
  void anyRandomBoardIsRankedWithoutLosingAQuestAndATrapOnlyWinsAlone() {
    Probability[] labels = Probability.values();
    for (long seed = 0; seed < RANDOM_BOARDS; seed++) {
      Random random = new Random(seed);
      List<Quest> board = new ArrayList<>();
      int size = 1 + random.nextInt(MAX_BOARD);
      for (int i = 0; i < size; i++) {
        board.add(
            quest(
                "q" + i,
                labels[random.nextInt(labels.length)],
                random.nextInt(MAX_REWARD),
                1 + random.nextInt(7),
                random.nextBoolean() ? TRAP_MESSAGE : "Help someone"));
      }
      GameState state =
          aGameState()
              .lives(1 + random.nextInt(4))
              .stateReputation(-random.nextInt(MAX_REPUTATION_LOSS))
              .build();

      List<QuestEvaluation> ranked = QUEST_SELECTOR.rank(board, state);

      assertThat(ranked)
          .as("seed %d", seed)
          .extracting(QuestEvaluation::quest)
          .containsExactlyInAnyOrderElementsOf(board);
      boolean hasNonTrap = board.stream().anyMatch(q -> !q.questType().isTrap());
      Quest chosen = QUEST_SELECTOR.best(ranked).orElseThrow().quest();
      assertThat(chosen.questType().isTrap()).as("seed %d", seed).isEqualTo(!hasNonTrap);
    }
  }

  @Test
  void tieGoesToTheQuestExpiringSooner() {
    List<Quest> board =
        List.of(
            quest("later", Probability.SURE_THING, 20, 6, "x"),
            quest("sooner", Probability.SURE_THING, 20, 2, "x"));

    assertThat(selector.choose(board, lives(3)).orElseThrow().quest().questId())
        .isEqualTo("sooner");
  }

  @Test
  void fallsBackToBestQuestWhenNothingIsAcceptable() {
    List<Quest> board =
        List.of(
            quest("fire", Probability.PLAYING_WITH_FIRE, 100),
            quest("gamble", Probability.GAMBLE, 100));

    QuestEvaluation choice = selector.choose(board, lives(1)).orElseThrow();

    assertThat(choice.quest().questId()).isEqualTo("gamble");
    assertThat(choice.acceptable()).isFalse();
  }

  @Test
  void emptyBoardGivesNoChoice() {
    assertThat(selector.choose(List.of(), lives(3))).isEmpty();
  }

  @Test
  void rankPutsAcceptableQuestsFirst() {
    List<Quest> board =
        List.of(quest("risky", Probability.RISKY, 1000), quest("safe", Probability.SURE_THING, 5));

    assertThat(selector.rank(board, lives(3)))
        .extracting(e -> e.quest().questId())
        .containsExactly("safe", "risky");
  }

  @Test
  void rejectsInvalidSettings() {
    assertThatThrownBy(() -> aSelectorSettings().minChanceHealthy(1.5).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> aSelectorSettings().minChanceHealthy(-0.1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> aSelectorSettings().lifeCost(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultsRankPurelyByExpectedValue() {
    RiskAwareQuestSelector byValue = QUEST_SELECTOR;
    List<Quest> board =
        List.of(
            quest("risky", Probability.RISKY, 400),
            quest("safe", Probability.WALK_IN_THE_PARK, 61));

    // 0.40 * 400 - 0.60 * 150 = 70 ; 0.84 * 61 - 0.16 * 150 = 27.24
    assertThat(byValue.choose(board, lives(3)).orElseThrow().quest().questId()).isEqualTo("risky");
  }

  @Test
  void defaultsStillNeverAcceptATrap() {
    RiskAwareQuestSelector byValue = QUEST_SELECTOR;
    Quest trap = quest("t", Probability.SURE_THING, 344, 5, TRAP_MESSAGE);

    assertThat(byValue.evaluate(trap, lives(3)).acceptable()).isFalse();
  }

  @Test
  void questWithoutMessageStillGetsItsLabelsOdds() {
    Quest silent = quest("s", Probability.PIECE_OF_CAKE, 10, 5, null);

    assertThat(selector.evaluate(silent, lives(3)).successChance()).isEqualTo(0.96);
  }
}
