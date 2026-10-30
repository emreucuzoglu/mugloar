package com.mugloar.gateway.client;

import java.time.Duration;
import org.apache.commons.lang3.math.NumberUtils;

public final class RequestPacer {

  private static final int MAX_RATE_LIMIT_WAITS = 3;
  private static final int NO_RETRY_AFTER = -1;
  private static final Duration FIRST_WAIT = Duration.ofSeconds(1);

  private static final Duration LONGEST_WAIT = Duration.ofSeconds(5);

  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private final Duration minInterval;
  private final Sleeper sleeper;
  private long nextSlotNanos = System.nanoTime();

  public RequestPacer(Duration minInterval) {
    this(minInterval, Thread::sleep);
  }

  RequestPacer(Duration minInterval, Sleeper sleeper) {
    this.minInterval = minInterval;
    this.sleeper = sleeper;
  }

  void pace() {
    Duration wait = reserveSlot();
    if (wait.isPositive()) {
      sleep(wait);
    }
  }

  private synchronized Duration reserveSlot() {
    long now = System.nanoTime();
    long slot = Math.max(now, nextSlotNanos);
    nextSlotNanos = slot + minInterval.toNanos();
    return Duration.ofNanos(slot - now);
  }

  boolean waitForRateLimit(int waitsSoFar, String retryAfter) {
    if (waitsSoFar >= MAX_RATE_LIMIT_WAITS) {
      return false;
    }
    int seconds = NumberUtils.toInt(retryAfter, NO_RETRY_AFTER);
    Duration wait =
        seconds > NO_RETRY_AFTER
            ? Duration.ofSeconds(seconds)
            : FIRST_WAIT.multipliedBy(1L << waitsSoFar);
    return wait.compareTo(LONGEST_WAIT) <= 0 && sleep(wait);
  }

  private boolean sleep(Duration duration) {
    try {
      sleeper.sleep(duration);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
