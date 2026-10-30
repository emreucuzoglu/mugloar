package com.mugloar.game;

import static com.mugloar.testsupport.Fixtures.GAME_OVER;
import static com.mugloar.testsupport.Fixtures.GAME_START_DTO;
import static com.mugloar.testsupport.Fixtures.POTION_DTO;
import static com.mugloar.testsupport.Fixtures.REJECTED;
import static com.mugloar.testsupport.Fixtures.REPUTATION_DTO;
import static com.mugloar.testsupport.Fixtures.SERVER_DOWN;
import static com.mugloar.testsupport.Fixtures.SOLVE_RESULT_DTO;
import static com.mugloar.testsupport.Fixtures.aGameStartDto;
import static com.mugloar.testsupport.Fixtures.aPurchaseResultDto;
import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.aReputationDto;
import static com.mugloar.testsupport.Fixtures.aSolveResultDto;
import static com.mugloar.testsupport.Fixtures.autoPlayer;
import static com.mugloar.testsupport.Fixtures.gateway;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.ShopItem;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.solver.AutoPlayer;
import com.mugloar.solver.TurnEvent;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameServiceTest {

  private final MugloarClient client = mock(MugloarClient.class);
  private GameService service;

  @BeforeEach
  void setUp() {
    AutoPlayer player = autoPlayer(client);
    service = new GameService(gateway(client), player, new GameSessionRepository(10));
    when(client.startGame()).thenReturn(GAME_START_DTO);
    when(client.getShop("g1")).thenReturn(List.of(POTION_DTO));
    when(client.getMessages("g1"))
        .thenReturn(
            List.of(
                aQuestDto().questId("a1").message("Easy job").build(),
                aQuestDto()
                    .questId("a2")
                    .message("Hard job")
                    .reward(90)
                    .probability("Suicide mission")
                    .build()));
  }

  @Test
  void startsAndRemembersGame() {
    GameState state = service.start();

    assertThat(state.gameId()).isEqualTo("g1");
    assertThat(service.state("g1")).isEqualTo(state);
  }

  @Test
  void unknownGameIsNotFound() {
    assertThatThrownBy(() -> service.state("nope")).isInstanceOf(GameNotFoundException.class);
  }

  @Test
  void boardIsRankedBestFirst() {
    service.start();

    assertThat(service.board("g1").ranked())
        .extracting(e -> e.quest().questId())
        .containsExactly("a1", "a2");
  }

  @Test
  void suggestsThePurchaseTheSolverWouldMake() {
    service.start();
    assertThat(service.shop("g1").recommended()).isEmpty();
    assertThat(service.board("g1").recommended()).map(Quest::questId).contains("a1");

    when(client.solve("g1", "a2"))
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(1)
                .gold(60)
                .score(0)
                .highScore(0)
                .message("ouch")
                .build());
    service.solve("g1", "a2");

    assertThat(service.shop("g1").recommended()).map(ShopItem::id).contains("hpot");
    assertThat(service.board("g1").recommended()).isEmpty();
  }

  @Test
  void suggestsNothingOnceTheGameIsOver() {
    service.start();
    when(client.solve("g1", "a2"))
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(0)
                .gold(60)
                .score(0)
                .highScore(0)
                .message("dead")
                .build());
    service.solve("g1", "a2");

    assertThat(service.shop("g1").recommended()).isEmpty();
    assertThat(service.board("g1").recommended()).isEmpty();
  }

  @Test
  void solveUsesCachedBoardAndRecordsHistory() {
    service.start();
    service.board("g1");
    when(client.solve("g1", "a1")).thenReturn(SOLVE_RESULT_DTO);

    TurnEvent event = service.solve("g1", "a1");

    assertThat(event.state().score()).isEqualTo(20);
    assertThat(service.state("g1").score()).isEqualTo(20);
    assertThat(service.history("g1")).containsExactly(event);
    verify(client, times(1)).getMessages("g1");
  }

  @Test
  void solveReloadsBoardWhenQuestIsNotCached() {
    service.start();
    when(client.solve("g1", "a2"))
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(2)
                .gold(0)
                .score(0)
                .highScore(0)
                .message("ouch")
                .build());

    assertThat(service.solve("g1", "a2").success()).isFalse();
    verify(client).getMessages("g1");
  }

  @Test
  void solvingQuestThatIsNotOnBoardIsRejected() {
    service.start();

    assertThatThrownBy(() -> service.solve("g1", "ghost")).isInstanceOf(UnavailableException.class);
    verify(client, never()).solve(anyString(), anyString());
  }

  @Test
  void buyingWithoutEnoughGoldIsRejectedLocally() {
    service.start();

    assertThatThrownBy(() -> service.buy("g1", "hpot"))
        .isInstanceOf(UnavailableException.class)
        .hasMessageContaining("Not enough gold");
    verify(client, never()).buy(anyString(), anyString());
  }

  @Test
  void buyingUnknownItemIsRejected() {
    service.start();

    assertThatThrownBy(() -> service.buy("g1", "unicorn")).isInstanceOf(UnavailableException.class);
  }

  @Test
  void buyingUpdatesState() {
    when(client.startGame())
        .thenReturn(aGameStartDto().lives(2).gold(60).score(60).highScore(60).turn(3).build());
    when(client.buy("g1", "hpot")).thenReturn(aPurchaseResultDto().turn(4).build());
    service.start();

    TurnEvent event = service.buy("g1", "hpot");

    assertThat(event.state().lives()).isEqualTo(3);
    assertThat(service.state("g1").gold()).isEqualTo(10);
  }

  @Test
  void autoplayStepMakesOneMove() {
    service.start();
    when(client.solve("g1", "a1")).thenReturn(SOLVE_RESULT_DTO);

    List<TurnEvent> events = service.autoplayStep("g1");

    assertThat(events).extracting(TurnEvent::type).containsExactly(TurnEvent.Type.SOLVE);
    assertThat(service.state("g1").turn()).isEqualTo(1);
  }

  @Test
  void autoplayStopsAfterFiveRefusedMovesInARow() {
    service.start();
    when(client.solve("g1", "a1")).thenThrow(REJECTED);
    for (int i = 0; i < 5; i++) {
      service.autoplayStep("g1");
    }

    assertThatThrownBy(() -> service.autoplayStep("g1"))
        .isInstanceOf(UnavailableException.class)
        .hasMessageContaining("refused");
    verify(client, times(5)).solve("g1", "a1");
  }

  @Test
  void stoppedAutoplayCanBeStartedAgain() {
    service.start();
    when(client.solve("g1", "a1")).thenThrow(REJECTED);
    for (int i = 0; i < 5; i++) {
      service.autoplayStep("g1");
    }
    assertThatThrownBy(() -> service.autoplayStep("g1")).isInstanceOf(UnavailableException.class);

    service.autoplayStep("g1");

    verify(client, times(6)).solve("g1", "a1");
  }

  @Test
  void goneFromServerEndsSessionAndBlocksFurtherMoves() {
    service.start();
    when(client.solve("g1", "a1")).thenThrow(GAME_OVER);
    service.board("g1");

    assertThatThrownBy(() -> service.solve("g1", "a1")).isInstanceOf(GameOverException.class);
    assertThat(service.state("g1").isOver()).isTrue();
    assertThat(service.board("g1")).isEqualTo(QuestBoard.EMPTY);
    assertThatThrownBy(() -> service.autoplayStep("g1")).isInstanceOf(GameOverException.class);
  }

  @Test
  void lastLifeLostEndsGame() {
    when(client.startGame()).thenReturn(aGameStartDto().lives(1).build());
    when(client.solve("g1", "a1"))
        .thenReturn(
            aSolveResultDto()
                .success(false)
                .lives(0)
                .gold(0)
                .score(0)
                .highScore(0)
                .message("dead")
                .build());
    service.start();
    service.board("g1");

    service.solve("g1", "a1");

    assertThat(service.state("g1").isOver()).isTrue();
    assertThat(service.board("g1")).isEqualTo(QuestBoard.EMPTY);
  }

  @Test
  void shopIsFetchedOnlyOnce() {
    service.start();

    assertThat(service.shop("g1").items()).extracting(ShopItem::id).containsExactly("hpot");
    assertThat(service.shop("g1").items()).extracting(ShopItem::id).containsExactly("hpot");
    verify(client, times(1)).getShop("g1");
  }

  @Test
  void investigatingIsRecordedAndCostsATurn() {
    GameState start = service.start();
    when(client.investigateReputation("g1"))
        .thenReturn(aReputationDto().people(1).underworld(-2).build());

    TurnEvent event = service.investigate("g1");

    assertThat(event.type()).isEqualTo(TurnEvent.Type.INVESTIGATE);
    assertThat(service.state("g1")).isEqualTo(start.afterTurn());
    assertThat(service.history("g1")).containsExactly(event);
  }

  @Test
  void movesInAFinishedGameAreRefused() {
    when(client.startGame()).thenReturn(aGameStartDto().lives(0).gold(100).build());
    service.start();

    assertThatThrownBy(() -> service.solve("g1", "a1")).isInstanceOf(GameOverException.class);
    assertThatThrownBy(() -> service.buy("g1", "hpot")).isInstanceOf(GameOverException.class);
    assertThatThrownBy(() -> service.investigate("g1")).isInstanceOf(GameOverException.class);
    verify(client, never()).buy(anyString(), anyString());
  }

  @Test
  void goneWhileBuyingEndsTheSession() {
    when(client.startGame())
        .thenReturn(aGameStartDto().gold(60).score(60).highScore(60).turn(3).build());
    when(client.buy("g1", "hpot")).thenThrow(GAME_OVER);
    service.start();

    assertThatThrownBy(() -> service.buy("g1", "hpot")).isInstanceOf(GameOverException.class);

    assertThat(service.state("g1").isOver()).isTrue();
    assertThat(service.history("g1"))
        .extracting(TurnEvent::type)
        .containsExactly(TurnEvent.Type.GAME_OVER);
  }

  @Test
  void unansweredPurchaseLeavesTheSessionUnchanged() {
    when(client.startGame()).thenReturn(aGameStartDto().gold(60).build());
    when(client.buy("g1", "hpot")).thenThrow(SERVER_DOWN);
    GameState start = service.start();

    assertThatThrownBy(() -> service.buy("g1", "hpot")).isSameAs(SERVER_DOWN);

    assertThat(service.state("g1")).isEqualTo(start);
    assertThat(service.history("g1")).isEmpty();
  }

  @Test
  void failedAutoplayStepLeavesTheSessionUnchanged() {
    GameState start = service.start();
    when(client.getMessages("g1")).thenThrow(SERVER_DOWN);

    assertThatThrownBy(() -> service.autoplayStep("g1")).isSameAs(SERVER_DOWN);

    assertThat(service.state("g1")).isEqualTo(start);
    assertThat(service.history("g1")).isEmpty();
  }

  @Test
  void rateLimitedSolveLeavesTheSessionUnchanged() {
    GameState start = service.start();
    service.board("g1");
    MugloarApiException rateLimited = new MugloarApiException(429, "slow down");
    when(client.solve("g1", "a1")).thenThrow(rateLimited);

    assertThatThrownBy(() -> service.solve("g1", "a1")).isSameAs(rateLimited);

    assertThat(service.state("g1")).isEqualTo(start);
    assertThat(service.history("g1")).isEmpty();
  }

  @Test
  void unansweredSolveIsRecordedAndKeepsTheBoard() {
    service.start();
    service.board("g1");
    when(client.solve("g1", "a1")).thenThrow(SERVER_DOWN).thenReturn(SOLVE_RESULT_DTO);

    TurnEvent skip = service.solve("g1", "a1");
    service.solve("g1", "a1");

    assertThat(skip.type()).isEqualTo(TurnEvent.Type.SKIP);
    assertThat(service.history("g1")).hasSize(2).last().isEqualTo(skip);
    verify(client, times(1)).getMessages("g1");
  }

  @Test
  void aFailedBoardReadDuringASolveIsReportedWithoutAMove() {
    service.start();
    when(client.getMessages("g1")).thenThrow(SERVER_DOWN);

    assertThatThrownBy(() -> service.solve("g1", "a1")).isSameAs(SERVER_DOWN);

    verify(client, never()).solve(anyString(), anyString());
  }

  @Test
  void goneOnAFirstShopReadEndsTheSession() {
    service.start();
    when(client.getShop("g1")).thenThrow(GAME_OVER);

    assertThatThrownBy(() -> service.shop("g1")).isInstanceOf(GameOverException.class);

    assertThat(service.state("g1").isOver()).isTrue();
  }

  @Test
  void aSuccessfulMoveResetsTheRefusalCountForAutoplay() {
    service.start();
    when(client.solve("g1", "a1"))
        .thenThrow(REJECTED, REJECTED, REJECTED, REJECTED)
        .thenReturn(SOLVE_RESULT_DTO)
        .thenThrow(REJECTED);
    for (int i = 0; i < 4 + 1 + AutoPlayer.MAX_CONSECUTIVE_FAILURES; i++) {
      service.autoplayStep("g1");
    }

    assertThatThrownBy(() -> service.autoplayStep("g1")).isInstanceOf(UnavailableException.class);
  }

  @Test
  void anEvictedGameIsNoLongerFound() {
    GameService oneGame =
        new GameService(gateway(client), autoPlayer(client), new GameSessionRepository(1));
    when(client.startGame()).thenReturn(GAME_START_DTO, aGameStartDto().gameId("g2").build());
    oneGame.start();
    oneGame.start();

    assertThatThrownBy(() -> oneGame.solve("g1", "a1")).isInstanceOf(GameNotFoundException.class);
    assertThat(oneGame.state("g2").gameId()).isEqualTo("g2");
  }

  @Test
  void startingAGameWithAnIdAlreadyInUseReplacesTheOldSession() {
    service.start();
    when(client.investigateReputation("g1")).thenReturn(REPUTATION_DTO);
    service.investigate("g1");

    service.start();

    assertThat(service.history("g1")).isEmpty();
    assertThat(service.state("g1").turn()).isZero();
  }

  @Test
  void aSlowMoveInOneGameDoesNotBlockAnotherGame() throws Exception {
    when(client.startGame()).thenReturn(GAME_START_DTO, aGameStartDto().gameId("g2").build());
    service.start();
    service.start();
    CountDownLatch moveStarted = new CountDownLatch(1);
    CountDownLatch finishMove = new CountDownLatch(1);
    when(client.investigateReputation("g1"))
        .thenAnswer(call -> {
          moveStarted.countDown();
          finishMove.await();
          return REPUTATION_DTO;
        });
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      threads.submit(() -> service.investigate("g1"));
      assertThat(moveStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<GameState> other = threads.submit(() -> service.state("g2"));

      assertThat(other.get(5, TimeUnit.SECONDS).gameId()).isEqualTo("g2");
    } finally {
      finishMove.countDown();
      threads.shutdownNow();
    }
  }

  @Test
  void movesInOneGameDoNotInterleave() throws Exception {
    service.start();
    CountDownLatch moveStarted = new CountDownLatch(1);
    CountDownLatch finishMove = new CountDownLatch(1);
    when(client.investigateReputation("g1"))
        .thenAnswer(call -> {
          moveStarted.countDown();
          finishMove.await();
          return REPUTATION_DTO;
        });
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<TurnEvent> move = threads.submit(() -> service.investigate("g1"));
      assertThat(moveStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<List<TurnEvent>> second = threads.submit(() -> service.history("g1"));
      assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      finishMove.countDown();
      assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(move.get(5, TimeUnit.SECONDS));
    } finally {
      finishMove.countDown();
      threads.shutdownNow();
    }
  }
}
