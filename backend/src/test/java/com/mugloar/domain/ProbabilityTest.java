package com.mugloar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProbabilityTest {

  private static final double ODDS_TOLERANCE = 0.021;

  @ParameterizedTest
  @CsvSource({
    "Sure thing, SURE_THING",
    "Piece of cake, PIECE_OF_CAKE",
    "Walk in the park, WALK_IN_THE_PARK",
    "Quite likely, QUITE_LIKELY",
    "Hmmm...., HMMM",
    "Hmmm, HMMM",
    "Gamble, GAMBLE",
    "Risky, RISKY",
    "Rather detrimental, RATHER_DETRIMENTAL",
    "Playing with fire, PLAYING_WITH_FIRE",
    "Suicide mission, SUICIDE_MISSION",
    "Impossible, IMPOSSIBLE",
    "'  piece OF cake  ', PIECE_OF_CAKE",
    "Something new, UNKNOWN"
  })
  void parsesLabels(String label, Probability expected) {
    assertThat(Probability.fromLabel(label)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "SURE_THING, 1.00",
    "PIECE_OF_CAKE, 0.96",
    "WALK_IN_THE_PARK, 0.84",
    "QUITE_LIKELY, 0.78",
    "HMMM, 0.67",
    "GAMBLE, 0.51",
    "RISKY, 0.42",
    "RATHER_DETRIMENTAL, 0.35",
    "PLAYING_WITH_FIRE, 0.22",
    "SUICIDE_MISSION, 0.06",
    "IMPOSSIBLE, 0.00"
  })
  void catalogueStaysCloseToTheMeasuredOdds(Probability label, double measured) {
    assertThat(label.chance()).isCloseTo(measured, within(ODDS_TOLERANCE));
  }

  @Test
  void nullIsUnknown() {
    assertThat(Probability.fromLabel(null)).isEqualTo(Probability.UNKNOWN);
  }

  @Test
  void knownLabelsGetRiskierInOrder() {
    double[] chances =
        Arrays.stream(Probability.values())
            .filter(p -> p != Probability.UNKNOWN)
            .mapToDouble(Probability::chance)
            .toArray();
    for (int i = 1; i < chances.length; i++) {
      assertThat(chances[i]).isLessThan(chances[i - 1]);
    }
  }
}
