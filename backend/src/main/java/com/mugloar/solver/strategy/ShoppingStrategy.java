package com.mugloar.solver.strategy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.List;
import java.util.Optional;

public interface ShoppingStrategy {

  Optional<ShopItem> nextPurchase(GameState state, List<ShopItem> shop);
}
