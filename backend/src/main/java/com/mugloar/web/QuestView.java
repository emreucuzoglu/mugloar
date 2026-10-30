package com.mugloar.web;

import com.mugloar.domain.QuestEvaluation;
import com.mugloar.util.MathUtils;
import org.apache.commons.lang3.StringUtils;

record QuestView(
    String questId,
    String message,
    int reward,
    int expiresIn,
    String probability,
    boolean encrypted,
    String questType,
    boolean trap,
    int rank,
    boolean recommended,
    double successChance,
    double expectedValue,
    boolean acceptable) {

  static QuestView of(QuestEvaluation e, int rank, boolean recommended) {
    return new QuestView(
        e.quest().questId(),
        StringUtils.defaultString(e.quest().message()),
        e.quest().reward(),
        e.quest().expiresIn(),
        e.quest().probability().label(),
        e.quest().encrypted(),
        e.quest().questType().label(),
        e.quest().questType().isTrap(),
        rank,
        recommended,
        MathUtils.roundToCents(e.successChance()),
        MathUtils.roundToCents(e.expectedValue()),
        e.acceptable());
  }
}
