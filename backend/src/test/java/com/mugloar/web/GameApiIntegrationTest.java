package com.mugloar.web;

import static com.mugloar.testsupport.StubGameServer.json;
import static com.mugloar.testsupport.StubGameServer.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.testsupport.SimulatedMugloarClient;
import com.mugloar.testsupport.StubGameServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameApiIntegrationTest {

  private static final int CREATED = 201;
  private static final int OK = 200;
  private static final int BAD_REQUEST = 400;
  private static final int NOT_FOUND = 404;
  private static final int CONFLICT = 409;
  private static final int GONE = 410;
  private static final int INTERNAL_ERROR = 500;
  private static final int BAD_GATEWAY = 502;
  private static final int SERVICE_UNAVAILABLE = 503;
  private static final int MOST_TURNS_IN_A_GAME = 2000;
  private static final String SECRET = "upstream-secret-text";
  private static final String SIMULATED_GAME = "SIMGAME1";

  private static final StubGameServer UPSTREAM = new StubGameServer();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void pointAtTheStub(DynamicPropertyRegistry registry) {
    registry.add("mugloar.base-url", UPSTREAM::baseUrl);
    registry.add("mugloar.min-request-interval", () -> "0s");
    registry.add("mugloar.read-attempts", () -> "1");
  }

  @AfterAll
  static void stopUpstream() {
    UPSTREAM.close();
  }

  @BeforeEach
  void freshGame() {
    UPSTREAM.answerFrom(new SimulatedMugloarClient(42));
  }

  private HttpResponse<String> call(String method, String path, String origin) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
    if (origin != null) {
      request.header("Origin", origin);
    }
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  private HttpResponse<String> call(String method, String path) throws Exception {
    return call(method, path, null);
  }

  private static JsonNode body(HttpResponse<String> response) {
    return JsonMapper.shared().readTree(response.body());
  }

  private String startGame() throws Exception {
    HttpResponse<String> started = call("POST", "/api/games");
    assertThat(started.statusCode()).isEqualTo(CREATED);
    return body(started).path("gameId").asString();
  }

  @Test
  void playsAWholeGameThroughTheApiUntilTheDragonDies() throws Exception {
    String game = startGame();
    assertThat(game).isEqualTo(SIMULATED_GAME);

    int lives = 1;
    int steps = 0;
    while (lives > 0 && steps < MOST_TURNS_IN_A_GAME) {
      HttpResponse<String> step = call("POST", "/api/games/" + game + "/autoplay/step");
      assertThat(step.statusCode()).isEqualTo(OK);
      JsonNode events = body(step);
      lives = events.get(events.size() - 1).path("state").path("lives").asInt();
      steps++;
    }

    JsonNode state = body(call("GET", "/api/games/" + game));
    assertThat(state.path("lives").asInt()).isZero();
    assertThat(state.path("score").asInt()).isPositive();
    assertThat(body(call("GET", "/api/games/" + game + "/history"))).isNotEmpty();
    assertThat(body(call("GET", "/api/games/" + game + "/quests"))).isEmpty();
    assertThat(call("POST", "/api/games/" + game + "/autoplay/step").statusCode()).isEqualTo(GONE);
  }

  @Test
  void aPlayerCanTakeTheRecommendedJobAndSeeTheBoardChange() throws Exception {
    String game = startGame();
    JsonNode quests = body(call("GET", "/api/games/" + game + "/quests"));
    assertThat(quests).isNotEmpty();
    JsonNode pick = null;
    for (JsonNode quest : quests) {
      if (quest.path("recommended").asBoolean()) {
        pick = quest;
      }
    }
    assertThat(pick).isNotNull();
    String questId = pick.path("questId").asString();

    HttpResponse<String> solved =
        call("POST", "/api/games/" + game + "/quests/" + questId + "/solve");

    assertThat(solved.statusCode()).isEqualTo(OK);
    assertThat(body(solved).path("state").path("turn").asInt()).isEqualTo(1);
    assertThat(body(call("GET", "/api/games/" + game + "/quests")).toString())
        .doesNotContain(questId);
    assertThat(body(call("GET", "/api/games/" + game + "/shop"))).isNotEmpty();
  }

  @Test
  void trackedStateNeverReachesTheBrowser() throws Exception {
    HttpResponse<String> started = call("POST", "/api/games");

    assertThat(started.body()).doesNotContain("stateReputation").doesNotContain("itemsBought");
  }

  @Test
  void aJobNoLongerOnTheBoardIsAConflict() throws Exception {
    String game = startGame();

    assertThat(call("POST", "/api/games/" + game + "/quests/ghost/solve").statusCode())
        .isEqualTo(CONFLICT);
  }

  @Test
  void anUnknownGameIsNotFound() throws Exception {
    assertThat(call("GET", "/api/games/nobody").statusCode()).isEqualTo(NOT_FOUND);
  }

  @Test
  void aMalformedIdIsABadRequest() throws Exception {
    HttpResponse<String> response = call("GET", "/api/games/bad!id");

    assertThat(response.statusCode()).isEqualTo(BAD_REQUEST);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
  }

  @Test
  void anUpstreamFailureIsABadGatewayWithoutTheUpstreamText() throws Exception {
    UPSTREAM.answerWith(status(BAD_GATEWAY + 1, "text/html", "<html>" + SECRET + "</html>"));

    HttpResponse<String> response = call("POST", "/api/games");

    assertThat(response.statusCode()).isEqualTo(BAD_GATEWAY);
    assertThat(response.body()).doesNotContain(SECRET);
  }

  @Test
  void rubbishFromUpstreamIsABadGatewayWithoutTheRubbish() throws Exception {
    UPSTREAM.answerWith(json(OK, "{\"" + SECRET + "\":"));

    HttpResponse<String> response = call("POST", "/api/games");

    assertThat(response.statusCode()).isEqualTo(BAD_GATEWAY);
    assertThat(response.body()).doesNotContain(SECRET);
  }

  @Test
  void persistentRateLimitingIsServiceUnavailable() throws Exception {
    UPSTREAM.answerWith(exchange -> {
      exchange.getResponseHeaders().set("Retry-After", "600");
      exchange.sendResponseHeaders(429, StubGameServer.NO_BODY);
    });

    assertThat(call("POST", "/api/games").statusCode()).isEqualTo(SERVICE_UNAVAILABLE);
  }

  @Test
  void aGameStartedWithoutAnIdIsABadGateway() throws Exception {
    UPSTREAM.answerWith(
        json(
            OK,
            """
            {"gameId":null,"lives":3,"gold":0,"level":0,"score":0,"highScore":0,"turn":0}
            """));

    assertThat(call("POST", "/api/games").statusCode()).isEqualTo(BAD_GATEWAY);
  }

  @Test
  void aFailureNoHandlerClaimsRevealsNothing() throws Exception {
    String game = startGame();
    // A null shop entry is an unexpected state the solver doesn't guard against.
    UPSTREAM.answerWith(json(OK, "[null]"));

    HttpResponse<String> response = call("GET", "/api/games/" + game + "/shop");

    assertThat(response.statusCode()).isEqualTo(INTERNAL_ERROR);
    assertThat(response.body())
        .doesNotContain("Exception")
        .doesNotContain("java.")
        .doesNotContain("trace")
        .doesNotContainIgnoringCase("tomcat");
  }

  @Test
  void anotherOriginIsRefusedAndNoGameStarts() throws Exception {
    int before = UPSTREAM.requests().size();

    HttpResponse<String> response = call("POST", "/api/games", "https://evil.example");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(UPSTREAM.requests()).hasSize(before);
  }
}
