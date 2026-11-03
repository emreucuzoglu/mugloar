package com.mugloar;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.config.MugloarProperties;
import com.mugloar.solver.AutoPlayer;
import com.mugloar.solver.strategy.SelectorSettings;
import com.mugloar.solver.strategy.ShoppingSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MugloarApplicationTests {

  @Autowired private AutoPlayer autoPlayer;

  @Autowired private MugloarProperties props;

  @Test
  void contextLoadsWithConfiguredStrategy() {
    assertThat(autoPlayer).isNotNull();
    assertThat(props.baseUrl().toString()).isEqualTo("https://dragonsofmugloar.com/api/v2");
    assertThat(props.solver().targetScore()).isZero();
    assertThat(props.solver().maxMoves()).isZero();
    assertThat(props.corsOrigins()).contains("http://localhost:5173", "http://127.0.0.1:5173");
  }

  @Test
  void productionSettingsAreTheDefaultsTheTestsUse() {
    assertThat(props.selector()).isEqualTo(SelectorSettings.defaults());
    assertThat(props.shopping()).isEqualTo(ShoppingSettings.defaults());
  }
}
