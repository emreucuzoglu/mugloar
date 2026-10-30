package com.mugloar.gateway.dto;

public record GameStartDTO(
    String gameId, int lives, int gold, int level, int score, int highScore, int turn) {}
