package com.mugloar.domain;

import java.util.List;
import java.util.Optional;

public record ShopOffer(List<ShopItem> items, Optional<ShopItem> recommended) {

  public boolean isRecommended(ShopItem item) {
    return recommended.filter(item::equals).isPresent();
  }
}
