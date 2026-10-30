package com.mugloar.web;

import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aQuestEvaluation;
import static com.mugloar.testsupport.Fixtures.aTurnEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mugloar.config.MugloarProperties;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.QuestType;
import com.mugloar.domain.ShopOffer;
import com.mugloar.game.GameService;
import com.mugloar.solver.TurnEvent;
import com.mugloar.testsupport.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(GameController.class)
@Import(FrontendContractTest.Props.class)
class FrontendContractTest {

  private static final Path CONTRACT = Path.of("..", "contract");

  @TestConfiguration
  @EnableConfigurationProperties(MugloarProperties.class)
  static class Props {}

  @Autowired private MockMvc mvc;

  @MockitoBean private GameService games;

  private static String contract(String file) throws IOException {
    return Files.readString(CONTRACT.resolve(file));
  }

  private void assertSends(String path, String file) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().json(contract(file), JsonCompareMode.STRICT));
  }

  @Test
  void gameStateMatches() throws Exception {
    when(games.state("g1"))
        .thenReturn(
            aGameState().lives(2).gold(75).level(3).score(250).highScore(250).turn(9).build());

    assertSends("/api/games/g1", "game-state.json");
  }

  @Test
  void questsMatch() throws Exception {
    Quest quest =
        aQuest()
            .questId("a1")
            .message("Escort a friend to pasture")
            .reward(61)
            .expiresIn(6)
            .probability(Probability.WALK_IN_THE_PARK)
            .encrypted(true)
            .build();
    when(games.board("g1"))
        .thenReturn(
            new QuestBoard(
                List.of(
                    aQuestEvaluation()
                        .quest(quest)
                        .successChance(0.84)
                        .expectedValue(27.24)
                        .build()),
                Optional.of(quest)));

    assertSends("/api/games/g1/quests", "quests.json");
  }

  @Test
  void shopMatches() throws Exception {
    when(games.shop("g1"))
        .thenReturn(new ShopOffer(List.of(Fixtures.POTION), Optional.of(Fixtures.POTION)));

    assertSends("/api/games/g1/shop", "shop.json");
  }

  @Test
  void historyMatches() throws Exception {
    when(games.history("g1"))
        .thenReturn(
            List.of(
                aTurnEvent()
                    .message("Bought Healing potion for 50 gold")
                    .type(TurnEvent.Type.PURCHASE)
                    .state(aGameState().gold(10).turn(4).build())
                    .build()));

    assertSends("/api/games/g1/history", "history.json");
  }

  @Test
  void questTypeLabelsMatch() throws IOException {
    assertThat(names("quest-types.json"))
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(QuestType.values()).map(QuestType::label).toList());
  }

  @Test
  void turnTypesMatch() throws IOException {
    assertThat(names("turn-types.json"))
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(TurnEvent.Type.values()).map(Enum::name).toList());
  }

  private static List<String> names(String file) throws IOException {
    return JsonMapper.shared()
        .readTree(contract(file))
        .valueStream()
        .map(node -> node.asString())
        .toList();
  }
}
