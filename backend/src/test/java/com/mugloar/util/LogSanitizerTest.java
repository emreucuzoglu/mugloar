package com.mugloar.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.gateway.MugloarApiException;
import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void replacesLineBreaksAndEscapeCodes() {
    assertThat(LogSanitizer.clean("a\r\nb c\u001B[31md")).isEqualTo("a  b c [31md");
  }

  @Test
  void keepsOrdinaryTextAndNull() {
    assertThat(LogSanitizer.clean("Rescue the örn — 100 gold"))
        .isEqualTo("Rescue the örn — 100 gold");
    assertThat(LogSanitizer.clean(null)).isNull();
  }

  @Test
  void apiExceptionMessagesAreSafeToLog() {
    assertThat(new MugloarApiException(500, "bad\nFAKE LOG LINE").getMessage())
        .isEqualTo("bad FAKE LOG LINE");
    assertThat(new MugloarApiException(500, "bad\nFAKE", new RuntimeException()).getMessage())
        .isEqualTo("bad FAKE");
  }
}
