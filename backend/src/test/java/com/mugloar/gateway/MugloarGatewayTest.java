package com.mugloar.gateway;

import static com.mugloar.testsupport.Fixtures.GAME_OVER;
import static com.mugloar.testsupport.Fixtures.GAME_START_DTO;
import static com.mugloar.testsupport.Fixtures.GAME_STATE;
import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.POTION_DTO;
import static com.mugloar.testsupport.Fixtures.aGameStartDto;
import static com.mugloar.testsupport.Fixtures.aPurchaseResult;
import static com.mugloar.testsupport.Fixtures.aPurchaseResultDto;
import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.aReputation;
import static com.mugloar.testsupport.Fixtures.aReputationDto;
import static com.mugloar.testsupport.Fixtures.aSolveResult;
import static com.mugloar.testsupport.Fixtures.aSolveResultDto;
import static com.mugloar.testsupport.Fixtures.gateway;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mugloar.domain.Quest;
import com.mugloar.gateway.client.MugloarClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MugloarGatewayTest {

  private final MugloarClient client = mock(MugloarClient.class);
  private final MugloarGateway service = gateway(client);

  @Test
  void startsAGame() {
    when(client.startGame()).thenReturn(GAME_START_DTO);

    assertThat(service.startGame()).isEqualTo(GAME_STATE);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void aStartWithoutAGameIdIsAnError(String gameId) {
    when(client.startGame()).thenReturn(aGameStartDto().gameId(gameId).build());

    assertThatThrownBy(service::startGame)
        .isInstanceOfSatisfying(
            MugloarApiException.class,
            e -> assertThat(e.status()).isEqualTo(MugloarApiException.NO_RESPONSE));
  }

  @Test
  void decodesTheBoardAndDropsUnreadableQuests() {
    when(client.getMessages("g1"))
        .thenReturn(
            List.of(
                aQuestDto()
                    .questId("nop")
                    .message("Uvqqra wbo")
                    .reward(50)
                    .expiresIn(3)
                    .encrypted(2)
                    .probability("Fher guvat")
                    .build(),
                aQuestDto()
                    .questId("bad")
                    .message("x")
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(99)
                    .build()));

    assertThat(service.getBoard("g1"))
        .extracting(Quest::questId, Quest::rawQuestId, Quest::message)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("abc", "nop", "Hidden job"));
  }

  @Test
  void mapsMovesAndTheShop() {
    when(client.solve("g1", "a"))
        .thenReturn(aSolveResultDto().gold(1).score(2).highScore(3).turn(4).build());
    when(client.buy("g1", "hpot"))
        .thenReturn(aPurchaseResultDto().gold(1).lives(2).level(3).turn(4).build());
    when(client.getShop("g1")).thenReturn(List.of(POTION_DTO));
    when(client.investigateReputation("g1"))
        .thenReturn(aReputationDto().people(1).state(2).underworld(3).build());

    assertThat(service.solve("g1", "a"))
        .isEqualTo(aSolveResult().gold(1).score(2).highScore(3).turn(4).build());
    assertThat(service.buy("g1", "hpot"))
        .isEqualTo(aPurchaseResult().gold(1).lives(2).level(3).turn(4).build());
    assertThat(service.getShop("g1")).containsExactly(POTION);
    assertThat(service.investigateReputation("g1"))
        .isEqualTo(aReputation().people(1).state(2).underworld(3).build());
  }

  @Test
  void clientFailuresPassThroughUnchanged() {
    when(client.solve("g1", "gone")).thenThrow(GAME_OVER);
    when(client.solve("g1", "bad")).thenThrow(new MugloarApiException(400, "rejected"));

    assertThatThrownBy(() -> service.solve("g1", "gone")).isInstanceOf(GameOverException.class);
    assertThatThrownBy(() -> service.solve("g1", "bad")).isInstanceOf(MugloarApiException.class);
  }
}
