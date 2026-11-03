package com.mugloar.solver;

import static com.mugloar.testsupport.Fixtures.GAME_OVER;
import static com.mugloar.testsupport.Fixtures.GAME_STATE;
import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.PURCHASE_RESULT_DTO;
import static com.mugloar.testsupport.Fixtures.QUEST;
import static com.mugloar.testsupport.Fixtures.QUEST_DTO;
import static com.mugloar.testsupport.Fixtures.QUEST_SELECTOR;
import static com.mugloar.testsupport.Fixtures.REJECTED;
import static com.mugloar.testsupport.Fixtures.REPUTATION_DTO;
import static com.mugloar.testsupport.Fixtures.SERVER_DOWN;
import static com.mugloar.testsupport.Fixtures.SHOP_ITEMS;
import static com.mugloar.testsupport.Fixtures.SOLVE_RESULT_DTO;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aPurchaseResultDto;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.aReputationDto;
import static com.mugloar.testsupport.Fixtures.aShoppingSettings;
import static com.mugloar.testsupport.Fixtures.aSolveResultDto;
import static com.mugloar.testsupport.Fixtures.autoPlayer;
import static com.mugloar.testsupport.Fixtures.b64;
import static com.mugloar.testsupport.Fixtures.gateway;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.ShopItem;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.solver.strategy.BalancedShoppingStrategy;
import com.mugloar.testsupport.SimulatedMugloarClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AutoPlayerTest {

  private static final MugloarApiException RATE_LIMITED = new MugloarApiException(429, "slow down");
  private static final int MOVE_LIMIT = 2000;
  private static final int SURE_THINGS_TO_HARVEST = 7;

  /** From 30 live games of this solver until death or the move limit: 8 reached it, 22 died. */
  private static final double LIVE_SHARE_REACHING_THE_MOVE_LIMIT = 0.27;

  private static final double SHARE_TOLERANCE = 0.15;
  private static final double LIVE_AVERAGE_SCORE_OF_DEATHS = 11039;
  private static final double LIVE_AVERAGE_MOVES_OF_DEATHS = 128;

  /** The 8 games at the limit: 30 games averaged 159,794, less the 22 deaths at 11,039. */
  private static final double LIVE_AVERAGE_SCORE_AT_THE_MOVE_LIMIT = 568_870;

  private static final double LOST_REPLY_RATE = 0.05;
  private static final int LOST_REPLY_GAMES = 30;
  private static final double CALIBRATION_PERCENT = 25;
  private static final double SERVER_ERROR_RATE = 0.02;
  private static final double REACHES_TARGET_THROUGH_ERRORS = 0.85;

  private static int sim100PercentFloor(int games) {
    return (int) (games * REACHES_TARGET_THROUGH_ERRORS);
  }

  private final MugloarClient client = mock(MugloarClient.class);
  private final AutoPlayer player = autoPlayer(client);

  @Test
  void reaches1000InAtLeast90PercentOfSimulatedGames() {
    int games = 100;
    int reached = 0;
    int shoppers = 0;
    for (long seed = 100; seed < 100 + games; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);
      GameResult r = autoPlayer(sim).play(gateway(sim).startGame(), 1000, 2000, null);
      if (r.score() >= 1000) {
        reached++;
      }
      if (sim.purchases > 0) {
        shoppers++;
      }
    }
    assertThat(reached).isGreaterThanOrEqualTo(90);
    assertThat(shoppers).as("the solver should use the shop").isGreaterThanOrEqualTo(90);
  }

  @Test
  void neverWalksIntoADiamondTrapInSimulation() {
    for (long seed = 100; seed < 150; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);
      autoPlayer(sim).play(gateway(sim).startGame(), 1000, 2000, null);

      assertThat(sim.trapsTaken).as("seed %d", seed).isZero();
    }
  }

  @Test
  void checkingReputationCostsATurn() {
    when(client.investigateReputation("g1")).thenReturn(REPUTATION_DTO);

    assertThat(player.investigate(GAME_STATE).state().turn()).isEqualTo(GAME_STATE.turn() + 1);
  }

  @Test
  void reportsEveryMoveToListener() {
    SimulatedMugloarClient sim = new SimulatedMugloarClient(42);
    List<TurnEvent> events = new ArrayList<>();

    GameResult result = autoPlayer(sim).play(gateway(sim).startGame(), 200, 2000, events::add);

    assertThat(events).hasSize(result.moves());
    assertThat(events.get(events.size() - 1).state()).isEqualTo(result.finalState());
  }

  @Test
  void solvesTheBestQuest() {
    when(client.getMessages("g1"))
        .thenReturn(
            List.of(
                aQuestDto()
                    .questId("risky")
                    .message("Risky job")
                    .reward(30)
                    .expiresIn(6)
                    .probability("Risky")
                    .build(),
                aQuestDto()
                    .questId("cake")
                    .message("Easy job")
                    .reward(37)
                    .expiresIn(6)
                    .probability("Piece of cake")
                    .build()));
    when(client.solve("g1", "cake"))
        .thenReturn(
            aSolveResultDto().gold(37).score(37).highScore(37).message("Well done").build());

    TurnEvent event = player.solveBest(GAME_STATE);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SOLVE);
    assertThat(event.success()).isTrue();
    assertThat(event.state().score()).isEqualTo(37);
    assertThat(event.message()).contains("Easy job").contains("Piece of cake");
  }

  @Test
  void encryptedQuestIsSolvedWithDecodedId() {
    when(client.getMessages("g1"))
        .thenReturn(
            List.of(
                aQuestDto()
                    .questId(b64("secret1"))
                    .message(b64("Hidden job"))
                    .reward(50)
                    .expiresIn(3)
                    .encrypted(1)
                    .probability(b64("Sure thing"))
                    .build()));
    when(client.solve("g1", "secret1"))
        .thenReturn(aSolveResultDto().gold(50).score(50).highScore(50).build());

    assertThat(player.solveBest(GAME_STATE).success()).isTrue();
  }

  @Test
  void encryptedQuestFallsBackToRawIdWhenDecodedIdIsRejected() {
    Quest quest =
        aQuest()
            .questId("secret1")
            .rawQuestId(b64("secret1"))
            .message("Hidden job")
            .reward(50)
            .expiresIn(3)
            .encrypted(true)
            .build();
    when(client.solve("g1", "secret1")).thenThrow(REJECTED);
    when(client.solve("g1", b64("secret1")))
        .thenReturn(aSolveResultDto().gold(50).score(50).highScore(50).build());

    TurnEvent event = player.solve(GAME_STATE, quest);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SOLVE);
    assertThat(event.success()).isTrue();
  }

  @Test
  void expiredQuestBecomesSkipWithoutChangingState() {
    Quest quest = aQuest().questId("gone").message("Old job").build();
    when(client.solve("g1", "gone")).thenThrow(REJECTED);

    TurnEvent event = player.solve(GAME_STATE, quest);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(event.state()).isEqualTo(GAME_STATE);
  }

  @Test
  void unansweredSolveBecomesASkipThatKeepsTheGameState() {
    when(client.solve("g1", "a")).thenThrow(SERVER_DOWN);

    TurnEvent event = player.solve(GAME_STATE, QUEST);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(event.message()).contains("may have counted");
    assertThat(event.state()).isEqualTo(GAME_STATE);
  }

  @Test
  void unansweredStealCountsAgainstTheTrapLineInCaseItWon() {
    Quest steal = aQuest().questId("a").rawQuestId("a").message("Steal a delivery").build();
    when(client.solve("g1", "a")).thenThrow(SERVER_DOWN);

    TurnEvent event = player.solve(GAME_STATE, steal);

    assertThat(event.state().stateReputation()).isEqualTo(-2);
  }

  @Test
  void unansweredJobDoesNotAssumeAGainInStateReputation() {
    Quest infiltrate = aQuest().questId("a").rawQuestId("a").message("Infiltrate a guild").build();
    when(client.solve("g1", "a")).thenThrow(SERVER_DOWN);

    TurnEvent event = player.solve(GAME_STATE, infiltrate);

    assertThat(event.state().stateReputation()).isZero();
  }

  @Test
  void reputationCheckCorrectsTheTrackedState() {
    when(client.investigateReputation("g1")).thenReturn(aReputationDto().state(-4).build());

    TurnEvent event = player.investigate(GAME_STATE);

    assertThat(event.state().stateReputation()).isEqualTo(-4);
  }

  @Test
  void rateLimitedSolveIsNotTreatedAsARefusal() {
    Quest quest =
        aQuest().questId("secret1").rawQuestId("raw1").message("Job").encrypted(true).build();
    when(client.solve("g1", "secret1")).thenThrow(RATE_LIMITED);

    assertThatThrownBy(() -> player.solve(GAME_STATE, quest)).isSameAs(RATE_LIMITED);
    verify(client, never()).solve("g1", "raw1");
  }

  @Test
  void rateLimitedPurchaseDoesNotFallThroughToAQuest() {
    GameState wounded = aGameState().lives(2).gold(60).turn(10).build();
    when(client.buy("g1", "hpot")).thenThrow(RATE_LIMITED);

    assertThatThrownBy(() -> player.playTurn(wounded, SHOP_ITEMS)).isSameAs(RATE_LIMITED);
    verify(client, never()).solve(anyString(), anyString());
  }

  @Test
  void takesAJobInsteadOfAnUpgradeWhileTheBoardIsFullOfSureThings() {
    GameState rich = aGameState().gold(1000).build();
    when(client.getMessages("g1"))
        .thenReturn(Collections.nCopies(SURE_THINGS_TO_HARVEST, QUEST_DTO));
    when(client.solve("g1", "a")).thenReturn(SOLVE_RESULT_DTO);

    List<TurnEvent> events = player.playTurn(rich, SHOP_ITEMS);

    assertThat(events).extracting(TurnEvent::type).containsExactly(TurnEvent.Type.SOLVE);
    assertThat(player.adviseShop(rich, SHOP_ITEMS).recommended()).isEmpty();
    verify(client, never()).buy(anyString(), anyString());
  }

  @Test
  void buysTheUpgradeWhenTooFewJobsAreSureThings() {
    GameState rich = aGameState().gold(1000).build();
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.buy("g1", "ch")).thenReturn(PURCHASE_RESULT_DTO);

    List<TurnEvent> events = player.playTurn(rich, SHOP_ITEMS);

    assertThat(events).extracting(TurnEvent::type).containsExactly(TurnEvent.Type.PURCHASE);
    verify(client, never()).solve(anyString(), anyString());
  }

  @Test
  void playStopsAtOnceWhenRateLimited() {
    when(client.getShop("g1")).thenReturn(SimulatedMugloarClient.SHOP);
    when(client.getMessages("g1")).thenThrow(RATE_LIMITED);

    GameResult result = player.play(GAME_STATE, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.RATE_LIMITED);
    verify(client, times(1)).getMessages("g1");
  }

  @Test
  void playBuysOnceAFailedShopReadRecovers() {
    GameState wounded = aGameState().lives(1).gold(60).turn(1).build();
    when(client.getShop("g1"))
        .thenThrow(new MugloarApiException(500, "boom"))
        .thenReturn(SimulatedMugloarClient.SHOP);
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(true).gold(10).lives(2).turn(2).build());

    GameResult result = player.play(wounded, 0, 1, null);

    verify(client).buy("g1", "hpot");
    assertThat(result.reason()).isEqualTo(GameResult.StopReason.MOVE_LIMIT);
  }

  @Test
  void goneWhileRereadingTheShopEndsTheGame() {
    when(client.getShop("g1")).thenThrow(new MugloarApiException(500, "boom")).thenThrow(GAME_OVER);

    GameResult result = player.play(GAME_STATE, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.GAME_OVER);
    assertThat(result.finalState().isOver()).isTrue();
  }

  @Test
  void tracksStateReputationFromWinsOnly() {
    Quest steal =
        aQuest()
            .message("Steal dog delivery from someone")
            .reward(50)
            .expiresIn(5)
            .probability(Probability.GAMBLE)
            .build();
    when(client.solve("g1", "a"))
        .thenReturn(aSolveResultDto().gold(50).score(50).highScore(50).build())
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(2)
                .gold(50)
                .score(50)
                .highScore(50)
                .turn(2)
                .message("no")
                .build());

    GameState won = player.solve(GAME_STATE, steal).state();
    GameState lost = player.solve(won, steal).state();

    assertThat(won.stateReputation()).isEqualTo(-2);
    assertThat(lost.stateReputation()).isEqualTo(-2);
  }

  @Test
  void healsBeforeSolvingWhenWounded() {
    GameState wounded = aGameState().lives(1).gold(80).score(100).highScore(100).turn(10).build();
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().gold(30).lives(2).turn(11).build());

    List<TurnEvent> events = player.playTurn(wounded, SHOP_ITEMS);

    assertThat(events)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.PURCHASE);
          assertThat(e.state().lives()).isEqualTo(2);
        });
    verify(client, never()).solve(anyString(), anyString());
  }

  @Test
  void refusedPurchaseFallsThroughToAnQuest() {
    GameState wounded = aGameState().lives(2).gold(60).score(100).highScore(100).turn(10).build();
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(false).gold(60).lives(2).turn(11).build());
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a"))
        .thenReturn(aSolveResultDto().lives(2).gold(80).score(120).highScore(120).turn(12).build());

    List<TurnEvent> events = player.playTurn(wounded, SHOP_ITEMS);

    assertThat(events)
        .extracting(TurnEvent::type)
        .containsExactly(TurnEvent.Type.PURCHASE, TurnEvent.Type.SOLVE);
  }

  @Test
  void investigatesWhenBoardIsEmpty() {
    when(client.getMessages("g1")).thenReturn(List.of());
    when(client.investigateReputation("g1"))
        .thenReturn(aReputationDto().people(1).underworld(-2).build());

    TurnEvent event = player.solveBest(GAME_STATE);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.INVESTIGATE);
    assertThat(event.message()).contains("people 1.0").contains("underworld -2.0");
  }

  @Test
  void goneResponseEndsTheGame() {
    when(client.getMessages("g1")).thenThrow(GAME_OVER);

    List<TurnEvent> events = player.playTurn(GAME_STATE, SHOP_ITEMS);

    assertThat(events)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.GAME_OVER);
          assertThat(e.state().isOver()).isTrue();
        });
  }

  @Test
  void playStopsWhenStalled() {
    when(client.getShop("g1")).thenReturn(SimulatedMugloarClient.SHOP);
    when(client.getMessages("g1"))
        .thenThrow(new MugloarApiException(MugloarApiException.NO_RESPONSE, "offline"));

    GameResult result = player.play(GAME_STATE, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.STALLED);
    assertThat(result.moves()).isZero();
  }

  @Test
  void playRespectsMoveLimit() {
    SimulatedMugloarClient sim = new SimulatedMugloarClient(7);

    GameResult result = autoPlayer(sim).play(gateway(sim).startGame(), 0, 5, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.MOVE_LIMIT);
    assertThat(result.moves()).isEqualTo(5);
  }

  @Test
  void zeroMoveLimitPlaysUntilDeath() {
    long seed = 100;
    while (play(seed, MOVE_LIMIT).reason() != GameResult.StopReason.GAME_OVER) {
      seed++;
    }

    GameResult limited = play(seed, MOVE_LIMIT);
    GameResult unlimited = play(seed, 0);

    assertThat(unlimited.reason()).isEqualTo(GameResult.StopReason.GAME_OVER);
    assertThat(unlimited.moves()).isEqualTo(limited.moves());
  }

  private GameResult play(long seed, int maxMoves) {
    SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);
    return autoPlayer(sim).play(gateway(sim).startGame(), 0, maxMoves, null);
  }

  @Test
  void playWorksWithoutShopWhenShopFails() {
    when(client.getShop("g1")).thenThrow(new MugloarApiException(500, "boom"));
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a")).thenReturn(SOLVE_RESULT_DTO);

    GameResult result = player.play(GAME_STATE, 20, 10, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.TARGET_REACHED);
  }

  @Test
  void playStopsWhenEveryQuestIsRejected() {
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a")).thenThrow(REJECTED);
    List<TurnEvent> events = new ArrayList<>();

    GameResult result = player.play(GAME_STATE, 0, 100, events::add);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.STALLED);
    assertThat(result.moves()).isZero();
    assertThat(events)
        .hasSize(AutoPlayer.MAX_CONSECUTIVE_FAILURES)
        .allMatch(e -> e.type() == TurnEvent.Type.SKIP);
  }

  @Test
  void successfulMoveResetsTheFailureCount() {
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a"))
        .thenThrow(REJECTED, REJECTED, REJECTED, REJECTED)
        .thenReturn(SOLVE_RESULT_DTO)
        .thenThrow(REJECTED);

    GameResult result = player.play(GAME_STATE, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.STALLED);
    assertThat(result.moves()).isEqualTo(1);
    verify(client, times(4 + 1 + AutoPlayer.MAX_CONSECUTIVE_FAILURES)).solve("g1", "a");
  }

  @Test
  void playReportsServerErrorsToTheListener() {
    when(client.getMessages("g1")).thenThrow(SERVER_DOWN);
    List<TurnEvent> events = new ArrayList<>();

    player.play(GAME_STATE, 0, 100, events::add);

    assertThat(events)
        .first()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.SKIP);
          assertThat(e.success()).isFalse();
          assertThat(e.message()).isEqualTo("Game server error");
          assertThat(e.state()).isEqualTo(GAME_STATE);
        });
  }

  @Test
  void playEndsWhenTheServerSaysTheGameIsGone() {
    when(client.getMessages("g1")).thenThrow(GAME_OVER);

    GameResult result = player.play(GAME_STATE, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.GAME_OVER);
    assertThat(result.finalState().isOver()).isTrue();
  }

  @Test
  void playMakesNoMoveInAFinishedGame() {
    GameResult result = player.play(GAME_STATE.ended(), 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.GAME_OVER);
    assertThat(result.moves()).isZero();
    verify(client, never()).getMessages(anyString());
  }

  @Test
  void goneWhileLoadingTheShopEscapesPlay() {
    when(client.getShop("g1")).thenThrow(GAME_OVER);

    assertThatThrownBy(() -> player.play(GAME_STATE, 0, 100, null))
        .isInstanceOf(GameOverException.class);
  }

  @Test
  void playTurnInAFinishedGameOnlyReportsGameOver() {
    List<TurnEvent> events = player.playTurn(GAME_STATE.ended(), SHOP_ITEMS);

    assertThat(events)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.GAME_OVER);
          assertThat(e.message()).isEqualTo("Game over with 0 points");
        });
    verifyNoInteractions(client);
  }

  @Test
  void plainQuestIsNotRetriedWhenRejected() {
    Quest quest = aQuest().questId("gone").message("Old job").build();
    when(client.solve("g1", "gone")).thenThrow(REJECTED);

    player.solve(GAME_STATE, quest);

    verify(client, times(1)).solve("g1", "gone");
  }

  @Test
  void encryptedQuestWhoseIdDidNotChangeIsNotRetried() {
    Quest quest = aQuest().questId("same").encrypted(true).build();
    when(client.solve("g1", "same")).thenThrow(REJECTED);

    assertThat(player.solve(GAME_STATE, quest).type()).isEqualTo(TurnEvent.Type.SKIP);
    verify(client, times(1)).solve("g1", "same");
  }

  @Test
  void encryptedQuestBecomesSkipWhenBothIdsAreRejected() {
    Quest quest =
        aQuest()
            .questId("secret1")
            .rawQuestId("raw1")
            .message("Hidden job")
            .reward(50)
            .expiresIn(3)
            .encrypted(true)
            .build();
    when(client.solve("g1", "secret1")).thenThrow(REJECTED);
    when(client.solve("g1", "raw1")).thenThrow(REJECTED);

    TurnEvent event = player.solve(GAME_STATE, quest);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(event.message()).contains("encrypted quest").contains("Hidden job");
    assertThat(event.state()).isEqualTo(GAME_STATE);
  }

  @Test
  void unansweredRawIdRetryBecomesASkip() {
    Quest quest =
        aQuest()
            .questId("secret1")
            .rawQuestId("raw1")
            .message("Hidden job")
            .reward(50)
            .expiresIn(3)
            .encrypted(true)
            .build();
    when(client.solve("g1", "secret1")).thenThrow(REJECTED);
    when(client.solve("g1", "raw1")).thenThrow(SERVER_DOWN);

    assertThat(player.solve(GAME_STATE, quest).type()).isEqualTo(TurnEvent.Type.SKIP);
  }

  @Test
  void goneOnRawIdRetryIsNotSwallowed() {
    Quest quest =
        aQuest()
            .questId("secret1")
            .rawQuestId("raw1")
            .message("Hidden job")
            .reward(50)
            .expiresIn(3)
            .encrypted(true)
            .build();
    when(client.solve("g1", "secret1")).thenThrow(REJECTED);
    when(client.solve("g1", "raw1")).thenThrow(GAME_OVER);

    assertThatThrownBy(() -> player.solve(GAME_STATE, quest)).isInstanceOf(GameOverException.class);
  }

  @Test
  void goneOnSolveIsNotSwallowed() {
    Quest quest = QUEST;
    when(client.solve("g1", "a")).thenThrow(GAME_OVER);

    assertThatThrownBy(() -> player.solve(GAME_STATE, quest)).isInstanceOf(GameOverException.class);
  }

  @Test
  void failedSolveIsReportedWithTheServerMessage() {
    Quest quest = aQuest().probability(Probability.GAMBLE).build();
    when(client.solve("g1", "a"))
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(2)
                .gold(0)
                .score(0)
                .highScore(0)
                .message("Ouch")
                .build());

    TurnEvent event = player.solve(GAME_STATE, quest);

    assertThat(event.success()).isFalse();
    assertThat(event.message()).isEqualTo("Failed [Gamble, 10 gold]: Job — Ouch");
    assertThat(event.state().lives()).isEqualTo(2);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void solveMessageLeavesOutAnEmptyServerMessage(String serverMessage) {
    Quest quest = QUEST;
    when(client.solve("g1", "a"))
        .thenReturn(
            aSolveResultDto().gold(10).score(10).highScore(10).message(serverMessage).build());

    assertThat(player.solve(GAME_STATE, quest).message())
        .isEqualTo("Succeeded [Sure thing, 10 gold]: Job");
  }

  @Test
  void purchaseReportsWhatWasBought() {
    when(client.buy("g1", "hpot")).thenReturn(PURCHASE_RESULT_DTO);

    TurnEvent event = player.purchase(GAME_STATE, POTION);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.PURCHASE);
    assertThat(event.success()).isTrue();
    assertThat(event.message()).isEqualTo("Bought Healing potion for 50 gold");
  }

  @Test
  void remembersOnlyItemsThatWereSold() {
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(false).gold(0).build())
        .thenReturn(aPurchaseResultDto().turn(2).build());

    GameState refused = player.purchase(GAME_STATE, POTION).state();
    GameState sold = player.purchase(refused, POTION).state();

    assertThat(refused.itemsBought()).isEmpty();
    assertThat(sold.timesBought("hpot")).isOne();
  }

  @Test
  void purchaseReportsARefusal() {
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(false).gold(0).build());

    TurnEvent event = player.purchase(GAME_STATE, POTION);

    assertThat(event.success()).isFalse();
    assertThat(event.message()).isEqualTo("The shop refused to sell Healing potion");
  }

  @Test
  void rejectedPurchaseBecomesSkipWithoutChangingState() {
    when(client.buy("g1", "hpot")).thenThrow(REJECTED);

    TurnEvent event = player.purchase(GAME_STATE, POTION);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(event.message())
        .isEqualTo("Could not buy Healing potion: the game server refused it");
    assertThat(event.state()).isEqualTo(GAME_STATE);
  }

  @Test
  void unansweredPurchaseBecomesASkipThatKeepsTheKnownGoldAndLives() {
    GameState wounded = aGameState().lives(2).gold(120).turn(10).build();
    when(client.buy("g1", "hpot")).thenThrow(SERVER_DOWN);

    TurnEvent event = player.purchase(wounded, POTION);

    assertThat(event.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(event.message()).contains("may have counted");
    assertThat(event.state().gold()).isEqualTo(120);
    assertThat(event.state().lives()).isEqualTo(2);
    assertThat(event.state().purchaseUnconfirmed()).isTrue();
  }

  @Test
  void rejectedPurchaseFallsThroughToAnQuest() {
    GameState wounded = aGameState().lives(2).gold(60).score(100).highScore(100).turn(10).build();
    when(client.buy("g1", "hpot")).thenThrow(REJECTED);
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a"))
        .thenReturn(aSolveResultDto().lives(2).gold(80).score(120).highScore(120).turn(11).build());

    List<TurnEvent> events = player.playTurn(wounded, SHOP_ITEMS);

    assertThat(events)
        .extracting(TurnEvent::type)
        .containsExactly(TurnEvent.Type.SKIP, TurnEvent.Type.SOLVE);
  }

  @Test
  void goneWhileBuyingEndsTheGame() {
    GameState wounded = aGameState().lives(2).gold(60).score(100).highScore(100).turn(10).build();
    when(client.buy("g1", "hpot")).thenThrow(GAME_OVER);

    List<TurnEvent> events = player.playTurn(wounded, SHOP_ITEMS);

    assertThat(events)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.GAME_OVER);
          assertThat(e.state().score()).isEqualTo(100);
          assertThat(e.state().isOver()).isTrue();
        });
  }

  @Test
  void refusedPurchaseIsKeptWhenReadingTheBoardAfterItFails() {
    GameState wounded = aGameState().lives(2).gold(60).score(100).highScore(100).turn(10).build();
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(false).gold(60).lives(2).turn(11).build());
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO)).thenThrow(SERVER_DOWN);

    List<TurnEvent> events = player.playTurn(wounded, SHOP_ITEMS);

    assertThat(events)
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.PURCHASE);
          assertThat(e.state().turn()).isEqualTo(11);
        });
  }

  @Test
  void rateLimitAfterARefusedPurchaseStillStopsTheGame() {
    GameState wounded = aGameState().lives(2).gold(60).score(100).highScore(100).turn(10).build();
    when(client.getShop("g1")).thenReturn(SimulatedMugloarClient.SHOP);
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().shoppingSuccess(false).gold(60).lives(2).turn(11).build());
    when(client.getMessages("g1")).thenThrow(RATE_LIMITED);

    GameResult result = player.play(wounded, 0, 100, null);

    assertThat(result.reason()).isEqualTo(GameResult.StopReason.RATE_LIMITED);
  }

  @Test
  void unansweredPurchaseIsNotSentAgainUntilAResponseCorrectsTheState() {
    GameState wounded = aGameState().lives(2).gold(120).turn(10).build();
    when(client.buy("g1", "hpot"))
        .thenThrow(SERVER_DOWN)
        .thenReturn(aPurchaseResultDto().gold(20).lives(3).turn(13).build());
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a"))
        .thenThrow(SERVER_DOWN)
        .thenReturn(aSolveResultDto().lives(2).gold(70).turn(12).build());

    List<TurnEvent> first = player.playTurn(wounded, SHOP_ITEMS);
    GameState unconfirmed = first.getLast().state();
    assertThat(first).extracting(TurnEvent::type).containsOnly(TurnEvent.Type.SKIP);

    GameState corrected = player.playTurn(unconfirmed, SHOP_ITEMS).getLast().state();
    verify(client, times(1)).buy("g1", "hpot");
    assertThat(corrected.gold()).isEqualTo(70);
    assertThat(corrected.purchaseUnconfirmed()).isFalse();

    player.playTurn(corrected, SHOP_ITEMS);
    verify(client, times(2)).buy("g1", "hpot");
  }

  @Test
  void unansweredPurchaseMovesTheRotationOnInCaseItCounted() {
    GameState unconfirmed = aGameState().gold(1000).build().afterPossiblePurchase("ch");
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a")).thenReturn(aSolveResultDto().gold(1000).turn(1).build());
    when(client.buy("g1", "iron")).thenReturn(aPurchaseResultDto().gold(700).turn(2).build());

    GameState corrected = player.playTurn(unconfirmed, SHOP_ITEMS).getLast().state();
    player.playTurn(corrected, SHOP_ITEMS);

    verify(client, never()).buy("g1", "ch");
    verify(client).buy("g1", "iron");
  }

  @Test
  void recommendsNoPurchaseWhileOneIsUnconfirmed() {
    GameState unconfirmed = aGameState().lives(2).gold(120).build().afterPossiblePurchase("hpot");
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));

    assertThat(player.adviseShop(unconfirmed, SHOP_ITEMS).recommended()).isEmpty();
    assertThat(player.adviseQuests(unconfirmed, SHOP_ITEMS).recommended()).isPresent();
  }

  @Test
  void playsUntilDeathWithoutAnAvoidableTrapOrACallAfterTheEnd() {
    for (long seed = 100; seed < 150; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);

      GameResult result = autoPlayer(sim).play(gateway(sim).startGame(), 0, MOVE_LIMIT, null);

      assertThat(result.reason())
          .as("seed %d", seed)
          .isIn(GameResult.StopReason.GAME_OVER, GameResult.StopReason.MOVE_LIMIT);
      assertThat(sim.avoidableTraps).as("seed %d", seed).isZero();
      assertThat(sim.callsAfterGameOver).as("seed %d", seed).isZero();
    }
  }

  /**
   * Games either fall behind and die, or keep up and reach the move limit, so each is compared. The
   * live figures come from the solver before harvesting, so the simulated one buys the same way.
   */
  @Test
  void simulatedGamesUntilDeathResembleTheLiveGame() {
    int games = 100;
    int reachedTheLimit = 0;
    IntSummaryStatistics deathScores = new IntSummaryStatistics();
    IntSummaryStatistics deathMoves = new IntSummaryStatistics();
    IntSummaryStatistics limitScores = new IntSummaryStatistics();
    for (long seed = 100; seed < 100 + games; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);
      AutoPlayer alwaysBuys =
          new AutoPlayer(
              gateway(sim),
              QUEST_SELECTOR,
              new BalancedShoppingStrategy(aShoppingSettings().harvestAtSureThings(0).build()));
      GameResult r = alwaysBuys.play(gateway(sim).startGame(), 0, MOVE_LIMIT, null);
      if (r.reason() == GameResult.StopReason.MOVE_LIMIT) {
        reachedTheLimit++;
        limitScores.accept(r.score());
      } else {
        deathScores.accept(r.score());
        deathMoves.accept(r.moves());
      }
    }

    assertThat((double) reachedTheLimit / games)
        .as("share reaching the move limit; deaths: score %s, moves %s", deathScores, deathMoves)
        .isCloseTo(LIVE_SHARE_REACHING_THE_MOVE_LIMIT, within(SHARE_TOLERANCE));
    assertThat(deathScores.getAverage())
        .as("score of deaths %s", deathScores)
        .isCloseTo(LIVE_AVERAGE_SCORE_OF_DEATHS, withinPercentage(CALIBRATION_PERCENT));
    assertThat(deathMoves.getAverage())
        .as("moves of deaths %s", deathMoves)
        .isCloseTo(LIVE_AVERAGE_MOVES_OF_DEATHS, withinPercentage(CALIBRATION_PERCENT));
    assertThat(limitScores.getAverage())
        .as("score of games reaching the move limit %s", limitScores)
        .isCloseTo(LIVE_AVERAGE_SCORE_AT_THE_MOVE_LIMIT, withinPercentage(CALIBRATION_PERCENT));
  }

  @Test
  void tracksTheSameStateReputationAsTheServerAfterEveryMove() {
    for (long seed = 100; seed < 120; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed);
      long thisSeed = seed;

      autoPlayer(sim)
          .play(
              gateway(sim).startGame(),
              0,
              2000,
              event ->
                  assertThat(event.state().stateReputation())
                      .as("seed %d turn %d", thisSeed, event.state().turn())
                      .isEqualTo(sim.stateReputation()));
    }
  }

  @Test
  void rotatesThroughTheBigItemsDuringARealGame() {
    SimulatedMugloarClient sim = new SimulatedMugloarClient(42);

    GameResult result = autoPlayer(sim).play(gateway(sim).startGame(), 0, 2000, null);

    List<String> big = sim.bigItemsBought();
    assertThat(big).as("this seed has to go round more than once").hasSizeGreaterThan(5);
    assertThat(big.subList(0, 5)).doesNotHaveDuplicates();
    for (int i = 5; i < big.size(); i++) {
      assertThat(big.get(i)).isEqualTo(big.get(i - 5));
    }
    assertThat(result.finalState().timesBought(big.get(0))).isGreaterThan(1);
  }

  @Test
  void keepsPlayingThroughOccasionalServerErrors() {
    int games = 100;
    int reached = 0;
    for (long seed = 100; seed < 100 + games; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed, SERVER_ERROR_RATE);
      GameResult r = autoPlayer(sim).play(gateway(sim).startGame(), 1000, 2000, null);
      if (r.score() >= 1000) {
        reached++;
      }
    }
    assertThat(sim100PercentFloor(games)).isLessThanOrEqualTo(reached);
  }

  /**
   * The server makes the move but its answer is lost. The solver must not spend gold it may no
   * longer have, nor buy the same +2 item again, and every answer it gets must put it back in step.
   */
  @Test
  void movesThatCountWithoutAnAnswerLeaveNoRefusalAndAnEvenRotation() {
    int lostReplies = 0;
    for (long seed = 100; seed < 100 + LOST_REPLY_GAMES; seed++) {
      SimulatedMugloarClient sim = new SimulatedMugloarClient(seed, 0, LOST_REPLY_RATE);
      long thisSeed = seed;

      autoPlayer(sim)
          .play(
              gateway(sim).startGame(),
              0,
              MOVE_LIMIT,
              event -> {
                if (event.type() == TurnEvent.Type.SOLVE
                    || event.type() == TurnEvent.Type.PURCHASE) {
                  GameState known = event.state();
                  String where = "seed " + thisSeed + " turn " + known.turn();
                  assertThat(known.gold()).as(where).isEqualTo(sim.gold());
                  assertThat(known.lives()).as(where).isEqualTo(sim.lives());
                  assertThat(known.turn()).as(where).isEqualTo(sim.turn());
                }
                if (event.type() == TurnEvent.Type.PURCHASE) {
                  assertThat(event.state().level()).as("seed %d", thisSeed).isEqualTo(sim.level());
                }
              });

      assertThat(sim.refusedPurchases).as("seed %d", seed).isZero();
      assertThat(sim.bigItemSpread()).as("seed %d", seed).isLessThanOrEqualTo(1);
      assertThat(sim.avoidableTraps).as("seed %d", seed).isZero();
      lostReplies += sim.lostReplies;
    }
    assertThat(lostReplies)
        .as("the fault has to happen for the test to mean anything")
        .isPositive();
  }

  @Test
  void recommendsTheBestQuestAndNoItemWhenThereIsNothingToBuy() {
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));

    QuestBoard board = player.adviseQuests(GAME_STATE, SHOP_ITEMS);

    assertThat(board.recommended()).map(Quest::questId).contains("a");
    assertThat(player.adviseShop(GAME_STATE, SHOP_ITEMS).recommended()).isEmpty();
  }

  @Test
  void recommendsTheItemAndNoQuestWhenTheSolverWouldShopFirst() {
    GameState wounded = aGameState().lives(1).gold(60).turn(1).build();
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));

    QuestBoard board = player.adviseQuests(wounded, SHOP_ITEMS);

    assertThat(board.ranked()).hasSize(1);
    assertThat(board.recommended()).isEmpty();
    assertThat(player.adviseShop(wounded, SHOP_ITEMS).recommended())
        .map(ShopItem::id)
        .contains("hpot");
  }
}
