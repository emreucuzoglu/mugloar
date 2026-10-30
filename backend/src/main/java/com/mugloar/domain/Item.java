package com.mugloar.domain;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Item {
  HEALING_POTION("hpot", "Healing potion", 50, 1, 0),
  CLAW_SHARPENING("cs", "Claw Sharpening", Constants.COSTS_LEVEL_1, 0, 1),
  GASOLINE("gas", "Gasoline", Constants.COSTS_LEVEL_1, 0, 1),
  COPPER_PLATING("wax", "Copper Plating", Constants.COSTS_LEVEL_1, 0, 1),
  BOOK_OF_TRICKS("tricks", "Book of Tricks", Constants.COSTS_LEVEL_1, 0, 1),
  POTION_OF_STRONGER_WINGS("wingpot", "Potion of Stronger Wings", Constants.COSTS_LEVEL_1, 0, 1),
  CLAW_HONING("ch", "Claw Honing", Constants.COSTS_LEVEL_2, 0, 2),
  ROCKET_FUEL("rf", "Rocket Fuel", Constants.COSTS_LEVEL_2, 0, 2),
  IRON_PLATING("iron", "Iron Plating", Constants.COSTS_LEVEL_2, 0, 2),
  BOOK_OF_MEGATRICKS("mtrix", "Book of Megatricks", Constants.COSTS_LEVEL_2, 0, 2),
  POTION_OF_AWESOME_WINGS("wingpotmax", "Potion of Awesome Wings", Constants.COSTS_LEVEL_2, 0, 2);

  private final String id;
  private final String itemName;
  private final int cost;
  private final int livesGained;
  private final int levelsGained;

  public static Optional<Item> fromId(String id) {
    return Arrays.stream(values()).filter(item -> item.id.equals(id)).findFirst();
  }

  private static class Constants {
    public static final int COSTS_LEVEL_1 = 100;
    public static final int COSTS_LEVEL_2 = 300;
  }
}
