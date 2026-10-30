package com.mugloar.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.config.MugloarProperties;
import com.mugloar.domain.GameState;
import com.mugloar.domain.Item;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestType;
import com.mugloar.domain.ShopItem;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that the real game still looks the way the code expects. Only {@code ./gradlew
 * liveContract} runs this, never the normal tests, because it calls the public server: it starts
 * one game and reads its board and shop, which costs no turns.
 */
@Tag("live")
@SpringBootTest
class LiveContractTest {

  @Autowired private MugloarGateway gateway;
  @Autowired private MugloarProperties props;

  @Test
  void theLiveGameMatchesWhatTheCodeExpects() {
    assertThat(props.minRequestInterval()).isPositive();

    GameState start = gateway.startGame();
    assertThat(start.gameId()).isNotBlank();
    assertThat(start.lives()).isPositive();

    List<Quest> board = gateway.getBoard(start.gameId());
    assertThat(board).isNotEmpty();
    assertThat(board)
        .allSatisfy(quest -> {
          assertThat(quest.questType()).as(quest.message()).isNotEqualTo(QuestType.UNKNOWN);
          assertThat(quest.probability()).as(quest.message()).isNotEqualTo(Probability.UNKNOWN);
        });

    assertThat(gateway.getShop(start.gameId()))
        .extracting(ShopItem::id)
        .containsExactlyInAnyOrderElementsOf(Arrays.stream(Item.values()).map(Item::id).toList());
  }
}
