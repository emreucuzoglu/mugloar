package com.mugloar.domain;

import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
public enum QuestType {
  FIX("Fix", "^Help .* to fix ", false, 0, 5, 1, 5, 10, new Reputation(0.1, 0, 0)),
  CLEAN("Clean", "^Help .* to clean ", false, 0, 5, 1, 6, 10, new Reputation(0.1, 0, 0)),
  NEGOTIATE("Negotiate", "^Help .* to reach ", false, 0, 10, 2, 10, 20, new Reputation(0.2, 0, 0)),
  TRANSPORT(
      "Transport", "^Help .* to transport ", false, 0, 10, 5, 16, 28, new Reputation(0.3, 0, 0)),
  SELL("Sell", "^Help .* to sell ", false, 0, 15, 7, 21, 36, new Reputation(0.1, 0, 0)),
  WRITE("Write", "^Help .* to write ", false, 0, 20, 14, 34, 52, new Reputation(1, 0, 0)),
  ADVERTISE(
      "Advertise", "^Create an advertisement ", false, 0, 20, 12, 31, 51, new Reputation(1, 0, 0)),
  ESCORT("Escort", "^Escort ", false, 0, 27, 39, 68, 95, new Reputation(0.1, 0, 0)),
  RESCUE("Rescue", "^Rescue ", false, 6, 31, 12, 35, 65, new Reputation(0.1, 0, 0)),
  INVESTIGATE(
      "Investigate", "^Investigate ", false, 16, 36, 88, 137, 179, new Reputation(-0.1, 1, 0)),
  INFILTRATE("Infiltrate", "^Infiltrate ", false, 16, 35, 89, 137, 179, new Reputation(0, 2, -1)),
  KILL("Kill", "^Kill ", false, 26, 30, 79, 110, 135, new Reputation(0, -1, 2)),
  DEFEND("Defend", "^Help defending ", false, 21, Integer.MAX_VALUE, 25, 67, 145, none()),
  STEAL_DIAMOND("Trap", "^Steal super awesome diamond ", true, 13, 96, 121, 164, 207, none()),
  /** Must stay after {@link #STEAL_DIAMOND}, whose text also starts with "Steal". */
  STEAL_DELIVERY("Steal", "^Steal ", false, 0, 26, 34, 55, 88, new Reputation(1, -2, 0)),
  UNKNOWN("Unknown", "$^", false, 0, Integer.MAX_VALUE, 0, 0, 0, none());

  public static final double TRAP_STATE_REPUTATION = -10;

  private final String label;

  @Getter(AccessLevel.NONE)
  private final Pattern pattern;

  @Getter(AccessLevel.NONE)
  private final boolean trap;

  private final int firstTurn;
  private final int lastTurn;
  private final int rewardMin;
  private final int rewardMedian;
  private final int rewardMax;
  private final Reputation reputationChange;

  QuestType(
      String label,
      String pattern,
      boolean trap,
      int firstTurn,
      int lastTurn,
      int rewardMin,
      int rewardMedian,
      int rewardMax,
      Reputation reputationChange) {
    this.label = label;
    this.pattern = Pattern.compile(pattern);
    this.trap = trap;
    this.firstTurn = firstTurn;
    this.lastTurn = lastTurn;
    this.rewardMin = rewardMin;
    this.rewardMedian = rewardMedian;
    this.rewardMax = rewardMax;
    this.reputationChange = reputationChange;
  }

  static QuestType classify(String message) {
    if (message == null) {
      return UNKNOWN;
    }
    return Arrays.stream(values())
        .filter(type -> type.pattern.matcher(message).find())
        .findFirst()
        .orElse(UNKNOWN);
  }

  public boolean isTrap() {
    return trap;
  }

  private static Reputation none() {
    return new Reputation(0, 0, 0);
  }
}
