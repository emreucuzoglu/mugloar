package com.mugloar.solver;

import com.mugloar.domain.GameState;

public record TurnEvent(Type type, boolean success, String message, GameState state) {

  public enum Type {
    SOLVE,
    PURCHASE,
    INVESTIGATE,
    SKIP,
    GAME_OVER
  }
}
