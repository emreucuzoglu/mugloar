package com.mugloar.web;

import com.mugloar.domain.QuestBoard;
import com.mugloar.domain.QuestEvaluation;
import com.mugloar.domain.ShopOffer;
import com.mugloar.game.GameService;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/games")
class GameController {

  static final String ID = "^[A-Za-z0-9_-]{1,64}$";
  static final String ITEM_ID = "^[a-z0-9]{1,32}$";

  private final GameService gameService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GameStateView start() {
    return GameStateView.of(gameService.start());
  }

  @GetMapping("/{gameId}")
  public GameStateView state(@PathVariable @Pattern(regexp = ID) String gameId) {
    return GameStateView.of(gameService.state(gameId));
  }

  @GetMapping("/{gameId}/history")
  public List<TurnEventView> history(@PathVariable @Pattern(regexp = ID) String gameId) {
    return TurnEventView.of(gameService.history(gameId));
  }

  @GetMapping("/{gameId}/quests")
  public List<QuestView> quests(@PathVariable @Pattern(regexp = ID) String gameId) {
    QuestBoard board = gameService.board(gameId);
    List<QuestEvaluation> ranked = board.ranked();
    List<QuestView> views = new ArrayList<>(ranked.size());
    for (int i = 0; i < ranked.size(); i++) {
      QuestEvaluation quest = ranked.get(i);
      views.add(QuestView.of(quest, i + 1, board.isRecommended(quest.quest())));
    }
    return views;
  }

  @GetMapping("/{gameId}/shop")
  public List<ShopItemView> shop(@PathVariable @Pattern(regexp = ID) String gameId) {
    ShopOffer offer = gameService.shop(gameId);
    return offer.items().stream()
        .map(item -> ShopItemView.of(item, offer.isRecommended(item)))
        .toList();
  }

  @PostMapping("/{gameId}/quests/{questId}/solve")
  public TurnEventView solve(
      @PathVariable @Pattern(regexp = ID) String gameId,
      @PathVariable @Pattern(regexp = ID) String questId) {
    return TurnEventView.of(gameService.solve(gameId, questId));
  }

  @PostMapping("/{gameId}/shop/{itemId}/buy")
  public TurnEventView buy(
      @PathVariable @Pattern(regexp = ID) String gameId,
      @PathVariable @Pattern(regexp = ITEM_ID) String itemId) {
    return TurnEventView.of(gameService.buy(gameId, itemId));
  }

  @PostMapping("/{gameId}/investigate")
  public TurnEventView investigate(@PathVariable @Pattern(regexp = ID) String gameId) {
    return TurnEventView.of(gameService.investigate(gameId));
  }

  @PostMapping("/{gameId}/autoplay/step")
  public List<TurnEventView> autoplayStep(@PathVariable @Pattern(regexp = ID) String gameId) {
    return TurnEventView.of(gameService.autoplayStep(gameId));
  }
}
