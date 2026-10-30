package com.mugloar.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MugloarApiExceptionTest {

  @ParameterizedTest
  @CsvSource({
    "0,   false, true,  false",
    "200, false, false, false",
    "399, false, false, false",
    "400, true,  false, false",
    "404, true,  false, false",
    "410, false, false, false",
    "429, false, false, true",
    "499, true,  false, false",
    "500, false, true,  false",
    "503, false, true,  false",
  })
  void classifiesStatus(int status, boolean refusal, boolean isTransient, boolean rateLimited) {
    MugloarApiException e = new MugloarApiException(status, "failed");

    assertThat(e.status()).isEqualTo(status);
    assertThat(e.isRefusal()).isEqualTo(refusal);
    assertThat(e.isTransient()).isEqualTo(isTransient);
    assertThat(e.isRateLimited()).isEqualTo(rateLimited);
  }
}
