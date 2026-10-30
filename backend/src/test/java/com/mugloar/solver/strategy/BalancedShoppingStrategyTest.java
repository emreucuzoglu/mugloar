package com.mugloar.solver.strategy;

import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.SHOPPING_STRATEGY;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aShoppingSettings;
import static com.mugloar.testsupport.Fixtures.shopItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.ShopItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BalancedShoppingStrategyTest {

  private static final int RANDOM_STATES = 1000;
  private static final int MAX_GOLD = 1500;
  private static final int MAX_TURN = 120;
  private static final int RESERVE = 50;

  private static final List<ShopItem> SHOP =
      List.of(
          POTION,
          shopItem("cs", "Claw Sharpening", 100),
          shopItem("gas", "Gasoline", 100),
          shopItem("wax", "Copper Plating", 100),
          shopItem("tricks", "Book of Tricks", 100),
          shopItem("wingpot", "Potion of Stronger Wings", 100),
          shopItem("ch", "Claw Honing", 300),
          shopItem("rf", "Rocket Fuel", 300),
          shopItem("iron", "Iron Plating", 300),
          shopItem("mtrix", "Book of Megatricks", 300),
          shopItem("wingpotmax", "Potion of Awesome Wings", 300));

  private final BalancedShoppingStrategy strategy = SHOPPING_STRATEGY;

  private static GameState state(int lives, int gold, int level) {
    return aGameState().lives(lives).gold(gold).level(level).build();
  }

  @Test
  void healsWhenWoundedAndAffordable() {
    assertThat(strategy.nextPurchase(state(2, 60, 0), SHOP)).map(ShopItem::id).contains("hpot");
  }

  @Test
  void savesGoldForPotionWhenWoundedAndBroke() {
    // 40 gold: can't heal, and must not spend it on anything else
    assertThat(strategy.nextPurchase(state(1, 40, 0), SHOP)).isEmpty();
  }

  @Test
  void doesNotHealAtFullLives() {
    assertThat(strategy.nextPurchase(state(3, 60, 0), SHOP)).isEmpty();
  }

  @Test
  void buysUpgradeKeepingReserve() {
    assertThat(strategy.nextPurchase(state(3, 349, 0), SHOP)).isEmpty();
    assertThat(strategy.nextPurchase(state(3, 350, 0), SHOP)).map(ShopItem::id).contains("ch");
  }

  @Test
  void rotatesThroughTheBigUpgrades() {
    GameState state = state(3, 1000, 0);
    List<String> bought = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      String id = strategy.nextPurchase(state, SHOP).orElseThrow().id();
      bought.add(id);
      state = state.afterBuying(id);
    }

    assertThat(bought).containsExactly("ch", "iron", "mtrix", "rf", "wingpotmax", "ch", "iron");
  }

  @Test
  void savesForTheBiggestUpgradeInsteadOfBuyingASmallOne() {
    assertThat(strategy.nextPurchase(state(3, 200, 0), SHOP)).isEmpty();
  }

  @Test
  void smallUpgradesCanBeTurnedBackOnForTheEarlyGame() {
    BalancedShoppingStrategy withSmall =
        new BalancedShoppingStrategy(aShoppingSettings().smallUpgradesUntilTurn(15).build());
    GameState early = aGameState().gold(200).turn(14).build();
    GameState later = aGameState().gold(200).turn(15).build();
    GameState rich = aGameState().gold(400).turn(14).build();

    assertThat(withSmall.nextPurchase(early, SHOP)).map(ShopItem::id).contains("cs");
    assertThat(withSmall.nextPurchase(later, SHOP)).isEmpty();
    assertThat(withSmall.nextPurchase(rich, SHOP)).map(ShopItem::id).contains("ch");
  }

  @Test
  void aBiggerUpgradeBeatsASmallerOneWhenBothAreAllowed() {
    BalancedShoppingStrategy withSmall =
        new BalancedShoppingStrategy(aShoppingSettings().smallUpgradesUntilTurn(15).build());
    List<ShopItem> shop =
        List.of(shopItem("cs", "Claw Sharpening", 100), shopItem("rf", "Rocket Fuel", 300));

    assertThat(withSmall.nextPurchase(aGameState().gold(400).turn(14).build(), shop))
        .map(ShopItem::id)
        .contains("rf");
  }

  @Test
  void stopsBuyingUpgradesLateInTheGameButStillHeals() {
    GameState justBefore = aGameState().gold(1000).turn(69).build();
    GameState tooLate = aGameState().gold(1000).turn(70).build();
    GameState woundedAndLate = aGameState().lives(2).gold(1000).turn(70).build();

    assertThat(strategy.nextPurchase(justBefore, SHOP)).map(ShopItem::id).contains("ch");
    assertThat(strategy.nextPurchase(tooLate, SHOP)).isEmpty();
    assertThat(strategy.nextPurchase(woundedAndLate, SHOP)).map(ShopItem::id).contains("hpot");
  }

  @Test
  void buysASmallUpgradeWhenTheShopSellsNothingBigger() {
    List<ShopItem> shop =
        List.of(shopItem("gas", "Gasoline", 100), shopItem("cs", "Claw Sharpening", 100));

    assertThat(strategy.nextPurchase(state(3, 150, 0), shop)).map(ShopItem::id).contains("cs");
  }

  @Test
  void itemsNotInTheCatalogueAreNeverBought() {
    List<ShopItem> shop = List.of(shopItem("mystery", "Mystery box", 100));

    assertThat(strategy.nextPurchase(state(3, 1000, 0), shop)).isEmpty();
    assertThat(strategy.nextPurchase(state(1, 1000, 0), shop)).isEmpty();
  }

  @Test
  void usesThePriceTheShopAsksNotTheCatalogue() {
    List<ShopItem> dearer = List.of(shopItem("hpot", "Healing potion", 80));

    assertThat(strategy.nextPurchase(state(2, 60, 0), dearer)).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 80, 0), dearer)).map(ShopItem::id).contains("hpot");
  }

  @Test
  void neverBuysForFinishedGameOrEmptyShop() {
    assertThat(strategy.nextPurchase(state(0, 1000, 0), SHOP)).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 1000, 0), List.of())).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 1000, 0), null)).isEmpty();
  }

  @Test
  void ignoresFreeUpgrades() {
    List<ShopItem> shop = List.of(shopItem("free", "Suspicious gift", 0));

    assertThat(strategy.nextPurchase(state(3, 1000, 0), shop)).isEmpty();
  }

  @Test
  void woundedPlayerWithNoPotionOnSaleBuysNothing() {
    List<ShopItem> shop = List.of(shopItem("cs", "Claw Sharpening", 100));

    assertThat(strategy.nextPurchase(state(2, 1000, 0), shop)).isEmpty();
  }

  @Test
  void anyRandomStateNeverSpendsMoreThanItCanAffordAndUpgradesKeepTheReserve() {
    for (long seed = 0; seed < RANDOM_STATES; seed++) {
      Random random = new Random(seed);
      GameState state =
          aGameState()
              .lives(1 + random.nextInt(5))
              .gold(random.nextInt(MAX_GOLD))
              .turn(random.nextInt(MAX_TURN))
              .build();

      strategy
          .nextPurchase(state, SHOP)
          .ifPresent(item -> {
            int reserve = item.id().equals(POTION.id()) ? 0 : RESERVE;
            assertThat(item.cost() + reserve).as("%s", state).isLessThanOrEqualTo(state.gold());
          });
    }
  }

  @Test
  void rejectsInvalidSettings() {
    assertThatThrownBy(() -> aShoppingSettings().healBelowLives(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> aShoppingSettings().goldReserve(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> aShoppingSettings().smallUpgradesUntilTurn(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> aShoppingSettings().upgradesUntilTurn(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
