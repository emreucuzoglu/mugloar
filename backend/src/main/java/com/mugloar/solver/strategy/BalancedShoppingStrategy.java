package com.mugloar.solver.strategy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Item;
import com.mugloar.domain.ShopItem;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BalancedShoppingStrategy implements ShoppingStrategy {

  private static final int ANY_GAIN = 1;

  @NonNull private final ShoppingSettings settings;

  @Override
  public Optional<ShopItem> nextPurchase(GameState state, List<ShopItem> shop) {
    if (state.isOver() || shop == null || shop.isEmpty()) {
      return Optional.empty();
    }
    if (state.lives() < settings.healBelowLives()) {
      return shop.stream()
          .filter(item -> gain(item, Item::livesGained) > 0)
          .filter(item -> item.cost() <= state.gold())
          .findFirst();
    }
    return bestUpgrade(state, shop);
  }

  /** Among equal gains, the item bought least comes first: rotating the +2 items scored highest. */
  private Optional<ShopItem> bestUpgrade(GameState state, List<ShopItem> shop) {
    if (state.turn() >= settings.upgradesUntilTurn()) {
      return Optional.empty();
    }
    int budget = state.gold() - settings.goldReserve();
    int biggestGain =
        shop.stream().mapToInt(item -> gain(item, Item::levelsGained)).max().orElse(0);
    int smallestGain = state.turn() < settings.smallUpgradesUntilTurn() ? ANY_GAIN : biggestGain;
    return shop.stream()
        .filter(item -> gain(item, Item::levelsGained) > 0)
        .filter(item -> gain(item, Item::levelsGained) >= smallestGain)
        .filter(item -> item.cost() <= budget)
        .max(
            Comparator.<ShopItem>comparingInt(item -> gain(item, Item::levelsGained))
                .thenComparing(item -> state.timesBought(item.id()), Comparator.reverseOrder())
                .thenComparing(ShopItem::id, Comparator.reverseOrder()));
  }

  private static int gain(ShopItem item, ToIntFunction<Item> effect) {
    return Item.fromId(item.id()).map(effect::applyAsInt).orElse(0);
  }
}
