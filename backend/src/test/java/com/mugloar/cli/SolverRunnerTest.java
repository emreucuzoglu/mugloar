package com.mugloar.cli;

import static com.mugloar.testsupport.Fixtures.GAME_START_DTO;
import static com.mugloar.testsupport.Fixtures.QUEST_DTO;
import static com.mugloar.testsupport.Fixtures.SOLVE_RESULT_DTO;
import static com.mugloar.testsupport.Fixtures.aGameStartDto;
import static com.mugloar.testsupport.Fixtures.autoPlayer;
import static com.mugloar.testsupport.Fixtures.gateway;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mugloar.config.MugloarProperties;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.solver.AutoPlayer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SolverRunnerTest {

  private final MugloarClient client = mock(MugloarClient.class);
  private final Logger logger = (Logger) LoggerFactory.getLogger(SolverRunner.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void captureLogs() {
    logs.start();
    logger.addAppender(logs);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logger.setLevel(null);
  }

  private SolverRunner runner(int games, int targetScore, int maxMoves) {
    AutoPlayer player = autoPlayer(client);
    MugloarProperties props =
        new MugloarProperties(
            URI.create("http://localhost"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1,
            Duration.ZERO,
            null,
            1,
            null,
            null,
            new MugloarProperties.Solver(games, targetScore, maxMoves));
    return new SolverRunner(gateway(client), player, props);
  }

  private List<String> messages() {
    return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void playsTheConfiguredNumberOfGamesAndSummarizesThem() {
    when(client.startGame())
        .thenReturn(
            aGameStartDto().score(1000).highScore(1000).build(),
            aGameStartDto().gameId("g2").score(500).highScore(500).build());

    runner(2, 500, 10).run();

    verify(client, times(2)).startGame();
    assertThat(messages())
        .contains("Played 2 game(s): min 500, avg 750, max 1000; 1 reached 1000+");
  }

  @Test
  void logsEveryMoveAndTheResult() {
    when(client.startGame()).thenReturn(GAME_START_DTO);
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a")).thenReturn(SOLVE_RESULT_DTO);

    runner(1, 20, 10).run();

    assertThat(messages())
        .containsExactly(
            "Game 1/1 started: g1",
            "[turn 1 | score 20 | gold 20 | lives 3 | lvl 0] Succeeded [Sure thing, 20 gold]: Job — ok",
            "Game g1 finished: score 20, 1 moves, level 0, 3 lives left (TARGET_REACHED)",
            "Played 1 game(s): min 20, avg 20, max 20; 0 reached 1000+");
  }

  @Test
  void passesTheMoveLimitToThePlayer() {
    when(client.startGame()).thenReturn(GAME_START_DTO);
    when(client.getMessages("g1")).thenReturn(List.of(QUEST_DTO));
    when(client.solve("g1", "a")).thenReturn(SOLVE_RESULT_DTO);

    runner(1, 0, 2).run();

    verify(client, times(2)).solve("g1", "a");
    assertThat(messages()).anyMatch(m -> m.contains("(MOVE_LIMIT)"));
  }

  @Test
  void rateLimitingStopsTheRemainingGames() {
    when(client.startGame()).thenReturn(GAME_START_DTO);
    when(client.getMessages("g1")).thenThrow(new MugloarApiException(429, "slow down"));

    runner(3, 0, 10).run();

    verify(client, times(1)).startGame();
    assertThat(messages()).anyMatch(m -> m.contains("(RATE_LIMITED)"));
  }

  @Test
  void aGameThatCouldNotStartDoesNotStopTheOthersOrTheSummary() {
    when(client.startGame())
        .thenThrow(new MugloarApiException(500, "boom"))
        .thenReturn(aGameStartDto().score(1000).highScore(1000).build());

    runner(2, 500, 10).run();

    verify(client, times(2)).startGame();
    assertThat(messages())
        .contains("Played 1 game(s): min 1000, avg 1000, max 1000; 1 reached 1000+");
  }

  @Test
  void aGameThatEndsOnItsFirstShopReadStillGivesASummary() {
    when(client.startGame()).thenReturn(GAME_START_DTO);
    when(client.getShop("g1")).thenThrow(new GameOverException("g1"));

    runner(1, 0, 10).run();

    assertThat(messages()).contains("No game could be played");
  }

  @Test
  void rateLimitingOnStartStopsTheRemainingGames() {
    when(client.startGame()).thenThrow(new MugloarApiException(429, "slow down"));

    runner(3, 0, 10).run();

    verify(client, times(1)).startGame();
    assertThat(messages()).contains("No game could be played");
  }

  @Test
  void rateLimitedGameStillCountsInTheSummary() {
    when(client.startGame()).thenReturn(aGameStartDto().score(7).highScore(7).build());
    when(client.getMessages("g1")).thenThrow(new MugloarApiException(429, "slow down"));

    runner(3, 0, 10).run();

    assertThat(messages()).contains("Played 1 game(s): min 7, avg 7, max 7; 0 reached 1000+");
  }

  @Test
  void finishedGameMakesNoMoves() {
    when(client.startGame()).thenReturn(aGameStartDto().lives(0).score(7).highScore(7).build());

    runner(1, 0, 10).run();

    verify(client, never()).solve(anyString(), anyString());
    assertThat(messages())
        .contains("Game g1 finished: score 7, 0 moves, level 0, 0 lives left (GAME_OVER)");
  }
}
