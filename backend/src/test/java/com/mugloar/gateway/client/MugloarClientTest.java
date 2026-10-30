package com.mugloar.gateway.client;

import static com.mugloar.testsupport.Fixtures.POTION_DTO;
import static com.mugloar.testsupport.Fixtures.aGameStartDto;
import static com.mugloar.testsupport.Fixtures.aPurchaseResultDto;
import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.aReputationDto;
import static com.mugloar.testsupport.Fixtures.aSolveResultDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.dto.QuestDTO;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MugloarClientTest {

  private static final String BASE = "https://dragonsofmugloar.com/api/v2";
  private static final String SOLVED_JSON =
      """
      {"success":true,"lives":3,"gold":33,"score":33,"highScore":33,"turn":1,"message":"Done"}
      """;

  private final List<Duration> waits = new ArrayList<>();
  private MockRestServiceServer server;
  private MugloarClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new MugloarClient(
            builder.build(), MugloarRetry.forReads(2), new RequestPacer(Duration.ZERO, waits::add));
  }

  @Test
  void rateLimitedMoveIsSentAgainAfterTheServersWait() {
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2"));
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withSuccess(SOLVED_JSON, MediaType.APPLICATION_JSON));

    assertThat(client.solve("g1", "ad1").success()).isTrue();

    assertThat(waits).containsExactly(Duration.ofSeconds(2));
    server.verify();
  }

  @Test
  void rateLimitedReadIsSentAgainAfterWaiting() {
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.getShop("g1")).isEmpty();

    assertThat(waits).containsExactly(Duration.ofSeconds(1));
  }

  @Test
  void stillRateLimitedAfterTheLastWaitIsReported() {
    server
        .expect(ExpectedCount.times(4), requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .isInstanceOfSatisfying(
            MugloarApiException.class, e -> assertThat(e.isRateLimited()).isTrue());

    server.verify();
  }

  @Test
  void aReadThatStaysRateLimitedIsNotMultipliedByTheReadRetries() {
    server
        .expect(ExpectedCount.times(4), requestTo(BASE + "/g1/messages"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.getMessages("g1"))
        .isInstanceOfSatisfying(
            MugloarApiException.class, e -> assertThat(e.isRateLimited()).isTrue());

    server.verify();
  }

  @Test
  void startsGame() {
    server
        .expect(requestTo(BASE + "/game/start"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"gameId":"DW7XIae5","lives":3,"gold":0,"level":0,"score":0,"highScore":0,"turn":0}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.startGame()).isEqualTo(aGameStartDto().gameId("DW7XIae5").build());
    server.verify();
  }

  @Test
  void readsMessagesIncludingNullEncryptionAndUnknownFields() {
    server
        .expect(requestTo(BASE + "/DW7XIae5/messages"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"adId":"xysWfIGS","message":"Create an advertisement campaign","reward":33,
                  "expiresIn":6,"encrypted":null,"probability":"Piece of cake","somethingNew":true},
                 {"adId":"QUJD","message":"SGk=","reward":5,"expiresIn":2,"encrypted":1,"probability":"R2FtYmxl"}]
                """,
                MediaType.APPLICATION_JSON));

    List<QuestDTO> quests = client.getMessages("DW7XIae5");

    assertThat(quests)
        .containsExactly(
            aQuestDto()
                .questId("xysWfIGS")
                .message("Create an advertisement campaign")
                .reward(33)
                .expiresIn(6)
                .probability("Piece of cake")
                .build(),
            aQuestDto()
                .questId("QUJD")
                .message("SGk=")
                .reward(5)
                .expiresIn(2)
                .encrypted(1)
                .probability("R2FtYmxl")
                .build());
  }

  @Test
  void solvesQuest() {
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"success":true,"lives":3,"gold":33,"score":33,"highScore":33,"turn":1,"message":"Done"}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.solve("g1", "ad1"))
        .isEqualTo(aSolveResultDto().gold(33).score(33).highScore(33).message("Done").build());
  }

  @Test
  void readsShopAndBuys() {
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"hpot","name":"Healing potion","cost":50}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE + "/g1/shop/buy/hpot"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"shoppingSuccess":true,"gold":10,"lives":3,"level":0,"turn":5}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.getShop("g1")).containsExactly(POTION_DTO);
    assertThat(client.buy("g1", "hpot")).isEqualTo(aPurchaseResultDto().turn(5).build());
  }

  @Test
  void goneMeansGameOver() {
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withStatus(HttpStatus.GONE).body("{\"status\":\"Game Over\"}"));

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .isInstanceOf(GameOverException.class)
        .extracting(e -> ((MugloarApiException) e).status())
        .isEqualTo(410);
  }

  @Test
  void badRequestKeepsStatusAndBody() {
    server
        .expect(requestTo(BASE + "/g1/solve/old"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("No ad by this ID exists"));

    assertThatThrownBy(() -> client.solve("g1", "old"))
        .isInstanceOf(MugloarApiException.class)
        .isNotInstanceOf(GameOverException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("No ad by this ID exists");
  }

  @Test
  void getIsRetriedOnServerError() {
    server.expect(requestTo(BASE + "/g1/shop")).andRespond(withServerError());
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.getShop("g1")).isEmpty();
    server.verify();
  }

  @Test
  void getGivesUpAfterConfiguredAttempts() {
    server
        .expect(ExpectedCount.times(2), requestTo(BASE + "/g1/messages"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.getMessages("g1"))
        .isInstanceOf(MugloarApiException.class)
        .satisfies(e -> assertThat(((MugloarApiException) e).isTransient()).isTrue());
    server.verify();
  }

  @Test
  void movesAreNeverRetried() {
    server
        .expect(ExpectedCount.once(), requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.solve("g1", "ad1")).isInstanceOf(MugloarApiException.class);
    server.verify();
  }

  @Test
  void unparsableBodyIsReportedAsApiError() {
    server
        .expect(requestTo(BASE + "/game/start"))
        .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

    assertThatThrownBy(() -> client.startGame()).isInstanceOf(MugloarApiException.class);
  }

  @Test
  void investigatesReputation() {
    server
        .expect(requestTo(BASE + "/g1/investigate/reputation"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"people":1.5,"state":0,"underworld":-2}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.investigateReputation("g1"))
        .isEqualTo(aReputationDto().people(1.5).underworld(-2).build());
  }

  @Test
  void unreachableServerIsReportedAsNoResponse() {
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(request -> {
          throw new IOException("Connection refused");
        });

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .isInstanceOf(MugloarApiException.class)
        .hasMessageContaining("unreachable")
        .satisfies(e -> {
          assertThat(((MugloarApiException) e).status()).isEqualTo(MugloarApiException.NO_RESPONSE);
          assertThat(((MugloarApiException) e).isTransient()).isTrue();
        });
  }

  @Test
  void getIsRetriedWhenServerIsUnreachable() {
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andRespond(request -> {
          throw new IOException("Connection refused");
        });
    server
        .expect(requestTo(BASE + "/g1/shop"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.getShop("g1")).isEmpty();
    server.verify();
  }

  @Test
  void getIsNotRetriedOnClientError() {
    server
        .expect(ExpectedCount.once(), requestTo(BASE + "/g1/messages"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.getMessages("g1"))
        .isInstanceOf(MugloarApiException.class)
        .satisfies(e -> assertThat(((MugloarApiException) e).status()).isEqualTo(400));
    server.verify();
  }

  @Test
  void getIsNotRetriedWhenGameIsOver() {
    server
        .expect(ExpectedCount.once(), requestTo(BASE + "/g1/messages"))
        .andRespond(withStatus(HttpStatus.GONE));

    assertThatThrownBy(() -> client.getMessages("g1")).isInstanceOf(GameOverException.class);
    server.verify();
  }

  @Test
  void oneAttemptMeansNoRetry() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer oneShot = MockRestServiceServer.bindTo(builder).build();
    oneShot
        .expect(ExpectedCount.once(), requestTo(BASE + "/g1/shop"))
        .andRespond(withServerError());

    assertThatThrownBy(() ->
            new MugloarClient(
                    builder.build(), MugloarRetry.forReads(1), new RequestPacer(Duration.ZERO))
                .getShop("g1"))
        .isInstanceOf(MugloarApiException.class);
    oneShot.verify();
  }

  @Test
  void interruptedRetryStopsAndKeepsTheInterruptFlag() {
    server.expect(ExpectedCount.once(), requestTo(BASE + "/g1/shop")).andRespond(withServerError());
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> client.getShop("g1")).isInstanceOf(MugloarApiException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      server.verify();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void emptySuccessBodyIsReportedAsApiError() {
    server.expect(requestTo(BASE + "/game/start")).andRespond(withSuccess());

    assertThatThrownBy(() -> client.startGame())
        .isInstanceOf(MugloarApiException.class)
        .hasMessageContaining("empty body");
  }

  @Test
  void emptyErrorBodyIsNamedInTheMessage() {
    server.expect(requestTo(BASE + "/g1/solve/ad1")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.solve("g1", "ad1")).hasMessageContaining("(empty body)");
  }

  @Test
  void longErrorBodyIsCutShort() {
    server
        .expect(requestTo(BASE + "/g1/solve/ad1"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("x".repeat(301)));

    assertThatThrownBy(() -> client.solve("g1", "ad1"))
        .hasMessageEndingWith("x".repeat(299) + "…")
        .hasMessageNotContaining("x".repeat(300));
  }
}
