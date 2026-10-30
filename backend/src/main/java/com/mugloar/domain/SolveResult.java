package com.mugloar.domain;

public record SolveResult(
    boolean success, int lives, int gold, int score, int highScore, int turn, String message) {}
