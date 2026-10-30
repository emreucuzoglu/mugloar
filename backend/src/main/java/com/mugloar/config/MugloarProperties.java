package com.mugloar.config;

import com.mugloar.solver.strategy.SelectorSettings;
import com.mugloar.solver.strategy.ShoppingSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mugloar")
public record MugloarProperties(
    @NotNull URI baseUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @Min(1) int readAttempts,
    @NotNull Duration minRequestInterval,
    List<String> corsOrigins,
    @Min(1) int maxSessions,
    SelectorSettings selector,
    ShoppingSettings shopping,
    @Valid @NotNull Solver solver) {

  public MugloarProperties {
    corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    selector = selector == null ? SelectorSettings.defaults() : selector;
    shopping = shopping == null ? ShoppingSettings.defaults() : shopping;
  }

  /**
   * @param targetScore stop a game at this score; 0 plays until the dragon dies
   */
  public record Solver(@Min(1) int games, @Min(0) int targetScore, @Min(1) int maxMoves) {}
}
