package com.mugloar.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.solver.strategy.SelectorSettings;
import com.mugloar.solver.strategy.ShoppingSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class MugloarPropertiesTest {

  @EnableConfigurationProperties(MugloarProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(Config.class)
          .withPropertyValues(
              "mugloar.base-url=http://localhost",
              "mugloar.connect-timeout=1s",
              "mugloar.read-timeout=1s",
              "mugloar.read-attempts=1",
              "mugloar.min-request-interval=0s",
              "mugloar.max-sessions=1",
              "mugloar.solver.games=1",
              "mugloar.solver.target-score=0",
              "mugloar.solver.max-moves=1");

  @Test
  void optionalSectionsFallBackToDefaults() {
    runner.run(context -> {
      MugloarProperties props = context.getBean(MugloarProperties.class);

      assertThat(props.corsOrigins()).isEmpty();
      assertThat(props.selector()).isEqualTo(SelectorSettings.defaults());
      assertThat(props.shopping()).isEqualTo(ShoppingSettings.defaults());
    });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "mugloar.read-attempts=0",
        "mugloar.max-sessions=0",
        "mugloar.solver.games=0",
        "mugloar.solver.target-score=-1",
        "mugloar.solver.max-moves=0",
        "mugloar.selector.min-chance-healthy=1.5",
        "mugloar.shopping.gold-reserve=-1",
      })
  void invalidValueStopsStartup(String invalid) {
    runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
  }
}
