package com.mugloar.domain;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Getter
@RequiredArgsConstructor
public enum Probability {
  SURE_THING("Sure thing", 1.00),
  PIECE_OF_CAKE("Piece of cake", 0.96),
  WALK_IN_THE_PARK("Walk in the park", 0.84),
  QUITE_LIKELY("Quite likely", 0.79),
  HMMM("Hmmm....", 0.68),
  GAMBLE("Gamble", 0.49),
  RISKY("Risky", 0.40),
  RATHER_DETRIMENTAL("Rather detrimental", 0.33),
  PLAYING_WITH_FIRE("Playing with fire", 0.20),
  SUICIDE_MISSION("Suicide mission", 0.05),
  IMPOSSIBLE("Impossible", 0.00),
  UNKNOWN("Unknown", 0.25);

  private final String label;
  private final double chance;

  /** Ignores case, surrounding whitespace and trailing dots, because the game writes "Hmmm....". */
  public static Probability fromLabel(String label) {
    if (label == null) {
      return UNKNOWN;
    }
    String key = normalize(label);
    return Arrays.stream(values())
        .filter(p -> p != UNKNOWN && normalize(p.label).equals(key))
        .findFirst()
        .orElse(UNKNOWN);
  }

  private static String normalize(String s) {
    return StringUtils.stripEnd(s.strip(), ".").toLowerCase(Locale.ROOT);
  }
}
