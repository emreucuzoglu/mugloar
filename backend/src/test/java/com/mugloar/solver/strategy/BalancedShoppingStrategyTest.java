package com.mugloar.solver.strategy;

import static com.mugloar.testsupport.Fixtures.POTION;
import static com.mugloar.testsupport.Fixtures.SHOPPING_STRATEGY;
import static com.mugloar.testsupport.Fixtures.aGameState;
import static com.mugloar.testsupport.Fixtures.aQuest;
import static com.mugloar.testsupport.Fixtures.aShoppingSettings;
import static com.mugloar.testsupport.Fixtures.shopItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mugloar.domain.GameState;
import com.mugloar.domain.Probability;
import com.mugloar.domain.Quest;
import com.mugloar.domain.ShopItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BalancedShoppingStrategyTest {

  private static final int RANDOM_STATES = 1000;
  private static final int MAX_GOLD = 1500;
  private static final int MAX_TURN = 120;
  private static final int RESERVE = 50;
  private static final int SURE_THINGS_TO_HARVEST = 7;
  private static final List<Quest> NO_JOBS = List.of();
  private static final String TRAP_MESSAGE =
      "Steal super awesome diamond beer mug from Ketzi'ah Huddleson";

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

  private static List<Quest> board(int sureThings, Quest... others) {
    Stream<Quest> sure = Stream.generate(() -> aQuest().build()).limit(sureThings);
    return Stream.concat(sure, Stream.of(others)).toList();
  }

  @Test
  void healsWhenWoundedAndAffordable() {
    assertThat(strategy.nextPurchase(state(2, 60, 0), SHOP, NO_JOBS))
        .map(ShopItem::id)
        .contains("hpot");
  }

  @Test
  void savesGoldForPotionWhenWoundedAndBroke() {
    // 40 gold: can't heal, and must not spend it on anything else
    assertThat(strategy.nextPurchase(state(1, 40, 0), SHOP, NO_JOBS)).isEmpty();
  }

  @Test
  void doesNotHealAtFullLives() {
    assertThat(strategy.nextPurchase(state(3, 60, 0), SHOP, NO_JOBS)).isEmpty();
  }

  @Test
  void buysUpgradeKeepingReserve() {
    assertThat(strategy.nextPurchase(state(3, 349, 0), SHOP, NO_JOBS)).isEmpty();
    assertThat(strategy.nextPurchase(state(3, 350, 0), SHOP, NO_JOBS))
        .map(ShopItem::id)
        .contains("ch");
  }

  @Test
  void rotatesThroughTheBigUpgrades() {
    GameState state = state(3, 1000, 0);
    List<String> bought = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      String id = strategy.nextPurchase(state, SHOP, NO_JOBS).orElseThrow().id();
      bought.add(id);
      state = state.afterBuying(id);
    }

    assertThat(bought).containsExactly("ch", "iron", "mtrix", "rf", "wingpotmax", "ch", "iron");
  }

  @Test
  void savesForTheBiggestUpgradeInsteadOfBuyingASmallOne() {
    assertThat(strategy.nextPurchase(state(3, 200, 0), SHOP, NO_JOBS)).isEmpty();
  }

  @Test
  void smallUpgradesCanBeTurnedBackOnForTheEarlyGame() {
    BalancedShoppingStrategy withSmall =
        new BalancedShoppingStrategy(aShoppingSettings().smallUpgradesUntilTurn(15).build());
    GameState early = aGameState().gold(200).turn(14).build();
    GameState later = aGameState().gold(200).turn(15).build();
    GameState rich = aGameState().gold(400).turn(14).build();

    assertThat(withSmall.nextPurchase(early, SHOP, NO_JOBS)).map(ShopItem::id).contains("cs");
    assertThat(withSmall.nextPurchase(later, SHOP, NO_JOBS)).isEmpty();
    assertThat(withSmall.nextPurchase(rich, SHOP, NO_JOBS)).map(ShopItem::id).contains("ch");
  }

  @Test
  void aBiggerUpgradeBeatsASmallerOneWhenBothAreAllowed() {
    BalancedShoppingStrategy withSmall =
        new BalancedShoppingStrategy(aShoppingSettings().smallUpgradesUntilTurn(15).build());
    List<ShopItem> shop =
        List.of(shopItem("cs", "Claw Sharpening", 100), shopItem("rf", "Rocket Fuel", 300));

    assertThat(withSmall.nextPurchase(aGameState().gold(400).turn(14).build(), shop, NO_JOBS))
        .map(ShopItem::id)
        .contains("rf");
  }

  @Test
  void keepsBuyingUpgradesLateInTheGameAndStillHealsFirst() {
    GameState late = aGameState().gold(1000).turn(3000).build();
    GameState woundedAndLate = aGameState().lives(2).gold(1000).turn(3000).build();

    assertThat(strategy.nextPurchase(late, SHOP, NO_JOBS)).map(ShopItem::id).contains("ch");
    assertThat(strategy.nextPurchase(woundedAndLate, SHOP, NO_JOBS))
        .map(ShopItem::id)
        .contains("hpot");
  }

  @Test
  void takesAJobInsteadOfAnUpgradeWhileEnoughJobsAreSureThings() {
    Quest risky = aQuest().probability(Probability.RISKY).build();

    assertThat(strategy.nextPurchase(state(3, 1000, 0), SHOP, board(SURE_THINGS_TO_HARVEST)))
        .isEmpty();
    assertThat(
            strategy.nextPurchase(
                state(3, 1000, 0), SHOP, board(SURE_THINGS_TO_HARVEST - 1, risky, risky, risky)))
        .map(ShopItem::id)
        .contains("ch");
  }

  @Test
  void stillHealsWhileHarvesting() {
    assertThat(strategy.nextPurchase(state(2, 1000, 0), SHOP, board(10)))
        .map(ShopItem::id)
        .contains("hpot");
  }

  @Test
  void aTrapLabelledSureThingDoesNotCountTowardsHarvesting() {
    Quest trap = aQuest().message(TRAP_MESSAGE).build();
    List<Quest> board = board(SURE_THINGS_TO_HARVEST - 1, trap, trap, trap);

    assertThat(strategy.nextPurchase(state(3, 1000, 0), SHOP, board))
        .map(ShopItem::id)
        .contains("ch");
  }

  @Test
  void harvestingCanBeTurnedOff() {
    BalancedShoppingStrategy alwaysBuys =
        new BalancedShoppingStrategy(aShoppingSettings().harvestAtSureThings(0).build());

    assertThat(alwaysBuys.nextPurchase(state(3, 1000, 0), SHOP, board(10)))
        .map(ShopItem::id)
        .contains("ch");
  }

  @Test
  void buysASmallUpgradeWhenTheShopSellsNothingBigger() {
    List<ShopItem> shop =
        List.of(shopItem("gas", "Gasoline", 100), shopItem("cs", "Claw Sharpening", 100));

    assertThat(strategy.nextPurchase(state(3, 150, 0), shop, NO_JOBS))
        .map(ShopItem::id)
        .contains("cs");
  }

  @Test
  void itemsNotInTheCatalogueAreNeverBought() {
    List<ShopItem> shop = List.of(shopItem("mystery", "Mystery box", 100));

    assertThat(strategy.nextPurchase(state(3, 1000, 0), shop, NO_JOBS)).isEmpty();
    assertThat(strategy.nextPurchase(state(1, 1000, 0), shop, NO_JOBS)).isEmpty();
  }

  @Test
  void usesThePriceTheShopAsksNotTheCatalogue() {
    List<ShopItem> dearer = List.of(shopItem("hpot", "Healing potion", 80));

    assertThat(strategy.nextPurchase(state(2, 60, 0), dearer, NO_JOBS)).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 80, 0), dearer, NO_JOBS))
        .map(ShopItem::id)
        .contains("hpot");
  }

  @Test
  void neverBuysForFinishedGameOrEmptyShop() {
    assertThat(strategy.nextPurchase(state(0, 1000, 0), SHOP, NO_JOBS)).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 1000, 0), List.of(), NO_JOBS)).isEmpty();
    assertThat(strategy.nextPurchase(state(2, 1000, 0), null, NO_JOBS)).isEmpty();
  }

  @Test
  void ignoresFreeUpgrades() {
    List<ShopItem> shop = List.of(shopItem("free", "Suspicious gift", 0));

    assertThat(strategy.nextPurchase(state(3, 1000, 0), shop, NO_JOBS)).isEmpty();
  }

  @Test
  void woundedPlayerWithNoPotionOnSaleBuysNothing() {
    List<ShopItem> shop = List.of(shopItem("cs", "Claw Sharpening", 100));

    assertThat(strategy.nextPurchase(state(2, 1000, 0), shop, NO_JOBS)).isEmpty();
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
          .nextPurchase(state, SHOP, NO_JOBS)
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
    assertThatThrownBy(() -> aShoppingSettings().harvestAtSureThings(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
