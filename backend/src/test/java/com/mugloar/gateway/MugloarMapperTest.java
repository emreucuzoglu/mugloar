package com.mugloar.gateway;

import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.POTION_DTO;
import static com.mugloar.testsupport.Fixtures.aGameStartDto;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aPurchaseResult;
import static com.mugloar.testsupport.Fixtures.aPurchaseResultDto;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.aReputation;
import static com.mugloar.testsupport.Fixtures.aReputationDto;
import static com.mugloar.testsupport.Fixtures.aSolveResult;
import static com.mugloar.testsupport.Fixtures.aSolveResultDto;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.gateway.dto.QuestDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MugloarMapperTest {

  private final MugloarMapper mapper = Mappers.getMapper(MugloarMapper.class);

  @Test
  void gameStartBecomesAStateWithNoReputationYet() {
    assertThat(
            mapper.toGameState(
                aGameStartDto().gold(10).level(2).score(50).highScore(60).turn(7).build()))
        .isEqualTo(aGameState().gold(10).level(2).score(50).highScore(60).turn(7).build());
  }

  @Test
  void mapsEveryFieldOfTheResponses() {
    assertThat(
            mapper.toSolveResult(aSolveResultDto().lives(2).gold(10).highScore(30).turn(4).build()))
        .isEqualTo(aSolveResult().lives(2).gold(10).highScore(30).turn(4).build());
    assertThat(
            mapper.toPurchaseResult(aPurchaseResultDto().gold(1).lives(2).level(3).turn(4).build()))
        .isEqualTo(aPurchaseResult().gold(1).lives(2).level(3).turn(4).build());
    assertThat(mapper.toReputation(aReputationDto().people(1).state(-2).underworld(3).build()))
        .isEqualTo(aReputation().people(1).state(-2).underworld(3).build());
    assertThat(mapper.toShopItems(List.of(POTION_DTO))).containsExactly(POTION);
  }

  @Test
  void questKeepsItsRawIdAndUsesTheDecodedText() {
    QuestDTO quest =
        aQuestDto()
            .questId("encoded")
            .message("encoded text")
            .reward(40)
            .expiresIn(3)
            .encrypted(1)
            .probability("encoded label")
            .build();

    assertThat(mapper.toQuest(quest, "plain", "Plain text", "Sure thing", true))
        .isEqualTo(
            aQuest()
                .questId("plain")
                .rawQuestId("encoded")
                .message("Plain text")
                .reward(40)
                .expiresIn(3)
                .encrypted(true)
                .build());
  }
}
