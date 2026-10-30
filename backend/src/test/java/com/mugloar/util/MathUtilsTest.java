package com.mugloar.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MathUtilsTest {

  @Test
  void roundsToTwoDecimals() {
    assertThat(MathUtils.roundToCents(0.846)).isEqualTo(0.85);
    assertThat(MathUtils.roundToCents(-1.234)).isEqualTo(-1.23);
    assertThat(MathUtils.roundToCents(3)).isEqualTo(3.0);
  }
}
