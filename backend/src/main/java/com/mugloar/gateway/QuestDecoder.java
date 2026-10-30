package com.mugloar.gateway;

import com.mugloar.domain.Quest;
import com.mugloar.gateway.dto.QuestDTO;
import com.mugloar.util.DecodeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
final class QuestDecoder {

  private static final int PLAIN = 0;
  private static final int BASE64 = 1;
  private static final int ROT13 = 2;

  private final MugloarMapper mapper;

  Optional<Quest> decode(QuestDTO quest) {
    if (quest == null || StringUtils.isBlank(quest.questId())) {
      return Optional.empty();
    }
    Integer scheme = quest.encrypted();
    if (scheme == null || scheme == PLAIN) {
      return Optional.of(
          mapper.toQuest(quest, quest.questId(), quest.message(), quest.probability(), false));
    }
    try {
      Optional<Quest> decoded =
          switch (scheme) {
            case BASE64 ->
                Optional.of(
                    mapper.toQuest(
                        quest,
                        DecodeUtils.base64(quest.questId()),
                        DecodeUtils.base64(quest.message()),
                        DecodeUtils.base64(quest.probability()),
                        true));
            case ROT13 ->
                Optional.of(
                    mapper.toQuest(
                        quest,
                        DecodeUtils.rot13(quest.questId()),
                        DecodeUtils.rot13(quest.message()),
                        DecodeUtils.rot13(quest.probability()),
                        true));
            default -> Optional.empty();
          };
      return decoded.filter(q -> StringUtils.isNotBlank(q.questId()));
    } catch (IllegalArgumentException e) {
      return Optional.empty(); // malformed Base64
    }
  }

  List<Quest> decodeAll(List<QuestDTO> quests) {
    List<Quest> result = new ArrayList<>();
    if (quests != null) {
      for (QuestDTO quest : quests) {
        decode(quest).ifPresent(result::add);
      }
    }
    return result;
  }
}
