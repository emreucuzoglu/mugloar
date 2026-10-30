package com.mugloar.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestPacerTest {

  private final List<Duration> sleeps = new ArrayList<>();

  @Test
  void firstRequestGoesStraightAway() {
    new RequestPacer(Duration.ofSeconds(10), sleeps::add).pace();

    assertThat(sleeps).isEmpty();
  }

  @Test
  void nextRequestWaitsForTheInterval() {
    RequestPacer pacer = new RequestPacer(Duration.ofSeconds(10), sleeps::add);

    pacer.pace();
    pacer.pace();

    assertThat(sleeps)
        .singleElement()
        .satisfies(wait -> {
          assertThat(wait).isPositive();
          assertThat(wait).isLessThanOrEqualTo(Duration.ofSeconds(10));
        });
  }

  @Test
  void eachWaitingRequestTakesItsOwnSlot() {
    RequestPacer pacer = new RequestPacer(Duration.ofSeconds(10), sleeps::add);

    pacer.pace();
    pacer.pace();
    pacer.pace();

    assertThat(sleeps.get(1)).isGreaterThan(Duration.ofSeconds(10));
  }

  @Test
  void zeroIntervalNeverWaits() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    pacer.pace();
    pacer.pace();

    assertThat(sleeps).isEmpty();
  }

  @Test
  void rateLimitWaitsDoubleWithoutARetryAfter() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(0, null)).isTrue();
    assertThat(pacer.waitForRateLimit(1, null)).isTrue();
    assertThat(pacer.waitForRateLimit(2, "soon")).isTrue();

    assertThat(sleeps)
        .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  @Test
  void rateLimitFollowsTheServersRetryAfter() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(0, "3")).isTrue();

    assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
  }

  @Test
  void givesUpAfterTheLastWait() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(3, null)).isFalse();
    assertThat(sleeps).isEmpty();
  }

  @Test
  void givesUpWhenTheServerAsksForTooLong() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(0, "600")).isFalse();
    assertThat(sleeps).isEmpty();
  }

  @Test
  void aZeroRetryAfterTriesAgainStraightAway() {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(0, "0")).isTrue();

    assertThat(sleeps).containsExactly(Duration.ZERO);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-5", "Wed, 21 Oct 2026 07:28:00 GMT"})
  void anUnusableRetryAfterFallsBackToOurOwnWait(String retryAfter) {
    RequestPacer pacer = new RequestPacer(Duration.ZERO, sleeps::add);

    assertThat(pacer.waitForRateLimit(0, retryAfter)).isTrue();

    assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
  }

  @Test
  void threadsSharingAPacerEachGetTheirOwnSlot() throws Exception {
    int threads = 8;
    Duration interval = Duration.ofHours(1);
    List<Duration> waits = Collections.synchronizedList(new ArrayList<>());
    RequestPacer pacer = new RequestPacer(interval, waits::add);
    CountDownLatch ready = new CountDownLatch(threads);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        pool.submit(() -> {
          ready.countDown();
          ready.await();
          pacer.pace();
          return null;
        });
      }
    }

    List<Long> hours = waits.stream().map(w -> Math.round(w.toMinutes() / 60.0)).sorted().toList();
    assertThat(hours).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
  }

  @Test
  void anInterruptedPaceStillKeepsTheInterruptFlag() {
    RequestPacer pacer =
        new RequestPacer(
            Duration.ofHours(1),
            wait -> {
              throw new InterruptedException();
            });
    try {
      pacer.pace();
      pacer.pace();

      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void interruptedWaitGivesUpAndKeepsTheInterruptFlag() {
    RequestPacer pacer =
        new RequestPacer(
            Duration.ZERO,
            wait -> {
              throw new InterruptedException();
            });
    try {
      assertThat(pacer.waitForRateLimit(0, null)).isFalse();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
