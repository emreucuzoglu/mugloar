package com.mugloar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemTest {

  @ParameterizedTest
  @CsvSource({
    "hpot, 50, 1, 0",
    "cs, 100, 0, 1",
    "gas, 100, 0, 1",
    "wax, 100, 0, 1",
    "tricks, 100, 0, 1",
    "wingpot, 100, 0, 1",
    "ch, 300, 0, 2",
    "rf, 300, 0, 2",
    "iron, 300, 0, 2",
    "mtrix, 300, 0, 2",
    "wingpotmax, 300, 0, 2"
  })
  void catalogueMatchesTheLiveShop(String id, int cost, int lives, int levels) {
    Item item = Item.fromId(id).orElseThrow();

    assertThat(item.cost()).isEqualTo(cost);
    assertThat(item.livesGained()).isEqualTo(lives);
    assertThat(item.levelsGained()).isEqualTo(levels);
  }

  @Test
  void unseenItemIsNotInTheCatalogue() {
    assertThat(Item.fromId("nosuchitem")).isEmpty();
    assertThat(Item.fromId(null)).isEmpty();
  }
}
