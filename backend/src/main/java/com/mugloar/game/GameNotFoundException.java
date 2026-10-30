package com.mugloar.game;

public class GameNotFoundException extends RuntimeException {

  public GameNotFoundException(String gameId) {
    super("No game with id " + gameId);
  }
}
