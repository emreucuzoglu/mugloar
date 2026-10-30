package com.mugloar.solver.strategy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestEvaluation;
import java.util.List;
import java.util.Optional;

public interface QuestSelector {

  List<QuestEvaluation> rank(List<Quest> quests, GameState state);

  default Optional<QuestEvaluation> choose(List<Quest> quests, GameState state) {
    return best(rank(quests, state));
  }

  default Optional<QuestEvaluation> best(List<QuestEvaluation> ranked) {
    return ranked.stream()
        .filter(QuestEvaluation::acceptable)
        .findFirst()
        .or(() -> ranked.stream().findFirst());
  }
}
