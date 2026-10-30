package com.mugloar.solver.strategy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestEvaluation;
import com.mugloar.domain.QuestType;
import java.util.Comparator;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RiskAwareQuestSelector implements QuestSelector {

  private static final double LAST_LIFE_MULTIPLIER = 3.0;
  private static final int LAST_LIFE = 1;
  private static final int WOUNDED_LIVES = 2;
  private static final int HEALTHY_LIVES = 3;

  @NonNull private final SelectorSettings settings;

  @Override
  public List<QuestEvaluation> rank(List<Quest> quests, GameState state) {
    return quests.stream()
        .map(quest -> evaluate(quest, state))
        .sorted(
            Comparator.comparing(QuestEvaluation::acceptable)
                .reversed()
                .thenComparing(
                    Comparator.comparingDouble(QuestEvaluation::expectedValue).reversed())
                .thenComparingInt(e -> e.quest().expiresIn()))
        .toList();
  }

  QuestEvaluation evaluate(Quest quest, GameState state) {
    double chance = quest.successChance();
    double lifeCost =
        settings.lifeCost() * (state.lives() <= LAST_LIFE ? LAST_LIFE_MULTIPLIER : 1.0);
    double expectedValue = chance * quest.reward() - (1 - chance) * lifeCost;
    boolean acceptable =
        !quest.questType().isTrap()
            && chance >= minChance(state.lives())
            && !bringsTraps(quest, state);
    return new QuestEvaluation(quest, chance, expectedValue, acceptable);
  }

  private static boolean bringsTraps(Quest quest, GameState state) {
    return state.stateReputation() > QuestType.TRAP_STATE_REPUTATION
        && state.stateReputation() + quest.questType().reputationChange().state()
            <= QuestType.TRAP_STATE_REPUTATION;
  }

  double minChance(int lives) {
    if (lives >= HEALTHY_LIVES) {
      return settings.minChanceHealthy();
    }
    return lives == WOUNDED_LIVES ? settings.minChanceWounded() : settings.minChanceCritical();
  }
}
