package com.mugloar.gateway.dto;

public record GameStartDTO(
    String gameId, int lives, long gold, int level, long score, long highScore, int turn) {}
