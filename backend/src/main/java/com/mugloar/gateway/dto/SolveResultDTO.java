package com.mugloar.gateway.dto;

public record SolveResultDTO(
    boolean success, int lives, long gold, long score, long highScore, int turn, String message) {}
