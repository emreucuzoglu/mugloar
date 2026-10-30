package com.mugloar.config;

import static com.mugloar.testsupport.StubGameServer.GONE;
import static com.mugloar.testsupport.StubGameServer.OK;
import static com.mugloar.testsupport.StubGameServer.hangUp;
import static com.mugloar.testsupport.StubGameServer.json;
import static com.mugloar.testsupport.StubGameServer.stall;
import static com.mugloar.testsupport.StubGameServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.client.MugloarClient;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.testsupport.StubGameServer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GameClientWireTest {

  private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
  private static final long LONGER_THAN_THE_TIMEOUT = 2000;
  private static final int SERVER_ERROR = 500;
  private static final int TOO_MANY_REQUESTS = 429;

  private final StubGameServer server = new StubGameServer();
  private final MugloarClient client = realClient();

  private MugloarClient realClient() {
    MugloarProperties props =
        new MugloarProperties(
            server.baseUrl(),
            READ_TIMEOUT,
            READ_TIMEOUT,
            2,
            Duration.ZERO,
            null,
            1,
            null,
            null,
            new MugloarProperties.Solver(1, 0, 1));
    return new GameConfig().mugloarClient(RestClient.builder(), props);
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private static void assertNoResponse(Throwable thrown) {
    assertThat(thrown).isInstanceOf(MugloarApiException.class);
    assertThat(((MugloarApiException) thrown).status()).isEqualTo(MugloarApiException.NO_RESPONSE);
  }

  @Test
  void aMoveIsNotResentWhenTheConnectionDropsWithoutAnAnswer() {
    server.answerWith(hangUp());

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .satisfies(GameClientWireTest::assertNoResponse);

    assertThat(server.requests()).containsExactly("POST /g1/solve/ad1");
  }

  @Test
  void aMoveIsNotResentWhenAReusedConnectionDropsWithoutAnAnswer() {
    StubGameServer.Script answer =
        json(
            OK,
            """
            {"success":true,"lives":3,"gold":1,"score":1,"highScore":1,"turn":1,"message":"ok"}
            """);
    StubGameServer.Script hangUp = hangUp();
    AtomicInteger seen = new AtomicInteger();
    server.answerWith(exchange -> {
      if (seen.getAndIncrement() == 0) {
        answer.answer(exchange);
      } else {
        hangUp.answer(exchange);
      }
    });
    client.solve("g1", "first");

    assertThatThrownBy(() -> client.solve("g1", "second"))
        .satisfies(GameClientWireTest::assertNoResponse);

    assertThat(server.requests()).containsExactly("POST /g1/solve/first", "POST /g1/solve/second");
  }

  @Test
  void aPurchaseIsNotResentWhenTheConnectionDropsWithoutAnAnswer() {
    server.answerWith(hangUp());

    assertThatThrownBy(() -> client.buy("g1", "hpot"))
        .satisfies(GameClientWireTest::assertNoResponse);

    assertThat(server.requests()).containsExactly("POST /g1/shop/buy/hpot");
  }

  @Test
  void aMoveThatTakesTooLongIsNotResent() {
    server.answerWith(stall(LONGER_THAN_THE_TIMEOUT));

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .satisfies(GameClientWireTest::assertNoResponse);

    assertThat(server.requests()).hasSize(1);
  }

  @Test
  void aReadThatTakesTooLongIsRetriedUpToTheConfiguredAttempts() {
    server.answerWith(stall(LONGER_THAN_THE_TIMEOUT));

    assertThatThrownBy(() -> client.getShop("g1")).satisfies(GameClientWireTest::assertNoResponse);

    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void aRawIdKeepsItsBase64CharactersWhenPutInThePath() {
    server.answerWith(
        json(
            OK,
            """
            {"success":true,"lives":3,"gold":1,"score":1,"highScore":1,"turn":1,"message":"ok"}
            """));

    client.solve("g1", "a+b/c==");

    assertThat(server.requests()).containsExactly("POST /g1/solve/a+b/c==");
    assertThat(server.rawPaths()).singleElement().asString().contains("a%2Bb%2Fc%3D%3D");
  }

  @Test
  void unknownFieldsAndNullEncryptionAreAccepted() {
    server.answerWith(
        json(
            OK,
            """
            [{"adId":"a","message":"Job","reward":1,"expiresIn":1,"encrypted":null,
              "probability":"Gamble","somethingNew":{"nested":true}}]
            """));

    List<QuestDTO> board = client.getMessages("g1");

    assertThat(board).singleElement().satisfies(q -> assertThat(q.encrypted()).isNull());
  }

  @Test
  void aCutOffJsonBodyIsReportedAsNoResponse() {
    server.answerWith(json(OK, "{\"gameId\":\"g1\",\"li"));

    assertThatThrownBy(client::startGame).satisfies(GameClientWireTest::assertNoResponse);
  }

  @Test
  void aSuccessWithTheWrongJsonShapeIsReportedAsNoResponse() {
    server.answerWith(json(OK, "{\"not\":\"a list\"}"));

    assertThatThrownBy(() -> client.getMessages("g1"))
        .satisfies(GameClientWireTest::assertNoResponse);
  }

  @Test
  void anHtmlErrorPageKeepsItsStatus() {
    server.answerWith(status(SERVER_ERROR, "text/html", "<html><body>maintenance</body></html>"));

    assertThatThrownBy(client::startGame)
        .isInstanceOfSatisfying(
            MugloarApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(SERVER_ERROR);
              assertThat(e.isTransient()).isTrue();
            });
  }

  @Test
  void goneOverRealHttpMeansGameOver() {
    server.answerWith(json(GONE, "{\"status\":\"Game Over\"}"));

    assertThatThrownBy(() -> client.solve("g1", "ad1")).isInstanceOf(GameOverException.class);
  }

  @Test
  void aLongRetryAfterStopsAtOnce() {
    server.answerWith(exchange -> {
      exchange.getResponseHeaders().set("Retry-After", "600");
      exchange.sendResponseHeaders(TOO_MANY_REQUESTS, StubGameServer.NO_BODY);
    });

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .isInstanceOfSatisfying(
            MugloarApiException.class, e -> assertThat(e.isRateLimited()).isTrue());

    assertThat(server.requests()).hasSize(1);
  }
}
