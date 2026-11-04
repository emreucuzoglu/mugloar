package com.mugloar.domain;

public record SolveResult(
    boolean success, int lives, long gold, long score, long highScore, int turn, String message) {}
