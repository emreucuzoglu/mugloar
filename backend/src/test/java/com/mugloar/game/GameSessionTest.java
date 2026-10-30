package com.mugloar.game;

import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.QUEST;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aTurnEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.ShopItem;
import com.mugloar.solver.TurnEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionTest {

  private static final GameState START = aGameState().score(40).highScore(40).build();
  private final GameSession session = new GameSession(START);

  private static TurnEvent event(TurnEvent.Type type, int turn) {
    return aTurnEvent()
        .type(type)
        .message("Turn " + turn)
        .state(aGameState().score(40).highScore(40).turn(turn).build())
        .build();
  }

  @Test
  void moveUpdatesStateAndForgetsTheBoard() {
    session.rememberBoard(List.of(QUEST));

    session.record(event(TurnEvent.Type.SOLVE, 1));

    assertThat(session.state().turn()).isEqualTo(1);
    assertThat(session.boardQuest("a")).isEmpty();
  }

  @Test
  void skipKeepsTheBoard() {
    session.rememberBoard(List.of(QUEST));

    session.record(event(TurnEvent.Type.SKIP, 0));

    assertThat(session.boardQuest("a")).contains(QUEST);
  }

  @Test
  void rememberingABoardReplacesTheOldOne() {
    Quest other = aQuest().questId("b").message("Other").probability(Probability.GAMBLE).build();
    session.rememberBoard(List.of(QUEST));

    session.rememberBoard(List.of(other));

    assertThat(session.boardQuest("a")).isEmpty();
    assertThat(session.boardQuest("b")).contains(other);
  }

  @Test
  void historyIsNewestFirstAndCapped() {
    int moves = GameSession.HISTORY_SIZE + 5;
    for (int turn = 1; turn <= moves; turn++) {
      session.record(event(TurnEvent.Type.SOLVE, turn));
    }

    List<TurnEvent> history = session.history();

    assertThat(history).hasSize(GameSession.HISTORY_SIZE);
    assertThat(history.get(0).state().turn()).isEqualTo(moves);
    assertThat(history.get(history.size() - 1).state().turn()).isEqualTo(6);
  }

  @Test
  void endingRecordsGameOverOnce() {
    session.end();
    session.end();

    assertThat(session.state().isOver()).isTrue();
    assertThat(session.history())
        .singleElement()
        .satisfies(e -> {
          assertThat(e.type()).isEqualTo(TurnEvent.Type.GAME_OVER);
          assertThat(e.message()).isEqualTo("Game over with 40 points");
        });
  }

  @Test
  void shopIsEmptyUntilSetAndCopied() {
    assertThat(session.shop()).isEmpty();
    List<ShopItem> items = new java.util.ArrayList<>(List.of(POTION));

    session.shop(items);
    items.clear();

    assertThat(session.shop().orElseThrow()).hasSize(1);
  }
}
