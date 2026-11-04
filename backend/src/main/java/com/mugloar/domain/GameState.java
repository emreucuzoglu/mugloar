package com.mugloar.domain;

import java.util.HashMap;
import java.util.Map;
import lombok.With;

@With
public record GameState(
    String gameId,
    int lives,
    long gold,
    int level,
    long score,
    long highScore,
    int turn,
    double stateReputation,
    Map<String, Integer> itemsBought,
    boolean purchaseUnconfirmed) {

  public GameState {
    itemsBought = itemsBought == null ? Map.of() : Map.copyOf(itemsBought);
  }

  public GameState(
      String gameId, int lives, long gold, int level, long score, long highScore, int turn) {
    this(gameId, lives, gold, level, score, highScore, turn, 0);
  }

  public GameState(
      String gameId,
      int lives,
      long gold,
      int level,
      long score,
      long highScore,
      int turn,
      double stateReputation) {
    this(gameId, lives, gold, level, score, highScore, turn, stateReputation, Map.of(), false);
  }

  public GameState after(SolveResult r) {
    return new GameState(
        gameId,
        r.lives(),
        r.gold(),
        level,
        r.score(),
        r.highScore(),
        r.turn(),
        stateReputation,
        itemsBought,
        false);
  }

  public GameState after(PurchaseResult r) {
    return new GameState(
        gameId,
        r.lives(),
        r.gold(),
        r.level(),
        score,
        highScore,
        r.turn(),
        stateReputation,
        itemsBought,
        false);
  }

  public GameState afterBuying(String itemId) {
    Map<String, Integer> bought = new HashMap<>(itemsBought);
    bought.merge(itemId, 1, Integer::sum);
    return withItemsBought(bought);
  }

  public GameState afterPossiblePurchase(String itemId) {
    return afterBuying(itemId).withPurchaseUnconfirmed(true);
  }

  public int timesBought(String itemId) {
    return itemsBought.getOrDefault(itemId, 0);
  }

  public GameState afterTurn() {
    return withTurn(turn + 1);
  }

  public GameState afterWinning(QuestType type) {
    return withStateReputation(stateReputation + type.reputationChange().state());
  }

  public GameState afterPossibleWin(QuestType type) {
    return withStateReputation(stateReputation + Math.min(0, type.reputationChange().state()));
  }

  public GameState ended() {
    return withLives(0);
  }

  public boolean isOver() {
    return lives <= 0;
  }
}
