package com.mugloar.web;

import com.mugloar.domain.GameState;

/** What the browser sees of a game: the solver's own memory of it stays on the server. */
record GameStateView(
    String gameId, int lives, long gold, int level, long score, long highScore, int turn) {

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
