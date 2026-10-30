package com.mugloar.domain;

import java.util.List;
import java.util.Optional;

public record QuestBoard(List<QuestEvaluation> ranked, Optional<Quest> recommended) {

  public static final QuestBoard EMPTY = new QuestBoard(List.of(), Optional.empty());

  public boolean isRecommended(Quest quest) {
    return recommended.filter(quest::equals).isPresent();
  }
}
