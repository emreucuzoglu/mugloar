package com.mugloar.game;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.QuestEvaluation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.ShopOffer;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarGateway;
import com.mugloar.solver.AutoPlayer;
import com.mugloar.solver.TurnEvent;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameService {

  private final MugloarGateway gateway;
  private final AutoPlayer player;
  private final GameSessionRepository sessions;

  public GameState start() {
    GameState state = gateway.startGame();
    sessions.save(state.gameId(), new GameSession(state));
    return state;
  }

  public GameState state(String gameId) {
    return withSession(gameId, GameSession::state);
  }

  public List<TurnEvent> history(String gameId) {
    return withSession(gameId, GameSession::history);
  }

  public QuestBoard board(String gameId) {
    return withSession(
        gameId,
        session -> {
          if (session.state().isOver()) {
            return QuestBoard.EMPTY;
          }
          QuestBoard board = player.adviseQuests(session.state(), shopOf(session));
          session.rememberBoard(board.ranked().stream().map(QuestEvaluation::quest).toList());
          return board;
        });
  }

  public ShopOffer shop(String gameId) {
    return withSession(gameId, session -> player.adviseShop(session.state(), shopOf(session)));
  }

  public TurnEvent solve(String gameId, String questId) {
    return withLiveSession(
        gameId,
        session -> {
          Quest quest =
              session
                  .boardQuest(questId)
                  .orElseGet(() -> {
                    // Board not loaded yet, or a move has changed it since: read it again.
                    session.rememberBoard(player.readBoard(gameId));
                    return session
                        .boardQuest(questId)
                        .orElseThrow(() ->
                            new UnavailableException(
                                "Quest " + questId + " is no longer on the board"));
                  });
          return record(session, player.solve(session.state(), quest));
        });
  }

  public TurnEvent buy(String gameId, String itemId) {
    return withLiveSession(
        gameId,
        session -> {
          ShopItem item =
              shopOf(session).stream()
                  .filter(i -> i.id().equals(itemId))
                  .findFirst()
                  .orElseThrow(() -> new UnavailableException("The shop does not sell " + itemId));
          if (item.cost() > session.state().gold()) {
            throw new UnavailableException(
                "Not enough gold for "
                    + item.name()
                    + " ("
                    + item.cost()
                    + " needed, "
                    + session.state().gold()
                    + " available)");
          }
          return record(session, player.purchase(session.state(), item));
        });
  }

  public TurnEvent investigate(String gameId) {
    return withLiveSession(gameId, session -> record(session, player.investigate(session.state())));
  }

  public List<TurnEvent> autoplayStep(String gameId) {
    return withLiveSession(
        gameId,
        session -> {
          if (session.skipsInARow() >= AutoPlayer.MAX_CONSECUTIVE_FAILURES) {
            session.clearSkips();
            throw new UnavailableException(
                "Autoplay stopped because the game server refused its last "
                    + AutoPlayer.MAX_CONSECUTIVE_FAILURES
                    + " moves. Start it again to retry.");
          }
          List<TurnEvent> events = player.playTurn(session.state(), shopOf(session));
          events.forEach(session::record);
          return events;
        });
  }

  private List<ShopItem> shopOf(GameSession session) {
    return session
        .shop()
        .orElseGet(() -> {
          List<ShopItem> items = gateway.getShop(session.state().gameId());
          session.shop(items);
          return items;
        });
  }

  private static TurnEvent record(GameSession session, TurnEvent event) {
    session.record(event);
    return event;
  }

  private <T> T withLiveSession(String gameId, Function<GameSession, T> action) {
    return withSession(
        gameId,
        session -> {
          if (session.state().isOver()) {
            throw new GameOverException(gameId);
          }
          return action.apply(session);
        });
  }

  private <T> T withSession(String gameId, Function<GameSession, T> action) {
    GameSession session =
        sessions.find(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    synchronized (session) {
      try {
        return action.apply(session);
      } catch (GameOverException e) {
        session.end();
        throw e;
      }
    }
  }
}
