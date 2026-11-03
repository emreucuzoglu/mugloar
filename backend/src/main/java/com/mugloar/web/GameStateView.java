package com.mugloar.web;

import com.mugloar.domain.GameState;

/** What the browser sees of a game: the solver's own memory of it stays on the server. */
record GameStateView(
    String gameId, int lives, int gold, int level, int score, int highScore, int turn) {

  static GameStateView of(GameState state) {
    return new GameStateView(
        state.gameId(),
        state.lives(),
        state.gold(),
        state.level(),
        state.score(),
        state.highScore(),
        state.turn());
  }
}
