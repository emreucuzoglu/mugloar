package com.mugloar.game;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Quest;
import com.mugloar.domain.ShopItem;
import com.mugloar.solver.TurnEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class GameSession {

  static final int HISTORY_SIZE = 110;

  @NonNull
  @Getter(AccessLevel.PACKAGE)
  private GameState state;

  @Getter(AccessLevel.PACKAGE)
  private int skipsInARow;

  private List<ShopItem> shop;
  private final Map<String, Quest> board = new HashMap<>();
  private final Deque<TurnEvent> history = new ArrayDeque<>();

  Optional<List<ShopItem>> shop() {
    return Optional.ofNullable(shop);
  }

  void shop(List<ShopItem> items) {
    this.shop = List.copyOf(items);
  }

  void rememberBoard(List<Quest> quests) {
    board.clear();
    quests.forEach(quest -> board.put(quest.questId(), quest));
  }

  Optional<Quest> boardQuest(String questId) {
    return Optional.ofNullable(board.get(questId));
  }

  void record(TurnEvent event) {
    state = event.state();
    if (event.type() == TurnEvent.Type.SKIP) {
      skipsInARow++;
    } else {
      skipsInARow = 0;
      board.clear();
    }
    history.addFirst(event);
    while (history.size() > HISTORY_SIZE) {
      history.removeLast();
    }
  }

  void clearSkips() {
    skipsInARow = 0;
  }

  void end() {
    if (!state.isOver()) {
      record(
          new TurnEvent(
              TurnEvent.Type.GAME_OVER,
              false,
              "Game over with " + state.score() + " points",
              state.ended()));
    }
  }

  /** Most recent first. */
  List<TurnEvent> history() {
    return List.copyOf(history);
  }
}
