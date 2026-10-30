package com.mugloar.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MathUtils {

  private static final double CENTS_PER_UNIT = 100;

  public static double roundToCents(double value) {
    return Math.round(value * CENTS_PER_UNIT) / CENTS_PER_UNIT;
  }
}
