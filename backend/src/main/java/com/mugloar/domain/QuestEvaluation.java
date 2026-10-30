package com.mugloar.domain;

public record QuestEvaluation(
    Quest quest, double successChance, double expectedValue, boolean acceptable) {}
