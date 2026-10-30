package com.mugloar.solver.strategy;

import org.apache.commons.lang3.Validate;

public record SelectorSettings(
    double minChanceHealthy, double minChanceWounded, double minChanceCritical, double lifeCost) {

  private static final double DEFAULT_MIN_CHANCE = 0;
  private static final double DEFAULT_LIFE_COST = 150;

  public SelectorSettings {
    Validate.inclusiveBetween(0d, 1d, minChanceHealthy, "minChanceHealthy must be between 0 and 1");
    Validate.inclusiveBetween(0d, 1d, minChanceWounded, "minChanceWounded must be between 0 and 1");
    Validate.inclusiveBetween(
        0d, 1d, minChanceCritical, "minChanceCritical must be between 0 and 1");
    Validate.isTrue(lifeCost >= 0, "lifeCost must not be negative");
  }

  public static SelectorSettings defaults() {
    return new SelectorSettings(
        DEFAULT_MIN_CHANCE, DEFAULT_MIN_CHANCE, DEFAULT_MIN_CHANCE, DEFAULT_LIFE_COST);
  }
}
