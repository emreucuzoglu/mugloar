package com.mugloar.gateway;

import org.springframework.http.HttpStatus;

public class GameOverException extends MugloarApiException {

  public static final int GONE = HttpStatus.GONE.value();

  public GameOverException(String gameId) {
    super(GONE, "Game " + gameId + " is over");
  }
}
