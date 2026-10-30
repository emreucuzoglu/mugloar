package com.mugloar.web;

import com.mugloar.domain.ShopItem;

record ShopItemView(String id, String name, int cost, boolean recommended) {

  static ShopItemView of(ShopItem item, boolean recommended) {
    return new ShopItemView(item.id(), item.name(), item.cost(), recommended);
  }
}
