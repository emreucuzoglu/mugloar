package com.mugloar.web;

import static com.mugloar.testsupport.Fixtures.GAME_OVER;
import static com.mugloar.testsupport.Fixtures.GAME_STATE;
import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aQuestEvaluation;
import static com.mugloar.testsupport.Fixtures.aTurnEvent;
import static com.mugloar.testsupport.Fixtures.shopItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mugloar.config.MugloarProperties;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.ShopOffer;
import com.mugloar.game.GameNotFoundException;
import com.mugloar.game.GameService;
import com.mugloar.game.UnavailableException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.solver.TurnEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GameController.class)
@Import(GameControllerTest.Props.class)
class GameControllerTest {

  @TestConfiguration
  @EnableConfigurationProperties(MugloarProperties.class)
  static class Props {}

  @Autowired private MockMvc mvc;

  @MockitoBean private GameService games;

  @Test
  void startsGame() throws Exception {
    when(games.start()).thenReturn(GAME_STATE);

    mvc.perform(post("/api/games"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gameId").value("g1"))
        .andExpect(jsonPath("$.lives").value(3));
  }

  @Test
  void listsRankedQuestsAndMarksRecommendation() throws Exception {
    Quest risky =
        aQuest()
            .questId("r")
            .message("Risky job")
            .reward(90)
            .expiresIn(3)
            .probability(Probability.RISKY)
            .build();
    Quest safe =
        aQuest().questId("s").message("Safe job").reward(20).expiresIn(6).encrypted(true).build();
    when(games.board("g1"))
        .thenReturn(
            new QuestBoard(
                List.of(
                    aQuestEvaluation().quest(safe).build(),
                    aQuestEvaluation()
                        .quest(risky)
                        .successChance(0.4)
                        .expectedValue(0.0)
                        .acceptable(false)
                        .build()),
                Optional.of(safe)));

    mvc.perform(get("/api/games/g1/quests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].questId").value("s"))
        .andExpect(jsonPath("$[0].rank").value(1))
        .andExpect(jsonPath("$[0].recommended").value(true))
        .andExpect(jsonPath("$[0].probability").value("Sure thing"))
        .andExpect(jsonPath("$[0].encrypted").value(true))
        .andExpect(jsonPath("$[1].recommended").value(false))
        .andExpect(jsonPath("$[1].acceptable").value(false));
  }

  @Test
  void listsShop() throws Exception {
    when(games.shop("g1")).thenReturn(new ShopOffer(List.of(POTION), Optional.empty()));

    mvc.perform(get("/api/games/g1/shop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("hpot"))
        .andExpect(jsonPath("$[0].cost").value(50))
        .andExpect(jsonPath("$[0].recommended").value(false));
  }

  @Test
  void marksTheItemTheSolverWouldBuy() throws Exception {
    ShopItem potion = POTION;
    when(games.shop("g1"))
        .thenReturn(
            new ShopOffer(
                List.of(potion, shopItem("cs", "Claw Sharpening", 100)), Optional.of(potion)));

    mvc.perform(get("/api/games/g1/shop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].recommended").value(true))
        .andExpect(jsonPath("$[1].recommended").value(false));
  }

  @Test
  void marksNoQuestWhenNoneIsRecommended() throws Exception {
    Quest safe = aQuest().questId("s").message("Safe job").reward(20).expiresIn(6).build();
    when(games.board("g1"))
        .thenReturn(
            new QuestBoard(List.of(aQuestEvaluation().quest(safe).build()), Optional.empty()));

    mvc.perform(get("/api/games/g1/quests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rank").value(1))
        .andExpect(jsonPath("$[0].recommended").value(false));
  }

  @Test
  void solvesQuest() throws Exception {
    when(games.solve("g1", "ad1"))
        .thenReturn(
            aTurnEvent()
                .state(aGameState().gold(20).score(20).highScore(20).turn(1).build())
                .build());

    mvc.perform(post("/api/games/g1/quests/ad1/solve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SOLVE"))
        .andExpect(jsonPath("$.state.score").value(20));
  }

  @Test
  void autoplayStepReturnsEvents() throws Exception {
    when(games.autoplayStep("g1"))
        .thenReturn(
            List.of(
                aTurnEvent()
                    .type(TurnEvent.Type.PURCHASE)
                    .message("Bought")
                    .state(GAME_STATE)
                    .build()));

    mvc.perform(post("/api/games/g1/autoplay/step"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("PURCHASE"));
  }

  @Test
  void rejectsMalformedIdsBeforeCallingService() throws Exception {
    mvc.perform(get("/api/games/bad!id/quests"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("One or more ids have an invalid format."));
    mvc.perform(post("/api/games/g1/quests/" + "x".repeat(65) + "/solve"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/games/g1/shop/HPOT/buy")).andExpect(status().isBadRequest());

    verifyNoInteractions(games);
  }

  @Test
  void unknownGameIs404() throws Exception {
    when(games.state("nope")).thenThrow(new GameNotFoundException("nope"));

    mvc.perform(get("/api/games/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Game not found"));
  }

  @Test
  void unavailableQuestIs409() throws Exception {
    when(games.solve("g1", "old"))
        .thenThrow(new UnavailableException("Quest old is no longer on the board"));

    mvc.perform(post("/api/games/g1/quests/old/solve"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Quest old is no longer on the board"));
  }

  @Test
  void finishedGameIs410() throws Exception {
    when(games.autoplayStep("g1")).thenThrow(GAME_OVER);

    mvc.perform(post("/api/games/g1/autoplay/step"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.title").value("Game over"));
  }

  @Test
  void rateLimitedByTheGameServerIs503AndSaysToWait() throws Exception {
    when(games.autoplayStep(anyString()))
        .thenThrow(new MugloarApiException(429, "slow down, secrets"));

    mvc.perform(post("/api/games/g1/autoplay/step"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.detail").value("The game server is busy. Wait a minute and try again."));
  }

  @Test
  void upstreamFailureIs502WithoutLeakingDetails() throws Exception {
    when(games.investigate(anyString()))
        .thenThrow(new MugloarApiException(500, "stack trace secrets"));

    mvc.perform(post("/api/games/g1/investigate"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value(not(containsString("secrets"))));
  }

  @Test
  void unreachableGameServerIs502WithAClearMessage() throws Exception {
    when(games.start())
        .thenThrow(new MugloarApiException(MugloarApiException.NO_RESPONSE, "connection refused"));

    mvc.perform(post("/api/games"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.title").value("Game server error"))
        .andExpect(
            jsonPath("$.detail")
                .value("The game server could not be reached. Try again in a moment."));
  }

  @Test
  void upstreamRefusalIs502WithoutTheUpstreamText() throws Exception {
    when(games.start()).thenThrow(new MugloarApiException(400, "stack trace secrets"));

    mvc.perform(post("/api/games"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value("The game server rejected the request (HTTP 400)."))
        .andExpect(content().string(not(containsString("secrets"))));
  }

  @Test
  void problemsUseTheProblemJsonMediaType() throws Exception {
    when(games.state("nope")).thenThrow(new GameNotFoundException("nope"));

    mvc.perform(get("/api/games/nope"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void wrongMethodIs405() throws Exception {
    mvc.perform(get("/api/games/g1/autoplay/step")).andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(games);
  }

  @Test
  void unknownRouteIs404() throws Exception {
    mvc.perform(get("/api/nothing-here")).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource({"64, 200", "65, 400"})
  void gameIdLengthIsLimited(int length, int expected) throws Exception {
    when(games.state(anyString())).thenReturn(GAME_STATE);

    mvc.perform(get("/api/games/" + "x".repeat(length))).andExpect(status().is(expected));
  }

  @ParameterizedTest
  @CsvSource({"32, 200", "33, 400"})
  void itemIdLengthIsLimited(int length, int expected) throws Exception {
    when(games.buy(anyString(), anyString())).thenReturn(aTurnEvent().state(GAME_STATE).build());

    mvc.perform(post("/api/games/g1/shop/" + "x".repeat(length) + "/buy"))
        .andExpect(status().is(expected));
  }

  @Test
  void aQuestWithoutAMessageIsSentAsEmptyText() throws Exception {
    Quest silent = aQuest().message(null).build();
    when(games.board("g1"))
        .thenReturn(
            new QuestBoard(List.of(aQuestEvaluation().quest(silent).build()), Optional.empty()));

    mvc.perform(get("/api/games/g1/quests")).andExpect(jsonPath("$[0].message").value(""));
  }

  @Test
  void aRealRequestFromAnotherOriginIsRefused() throws Exception {
    mvc.perform(post("/api/games").header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(games);
  }

  @Test
  void aRealRequestFromTheFrontendCarriesTheCorsHeader() throws Exception {
    when(games.start()).thenReturn(GAME_STATE);

    mvc.perform(post("/api/games").header("Origin", "http://localhost:5173"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void returnsGameState() throws Exception {
    when(games.state("g1")).thenReturn(GAME_STATE);

    mvc.perform(get("/api/games/g1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameId").value("g1"));
  }

  @Test
  void returnsHistory() throws Exception {
    when(games.history("g1")).thenReturn(List.of(aTurnEvent().state(GAME_STATE).build()));

    mvc.perform(get("/api/games/g1/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].message").value("Succeeded"));
  }

  @Test
  void buysItem() throws Exception {
    when(games.buy("g1", "hpot"))
        .thenReturn(
            aTurnEvent().type(TurnEvent.Type.PURCHASE).message("Bought").state(GAME_STATE).build());

    mvc.perform(post("/api/games/g1/shop/hpot/buy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("PURCHASE"));
  }

  @Test
  void investigates() throws Exception {
    when(games.investigate("g1"))
        .thenReturn(
            aTurnEvent()
                .type(TurnEvent.Type.INVESTIGATE)
                .message("Reputation")
                .state(GAME_STATE)
                .build());

    mvc.perform(post("/api/games/g1/investigate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("INVESTIGATE"));
  }

  @Test
  void emptyBoardIsAnEmptyList() throws Exception {
    when(games.board("g1")).thenReturn(QuestBoard.EMPTY);

    mvc.perform(get("/api/games/g1/quests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void allowsConfiguredFrontendOrigin() throws Exception {
    mvc.perform(
            options("/api/games")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void refusesOtherOrigins() throws Exception {
    mvc.perform(
            options("/api/games")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }
}
