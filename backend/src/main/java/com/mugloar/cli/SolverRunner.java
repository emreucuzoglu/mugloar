package com.mugloar.cli;

import com.mugloar.config.MugloarProperties;
import com.mugloar.domain.GameState;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.MugloarGateway;
import com.mugloar.solver.AutoPlayer;
import com.mugloar.solver.GameResult;
import com.mugloar.solver.TurnEvent;
import com.mugloar.util.LogSanitizer;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("solver")
class SolverRunner implements CommandLineRunner {

  /** The brief's goal, counted in the summary. */
  private static final int REQUIRED_SCORE = 1000;

  private final MugloarGateway gateway;
  private final AutoPlayer player;
  private final MugloarProperties props;

  @Override
  public void run(String... args) {
    var settings = props.solver();
    List<GameResult> results = new ArrayList<>();
    for (int i = 1; i <= settings.games(); i++) {
      boolean rateLimited;
      try {
        GameResult result = playGame(i, settings);
        results.add(result);
        rateLimited = result.reason() == GameResult.StopReason.RATE_LIMITED;
      } catch (MugloarApiException e) {
        log.warn("Game {}/{} could not be played: {}", i, settings.games(), e.getMessage());
        rateLimited = e.isRateLimited();
      }
      if (rateLimited) {
        log.warn("The game server is rate limiting, so the remaining games won't be played");
        break;
      }
    }
    if (results.isEmpty()) {
      log.info("No game could be played");
      return;
    }
    var scores = results.stream().mapToLong(GameResult::score).summaryStatistics();
    long over1000 = results.stream().filter(r -> r.score() >= REQUIRED_SCORE).count();
    log.info(
        "Played {} game(s): min {}, avg {}, max {}; {} reached 1000+",
        scores.getCount(),
        scores.getMin(),
        Math.round(scores.getAverage()),
        scores.getMax(),
        over1000);
  }

  private GameResult playGame(int number, MugloarProperties.Solver settings) {
    var start = gateway.startGame();
    log.info(
        "Game {}/{} started: {}", number, settings.games(), LogSanitizer.clean(start.gameId()));
    var result =
        player.play(start, settings.targetScore(), settings.maxMoves(), SolverRunner::logTurn);
    log.info(
        "Game {} finished: score {}, {} moves, level {}, {} lives left ({})",
        LogSanitizer.clean(start.gameId()),
        result.score(),
        result.moves(),
        result.finalState().level(),
        result.finalState().lives(),
        result.reason());
    return result;
  }

  private static void logTurn(TurnEvent e) {
    GameState s = e.state();
    log.info(
        "[turn {} | score {} | gold {} | lives {} | lvl {}] {}",
        s.turn(),
        s.score(),
        s.gold(),
        s.lives(),
        s.level(),
        LogSanitizer.clean(e.message()));
  }
}
