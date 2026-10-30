package com.mugloar.domain;

public record Quest(
    String questId,
    String rawQuestId,
    String message,
    int reward,
    int expiresIn,
    Probability probability,
    boolean encrypted) {

  public QuestType questType() {
    return QuestType.classify(message);
  }

  public double successChance() {
    return questType().isTrap() ? 0 : probability.chance();
  }
}
