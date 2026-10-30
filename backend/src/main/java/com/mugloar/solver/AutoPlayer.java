package com.mugloar.solver;

import com.mugloar.domain.GameState;
import com.mugloar.domain.PurchaseResult;
import com.mugloar.domain.Quest;
import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.QuestEvaluation;
import com.mugloar.domain.Reputation;
import com.mugloar.domain.ShopItem;
import com.mugloar.domain.ShopOffer;
import com.mugloar.domain.SolveResult;
import com.mugloar.gateway.GameOverException;
import com.mugloar.gateway.MugloarApiException;
import com.mugloar.gateway.MugloarGateway;
import com.mugloar.solver.strategy.QuestSelector;
import com.mugloar.solver.strategy.ShoppingStrategy;
import com.mugloar.util.LogSanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public final class AutoPlayer {

  public static final int MAX_CONSECUTIVE_FAILURES = 5;
  private static final int NO_TARGET = 0;

  @NonNull private final MugloarGateway gateway;
  @NonNull private final QuestSelector selector;
  @NonNull private final ShoppingStrategy shopping;

  public GameResult play(
      GameState start, int targetScore, int maxMoves, Consumer<TurnEvent> listener) {
    Consumer<TurnEvent> sink = listener == null ? e -> {} : listener;
    List<ShopItem> shop = loadShop(start.gameId());
    GameState state = start;
    int moves = 0;
    int failuresInARow = 0;

    while (true) {
      if (state.isOver()) {
        return new GameResult(state, moves, GameResult.StopReason.GAME_OVER);
      }
      if (targetScore > NO_TARGET && state.score() >= targetScore) {
        return new GameResult(state, moves, GameResult.StopReason.TARGET_REACHED);
      }
      if (moves >= maxMoves) {
        return new GameResult(state, moves, GameResult.StopReason.MOVE_LIMIT);
      }
      if (failuresInARow >= MAX_CONSECUTIVE_FAILURES) {
        return new GameResult(state, moves, GameResult.StopReason.STALLED);
      }

      List<TurnEvent> events;
      try {
        if (shop.isEmpty()) {
          shop = loadShop(state.gameId());
        }
        events = playTurn(state, shop);
      } catch (GameOverException e) {
        return new GameResult(state.ended(), moves, GameResult.StopReason.GAME_OVER);
      } catch (MugloarApiException e) {
        log.warn("Game server call failed: {}", e.getMessage());
        if (e.isRateLimited()) {
          return new GameResult(state, moves, GameResult.StopReason.RATE_LIMITED);
        }
        failuresInARow++;
        sink.accept(new TurnEvent(TurnEvent.Type.SKIP, false, "Game server error", state));
        continue;
      }
      for (TurnEvent event : events) {
        sink.accept(event);
        state = event.state();
        if (event.type() == TurnEvent.Type.SKIP) {
          failuresInARow++;
        } else {
          failuresInARow = 0;
          moves++;
        }
      }
    }
  }

  /** Returns two events when a purchase fails and a quest is taken instead. */
  public List<TurnEvent> playTurn(GameState state, List<ShopItem> shop) {
    List<TurnEvent> events = new ArrayList<>();
    if (state.isOver()) {
      events.add(gameOver(state));
      return events;
    }
    try {
      Optional<ShopItem> item = shopping.nextPurchase(state, shop);
      if (item.isPresent()) {
        TurnEvent bought = purchase(state, item.get());
        events.add(bought);
        if (bought.success()) {
          return events;
        }
        state = bought.state();
      }
      events.add(solveBest(state));
    } catch (GameOverException e) {
      events.add(gameOver(state));
    } catch (MugloarApiException e) {
      if (events.isEmpty()) {
        throw e;
      }
      log.warn("Game server call failed after a refused purchase: {}", e.getMessage());
    }
    return events;
  }

  /** A turn is one move: when the solver would shop, it recommends none of these quests. */
  public QuestBoard adviseQuests(GameState state, List<ShopItem> shop) {
    List<QuestEvaluation> ranked = selector.rank(readBoard(state.gameId()), state);
    Optional<Quest> recommended =
        shopping.nextPurchase(state, shop).isPresent()
            ? Optional.empty()
            : selector.best(ranked).map(QuestEvaluation::quest);
    return new QuestBoard(ranked, recommended);
  }

  public ShopOffer adviseShop(GameState state, List<ShopItem> shop) {
    return new ShopOffer(shop, shopping.nextPurchase(state, shop));
  }

  public List<Quest> readBoard(String gameId) {
    return gateway.getBoard(gameId);
  }

  TurnEvent solveBest(GameState state) {
    Optional<QuestEvaluation> choice = selector.choose(readBoard(state.gameId()), state);
    if (choice.isEmpty()) {
      return investigate(state);
    }
    return solve(state, choice.get().quest());
  }

  public TurnEvent solve(GameState state, Quest quest) {
    SolveResult result;
    try {
      result = solveAllowingRawId(state, quest);
    } catch (MugloarApiException e) {
      if (e.isRefusal()) {
        log.warn("Quest {} rejected: {}", LogSanitizer.clean(quest.questId()), e.getMessage());
        String kind = quest.encrypted() ? "encrypted quest " : "";
        return skip(
            state,
            "Could not take " + kind + "\"" + quest.message() + "\": the game server refused it");
      }
      if (e.isTransient()) {
        log.warn("No answer on quest {}: {}", LogSanitizer.clean(quest.questId()), e.getMessage());
        return skip(
            state.afterPossibleWin(quest.questType()),
            "No answer on \"" + quest.message() + "\": the move may have counted");
      }
      throw e;
    }
    GameState next =
        result.success()
            ? state.after(result).afterWinning(quest.questType())
            : state.after(result);
    String verdict = result.success() ? "Succeeded" : "Failed";
    String message =
        String.format(
            Locale.ROOT,
            "%s [%s, %d gold]: %s%s",
            verdict,
            quest.probability().label(),
            quest.reward(),
            quest.message(),
            StringUtils.isBlank(result.message()) ? "" : " — " + result.message());
    return new TurnEvent(TurnEvent.Type.SOLVE, result.success(), message, next);
  }

  private SolveResult solveAllowingRawId(GameState state, Quest quest) {
    try {
      return gateway.solve(state.gameId(), quest.questId());
    } catch (MugloarApiException e) {
      if (!e.isRefusal() || !quest.encrypted() || quest.rawQuestId().equals(quest.questId())) {
        throw e;
      }
      return gateway.solve(state.gameId(), quest.rawQuestId());
    }
  }

  public TurnEvent purchase(GameState state, ShopItem item) {
    PurchaseResult result;
    try {
      result = gateway.buy(state.gameId(), item.id());
    } catch (MugloarApiException e) {
      if (!e.isRefusal()) {
        throw e;
      }
      log.warn("Purchase of {} rejected: {}", LogSanitizer.clean(item.id()), e.getMessage());
      return skip(state, "Could not buy " + item.name() + ": the game server refused it");
    }
    GameState next =
        result.shoppingSuccess() ? state.after(result).afterBuying(item.id()) : state.after(result);
    String message =
        result.shoppingSuccess()
            ? String.format(Locale.ROOT, "Bought %s for %d gold", item.name(), item.cost())
            : "The shop refused to sell " + item.name();
    return new TurnEvent(TurnEvent.Type.PURCHASE, result.shoppingSuccess(), message, next);
  }

  public TurnEvent investigate(GameState state) {
    Reputation rep = gateway.investigateReputation(state.gameId());
    String message =
        String.format(
            Locale.ROOT,
            "Reputation: people %.1f, state %.1f, underworld %.1f",
            rep.people(),
            rep.state(),
            rep.underworld());
    return new TurnEvent(
        TurnEvent.Type.INVESTIGATE,
        true,
        message,
        state.afterTurn().withStateReputation(rep.state()));
  }

  private List<ShopItem> loadShop(String gameId) {
    try {
      return gateway.getShop(gameId);
    } catch (GameOverException e) {
      throw e;
    } catch (MugloarApiException e) {
      log.warn("Could not read the shop, trying again next turn: {}", e.getMessage());
      return List.of();
    }
  }

  private static TurnEvent skip(GameState state, String message) {
    return new TurnEvent(TurnEvent.Type.SKIP, false, message, state);
  }

  private static TurnEvent gameOver(GameState state) {
    GameState ended = state.ended();
    return new TurnEvent(
        TurnEvent.Type.GAME_OVER, false, "Game over with " + ended.score() + " points", ended);
  }
}
