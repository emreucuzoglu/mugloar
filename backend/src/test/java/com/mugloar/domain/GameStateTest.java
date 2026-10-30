package com.mugloar.domain;

import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aPurchaseResult;
import static com.mugloar.testsupport.Fixtures.aSolveResult;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStateTest {

  private final GameState start = aGameState().gameId("DW7XIae5").build();

  @Test
  void solveUpdatesEverythingButLevel() {
    GameState s =
        aGameState()
            .gold(10)
            .level(2)
            .score(5)
            .highScore(5)
            .turn(4)
            .build()
            .after(aSolveResult().gold(43).score(38).highScore(38).turn(5).build());

    assertThat(s).isEqualTo(aGameState().gold(43).level(2).score(38).highScore(38).turn(5).build());
  }

  @Test
  void purchaseUpdatesEverythingButScore() {
    GameState s =
        aGameState()
            .lives(2)
            .gold(150)
            .score(120)
            .highScore(120)
            .turn(7)
            .build()
            .after(aPurchaseResult().gold(100).turn(8).build());

    assertThat(s).isEqualTo(aGameState().gold(100).score(120).highScore(120).turn(8).build());
  }

  @Test
  void movesKeepTheItemsBoughtAndTrackedReputation() {
    GameState owner = aGameState().stateReputation(-4).build().afterBuying("ch");

    assertThat(owner.after(aSolveResult().build()).timesBought("ch")).isOne();
    assertThat(owner.after(aSolveResult().build()).stateReputation()).isEqualTo(-4);
    assertThat(owner.after(aPurchaseResult().build()).timesBought("ch")).isOne();
    assertThat(owner.after(aPurchaseResult().build()).stateReputation()).isEqualTo(-4);
    assertThat(owner.afterTurn().timesBought("ch")).isOne();
    assertThat(owner.ended().timesBought("ch")).isOne();
  }

  @Test
  void aWinChangesReputationByTheQuestTypesAmount() {
    assertThat(start.afterWinning(QuestType.STEAL_DELIVERY).stateReputation()).isEqualTo(-2);
    assertThat(start.afterWinning(QuestType.INFILTRATE).stateReputation()).isEqualTo(2);
  }

  @Test
  void aPossibleWinOnlyCountsAgainstTheTrapLine() {
    assertThat(start.afterPossibleWin(QuestType.STEAL_DELIVERY).stateReputation()).isEqualTo(-2);
    assertThat(start.afterPossibleWin(QuestType.INFILTRATE).stateReputation()).isZero();
  }

  @Test
  void aTurnOnItsOwnOnlyMovesTheTurnOn() {
    GameState before = aGameState().gold(5).score(7).turn(9).build();

    assertThat(before.afterTurn()).isEqualTo(aGameState().gold(5).score(7).turn(10).build());
  }

  @Test
  void countsEachItemBought() {
    GameState owner = start.afterBuying("ch").afterBuying("ch").afterBuying("rf");

    assertThat(owner.timesBought("ch")).isEqualTo(2);
    assertThat(owner.timesBought("rf")).isOne();
    assertThat(owner.timesBought("iron")).isZero();
  }

  @Test
  void gameIsOverWithoutLives() {
    assertThat(start.isOver()).isFalse();
    assertThat(start.ended().isOver()).isTrue();
    assertThat(start.ended().gameId()).isEqualTo("DW7XIae5");
  }
}
