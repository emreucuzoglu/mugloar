package com.mugloar.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DecodeUtilsTest {

  @Test
  void rot13LeavesDigitsAndPunctuationAlone() {
    assertThat(DecodeUtils.rot13("Ab-12 Zz!")).isEqualTo("No-12 Mm!");
    assertThat(DecodeUtils.rot13(DecodeUtils.rot13("Round trip"))).isEqualTo("Round trip");
  }

  @Test
  void rot13WrapsAroundBothEndsOfTheAlphabet() {
    assertThat(DecodeUtils.rot13("azAZmnMN")).isEqualTo("nmNMzaZA");
  }

  @Test
  void base64IgnoresSurroundingWhitespace() {
    assertThat(DecodeUtils.base64(" YWJj\n")).isEqualTo("abc");
  }

  @Test
  void base64RejectsMalformedInput() {
    assertThatThrownBy(() -> DecodeUtils.base64("%%%"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullStaysNull() {
    assertThat(DecodeUtils.base64(null)).isNull();
    assertThat(DecodeUtils.rot13(null)).isNull();
  }
}
