package com.mugloar.solver.strategy;

import org.apache.commons.lang3.Validate;

public record ShoppingSettings(
    int healBelowLives, int goldReserve, int smallUpgradesUntilTurn, int upgradesUntilTurn) {

  private static final int DEFAULT_HEAL_BELOW_LIVES = 3;
  private static final int DEFAULT_GOLD_RESERVE = 50;
  private static final int DEFAULT_SMALL_UPGRADES_UNTIL_TURN = 0;
  private static final int DEFAULT_UPGRADES_UNTIL_TURN = 70;

  public ShoppingSettings {
    Validate.isTrue(healBelowLives >= 0, "healBelowLives must not be negative");
    Validate.isTrue(goldReserve >= 0, "goldReserve must not be negative");
    Validate.isTrue(smallUpgradesUntilTurn >= 0, "smallUpgradesUntilTurn must not be negative");
    Validate.isTrue(upgradesUntilTurn >= 0, "upgradesUntilTurn must not be negative");
  }

  public static ShoppingSettings defaults() {
    return new ShoppingSettings(
        DEFAULT_HEAL_BELOW_LIVES,
        DEFAULT_GOLD_RESERVE,
        DEFAULT_SMALL_UPGRADES_UNTIL_TURN,
        DEFAULT_UPGRADES_UNTIL_TURN);
  }
}
