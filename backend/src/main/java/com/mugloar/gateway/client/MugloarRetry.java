package com.mugloar.gateway.client;

import com.mugloar.gateway.MugloarApiException;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

@Slf4j
@UtilityClass
public class MugloarRetry {

  private static final Duration FIRST_DELAY = Duration.ofMillis(200);
  private static final double DELAY_MULTIPLIER = 2;

  /** Retries only failures that may pass: no response, or a 5xx. */
  public static RetryTemplate forReads(int attempts) {
    RetryTemplate template =
        new RetryTemplate(
            RetryPolicy.builder()
                .maxRetries(attempts - 1L)
                .delay(FIRST_DELAY)
                .multiplier(DELAY_MULTIPLIER)
                .predicate(e -> e instanceof MugloarApiException api && api.isTransient())
                .build());
    template.setRetryListener(
        new RetryListener() {
          @Override
          public void beforeRetry(RetryPolicy policy, Retryable<?> call, RetryState state) {
            log.warn(
                "Retry {} of a game server read, after: {}",
                state.getRetryCount(),
                state.getLastException().getMessage());
          }
        });
    return template;
  }
}
