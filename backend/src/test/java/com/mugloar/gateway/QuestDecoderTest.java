package com.mugloar.gateway;

import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.b64;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.util.DecodeUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class QuestDecoderTest {

  private static final int RANDOM_TEXTS = 200;
  private static final int MAX_TEXT = 40;

  private final QuestDecoder decoder = new QuestDecoder(Mappers.getMapper(MugloarMapper.class));

  @Test
  void plainQuestIsPassedThrough() {
    QuestDTO quest =
        aQuestDto()
            .questId("xysWfIGS")
            .message("Create an advertisement campaign")
            .reward(33)
            .expiresIn(6)
            .probability("Piece of cake")
            .build();

    Quest decoded = decoder.decode(quest).orElseThrow();

    assertThat(decoded.questId()).isEqualTo("xysWfIGS");
    assertThat(decoded.rawQuestId()).isEqualTo("xysWfIGS");
    assertThat(decoded.message()).isEqualTo("Create an advertisement campaign");
    assertThat(decoded.reward()).isEqualTo(33);
    assertThat(decoded.expiresIn()).isEqualTo(6);
    assertThat(decoded.probability()).isEqualTo(Probability.PIECE_OF_CAKE);
    assertThat(decoded.encrypted()).isFalse();
  }

  @Test
  void base64QuestIsDecoded() {
    QuestDTO quest =
        aQuestDto()
            .questId(b64("abc123"))
            .message(b64("Help a farmer"))
            .reward(40)
            .expiresIn(3)
            .encrypted(1)
            .probability(b64("Walk in the park"))
            .build();

    Quest decoded = decoder.decode(quest).orElseThrow();

    assertThat(decoded.questId()).isEqualTo("abc123");
    assertThat(decoded.rawQuestId()).isEqualTo(b64("abc123"));
    assertThat(decoded.message()).isEqualTo("Help a farmer");
    assertThat(decoded.probability()).isEqualTo(Probability.WALK_IN_THE_PARK);
    assertThat(decoded.encrypted()).isTrue();
  }

  @Test
  void rot13QuestIsDecoded() {
    // "Sure thing" in ROT13 is "Fher guvat"
    QuestDTO quest =
        aQuestDto()
            .questId("nop")
            .message("Uryc n xavtug")
            .reward(10)
            .expiresIn(2)
            .encrypted(2)
            .probability("Fher guvat")
            .build();

    Quest decoded = decoder.decode(quest).orElseThrow();

    assertThat(decoded.questId()).isEqualTo("abc");
    assertThat(decoded.message()).isEqualTo("Help a knight");
    assertThat(decoded.probability()).isEqualTo(Probability.SURE_THING);
  }

  @Test
  void schemeZeroMeansNotEncrypted() {
    Quest decoded =
        decoder
            .decode(
                aQuestDto()
                    .questId("abc")
                    .message("Plain text")
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(0)
                    .probability("Gamble")
                    .build())
            .orElseThrow();

    assertThat(decoded.questId()).isEqualTo("abc");
    assertThat(decoded.message()).isEqualTo("Plain text");
    assertThat(decoded.encrypted()).isFalse();
  }

  @Test
  void encodedQuestWithMissingFieldsIsKept() {
    Quest base64 =
        decoder
            .decode(
                aQuestDto()
                    .questId(b64("abc"))
                    .message(null)
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(1)
                    .probability(null)
                    .build())
            .orElseThrow();
    Quest rot13 =
        decoder
            .decode(
                aQuestDto()
                    .questId("nop")
                    .message(null)
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(2)
                    .probability(null)
                    .build())
            .orElseThrow();

    assertThat(base64.message()).isNull();
    assertThat(base64.probability()).isEqualTo(Probability.UNKNOWN);
    assertThat(rot13.message()).isNull();
    assertThat(rot13.probability()).isEqualTo(Probability.UNKNOWN);
  }

  @Test
  void unknownSchemeIsDropped() {
    assertThat(
            decoder.decode(
                aQuestDto()
                    .questId("x")
                    .message("y")
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(3)
                    .probability("Gamble")
                    .build()))
        .isEmpty();
  }

  @Test
  void malformedBase64IsDropped() {
    assertThat(
            decoder.decode(
                aQuestDto()
                    .questId("%%%not base64%%%")
                    .message("y")
                    .reward(1)
                    .expiresIn(1)
                    .encrypted(1)
                    .probability("Gamble")
                    .build()))
        .isEmpty();
  }

  @Test
  void questWithoutIdIsDropped() {
    assertThat(
            decoder.decode(
                aQuestDto()
                    .questId(null)
                    .message("y")
                    .reward(1)
                    .expiresIn(1)
                    .probability("Gamble")
                    .build()))
        .isEmpty();
    assertThat(
            decoder.decode(
                aQuestDto()
                    .questId(" ")
                    .message("y")
                    .reward(1)
                    .expiresIn(1)
                    .probability("Gamble")
                    .build()))
        .isEmpty();
    assertThat(decoder.decode(null)).isEmpty();
  }

  @Test
  void questWhoseIdDecodesToNothingIsDropped() {
    assertThat(
            decoder.decode(
                aQuestDto()
                    .questId(b64("   "))
                    .message(b64("y"))
                    .encrypted(1)
                    .probability(null)
                    .build()))
        .isEmpty();
  }

  @Test
  void urlSafeBase64IsDropped() {
    assertThat(
            decoder.decode(
                aQuestDto().questId("a-_b").message("eQ==").encrypted(1).probability(null).build()))
        .isEmpty();
  }

  @Test
  void anyTextSurvivesAnEncodingRoundTrip() {
    for (long seed = 0; seed < RANDOM_TEXTS; seed++) {
      String text = randomText(new Random(seed));
      QuestDTO base64 =
          aQuestDto().questId(b64("id")).message(b64(text)).encrypted(1).probability(null).build();
      QuestDTO rot13 =
          aQuestDto()
              .questId("id")
              .message(DecodeUtils.rot13(text))
              .encrypted(2)
              .probability(null)
              .build();

      assertThat(decoder.decode(base64).orElseThrow().message()).isEqualTo(text);
      assertThat(decoder.decode(rot13).orElseThrow().message()).isEqualTo(text);
    }
  }

  private static String randomText(Random random) {
    String alphabet = "abcXYZ 019.,'-éßçÂ";
    StringBuilder text = new StringBuilder();
    int length = random.nextInt(MAX_TEXT);
    for (int i = 0; i < length; i++) {
      text.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return text.toString();
  }

  @Test
  void decodeAllKeepsOrderAndSkipsBadQuests() {
    List<QuestDTO> board =
        Arrays.asList(
            aQuestDto().message("first").reward(1).expiresIn(1).probability("Gamble").build(),
            aQuestDto()
                .questId("b")
                .message("bad")
                .reward(1)
                .expiresIn(1)
                .encrypted(9)
                .probability("Gamble")
                .build(),
            aQuestDto()
                .questId(b64("c"))
                .message(b64("third"))
                .reward(1)
                .expiresIn(1)
                .encrypted(1)
                .probability(b64("Risky"))
                .build());

    List<Quest> decoded = decoder.decodeAll(board);

    assertThat(decoded).extracting(Quest::questId).containsExactly("a", "c");
  }

  @Test
  void decodeAllToleratesNull() {
    assertThat(decoder.decodeAll(null)).isEmpty();
  }
}
