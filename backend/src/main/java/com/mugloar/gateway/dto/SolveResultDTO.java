package com.mugloar.gateway.dto;

public record SolveResultDTO(
    boolean success, int lives, int gold, int score, int highScore, int turn, String message) {}
