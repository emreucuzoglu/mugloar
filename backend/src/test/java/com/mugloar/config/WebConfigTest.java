package com.mugloar.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebConfigTest {

  @Test
  void addsNoCorsMappingWithoutOrigins() {
    MugloarProperties props =
        new MugloarProperties(
            URI.create("http://localhost"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            1,
            Duration.ZERO,
            null,
            1,
            null,
            null,
            new MugloarProperties.Solver(1, 0, 1));
    CorsRegistry registry = mock(CorsRegistry.class);

    new WebConfig(props).addCorsMappings(registry);

    verifyNoInteractions(registry);
  }
}
