package com.mugloar.solver;

import com.mugloar.domain.GameState;

public record GameResult(GameState finalState, int moves, StopReason reason) {

  public enum StopReason {
    GAME_OVER,
    TARGET_REACHED,
    MOVE_LIMIT,
    STALLED,
    RATE_LIMITED
  }

  public long score() {
    return finalState.score();
  }
}
