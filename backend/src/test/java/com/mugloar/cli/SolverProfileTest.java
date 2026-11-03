package com.mugloar.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.MugloarApplication;
import com.mugloar.testsupport.SimulatedMugloarClient;
import com.mugloar.testsupport.StubGameServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class SolverProfileTest {

  private static final long FALLS_BEHIND = 42;
  private static final long TIMEOUT_SECONDS = 60;

  private static ConfigurableApplicationContext run(StubGameServer server, int games) {
    return new SpringApplicationBuilder(MugloarApplication.class)
        .profiles("solver")
        .run(
            "--mugloar.base-url=" + server.baseUrl(),
            "--mugloar.min-request-interval=0s",
            "--mugloar.read-attempts=1",
            "--mugloar.solver.games=" + games,
            "--mugloar.solver.target-score=1000");
  }

  @Test
  void playsTheGamesWithoutAWebServerAndPrintsTheSummary(CapturedOutput output) {
    try (StubGameServer server = new StubGameServer()) {
      server.answerFrom(new SimulatedMugloarClient(42));

      try (ConfigurableApplicationContext context = run(server, 1)) {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
      }

      assertThat(server.requests()).contains("POST /game/start");
      assertThat(output).contains("Played 1 game(s)");
    }
  }

  /** Only the connection is set, so the target, move limit and game count are the shipped ones. */
  @Test
  @Timeout(TIMEOUT_SECONDS)
  void theShippedDefaultsPlayOneGameUntilTheDragonDies(CapturedOutput output) {
    try (StubGameServer server = new StubGameServer()) {
      server.answerFrom(new SimulatedMugloarClient(FALLS_BEHIND));

      new SpringApplicationBuilder(MugloarApplication.class)
          .profiles("solver")
          .run(
              "--mugloar.base-url=" + server.baseUrl(),
              "--mugloar.min-request-interval=0s",
              "--mugloar.read-attempts=1")
          .close();

      long starts = server.requests().stream().filter("POST /game/start"::equals).count();
      assertThat(starts).isOne();
      assertThat(output).contains("(GAME_OVER)", "Played 1 game(s)");
    }
  }

  @Test
  void aServerThatIsDownStillEndsWithAClearLine(CapturedOutput output) {
    try (StubGameServer server = new StubGameServer()) {
      server.answerWith(StubGameServer.status(503, "text/plain", "down"));

      try (ConfigurableApplicationContext context = run(server, 2)) {
        assertThat(context.isRunning()).isTrue();
      }

      assertThat(server.requests()).hasSize(2);
      assertThat(output).contains("No game could be played");
    }
  }
}
