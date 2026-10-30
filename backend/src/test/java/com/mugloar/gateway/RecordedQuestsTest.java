package com.mugloar.gateway;

import static com.mugloar.testsupport.Fixtures.aQuestDto;
import static com.mugloar.testsupport.Fixtures.b64;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestType;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.util.DecodeUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RecordedQuestsTest {

  private static final String SAMPLE = "/recordings/recorded-quests.jsonl";
  private static final String API_ID = "^[A-Za-z0-9_-]{1,64}$";
  private static final int BASE64 = 1;
  private static final int ROT13 = 2;

  private final QuestDecoder decoder = new QuestDecoder(Mappers.getMapper(MugloarMapper.class));

  static Stream<JsonNode> recordedQuests() throws IOException {
    try (InputStream in = RecordedQuestsTest.class.getResourceAsStream(SAMPLE)) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      List<JsonNode> rows = text.lines().map(JsonMapper.shared()::readTree).toList();
      return rows.stream();
    }
  }

  private static QuestDTO asSent(JsonNode row) {
    String id = row.path("id").asString();
    String message = row.path("msg").asString();
    String label = row.path("label").asString();
    QuestDtoValues sent =
        switch (row.path("enc").asInt(0)) {
          case BASE64 -> new QuestDtoValues(b64(id), b64(message), b64(label), BASE64);
          case ROT13 ->
              new QuestDtoValues(
                  DecodeUtils.rot13(id),
                  DecodeUtils.rot13(message),
                  DecodeUtils.rot13(label),
                  ROT13);
          default -> new QuestDtoValues(id, message, label, null);
        };
    return aQuestDto()
        .questId(sent.id())
        .message(sent.message())
        .probability(sent.label())
        .encrypted(sent.encoding())
        .reward(row.path("reward").asInt())
        .expiresIn(row.path("exp").asInt())
        .build();
  }

  private record QuestDtoValues(String id, String message, String label, Integer encoding) {}

  @ParameterizedTest
  @MethodSource("recordedQuests")
  void aRecordedJobIsUnderstood(JsonNode row) {
    Quest quest = decoder.decode(asSent(row)).orElseThrow();

    assertThat(quest.questId()).isEqualTo(row.path("id").asString()).matches(API_ID);
    assertThat(quest.message()).isEqualTo(row.path("msg").asString());
    assertThat(quest.questType()).isNotEqualTo(QuestType.UNKNOWN);
    assertThat(quest.probability()).isNotEqualTo(Probability.UNKNOWN);
  }

  @Test
  void theSampleCoversEveryQuestTypeLabelAndEncoding() throws IOException {
    List<Quest> quests =
        recordedQuests().map(row -> decoder.decode(asSent(row)).orElseThrow()).toList();

    assertThat(quests.stream().map(Quest::questType).distinct())
        .containsExactlyInAnyOrder(
            Stream.of(QuestType.values())
                .filter(type -> type != QuestType.UNKNOWN)
                .toArray(QuestType[]::new));
    assertThat(quests.stream().map(Quest::probability).distinct())
        .containsExactlyInAnyOrder(
            Stream.of(Probability.values())
                .filter(p -> p != Probability.UNKNOWN)
                .toArray(Probability[]::new));
    assertThat(recordedQuests().map(row -> row.path("enc").asInt(0)).distinct())
        .containsExactlyInAnyOrder(0, BASE64, ROT13);
  }
}
