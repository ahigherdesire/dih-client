package dihclient.trade;

import dihclient.trade.TradeBudget.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TradeLogicTest {
    private static final String BOOK = "minecraft:book";

    static TradeOffer book(int index, String enchantment, int level, int price) {
        return new TradeOffer(index, TradeOffer.ENCHANTED_BOOK, 1, Map.of("minecraft:" + enchantment, level),
                TradeOffer.EMERALD, price, BOOK, 1, 0, 12);
    }

    static TradeOffer item(int index, String id, int price) {
        return new TradeOffer(index, "minecraft:" + id, 1, Map.of(), TradeOffer.EMERALD, price, null, 0, 0, 12);
    }

    static VillagerOffers villager(String uuid, int xp, int x, TradeOffer... offers) {
        return new VillagerOffers(uuid, EnchantPlanner.LIBRARIAN, 1, xp, x, 64, 0, List.of(offers), 0);
    }

    // ---- rules

    @Test
    void parsesEveryRuleShape() {
        assertEquals(TradeRule.Kind.ANY, TradeRule.parse("any").kind());
        assertEquals(TradeRule.Kind.ANY, TradeRule.parse("").kind());
        TradeRule unbreaking = TradeRule.parse("Unbreaking3");
        assertEquals("minecraft:unbreaking", unbreaking.enchantment());
        assertEquals(3, unbreaking.minLevel());
        assertEquals(0, unbreaking.maxLevel());
        TradeRule range = TradeRule.parse("sharpness 4-5");
        assertEquals(4, range.minLevel());
        assertEquals(5, range.maxLevel());
        assertEquals(1, TradeRule.parse("mending").minLevel());
        assertEquals(2, TradeRule.parse("slot 2").slot());
        assertEquals("minecraft:bread", TradeRule.parse("item:bread").itemId());
        assertEquals("minecraft:bread", TradeRule.parse("minecraft:bread").itemId());
        assertEquals(TradeRule.Kind.BOOK, TradeRule.parse("book").kind());
        assertThrows(IllegalArgumentException.class, () -> TradeRule.parse("slot x"));
        assertThrows(IllegalArgumentException.class, () -> TradeRule.parse("sharpness 5-2"));
        assertThrows(IllegalArgumentException.class, () -> TradeRule.parse("what is this?"));
    }

    @Test
    void bookRulesMatchEnchantmentAndLevel() {
        TradeRule u3 = TradeRule.parse("unbreaking3");
        assertTrue(u3.matches(book(0, "unbreaking", 3, 20), null));
        assertFalse(u3.matches(book(0, "unbreaking", 2, 20), null), "below the wanted level");
        assertFalse(u3.matches(book(0, "mending", 1, 20), null));
        assertFalse(u3.matches(item(0, "bread", 1), null));
        TradeRule exact = TradeRule.parse("sharpness 2-3");
        assertFalse(exact.matches(book(0, "sharpness", 4, 30), null), "above the range");
        assertTrue(TradeRule.parse("book").matches(book(0, "sharpness", 4, 30), null));
    }

    @Test
    void slotItemAndProfessionRules() {
        assertTrue(TradeRule.parse("slot 1").matches(item(1, "bread", 1), null));
        assertFalse(TradeRule.parse("slot 1").matches(item(0, "bread", 1), null));
        assertTrue(TradeRule.parse("item:bread").matches(item(3, "bread", 1), null));
        TradeRule librarian = TradeRule.any().withProfession("librarian");
        assertTrue(librarian.matches(item(0, "bookshelf", 9), "minecraft:librarian"));
        assertFalse(librarian.matches(item(0, "bread", 1), "minecraft:farmer"));
    }

    @Test
    void describesRules() {
        assertEquals("mending 1", TradeRule.parse("mending").describe());
        assertEquals("unbreaking 3+ ≤20e", TradeRule.parse("unbreaking3").withMaxPrice(20).describe());
        assertEquals("sharpness 4-5", TradeRule.parse("sharpness 4-5").describe());
    }

    // ---- budget

    @Test
    void budgetReserveCapAndPrice() {
        TradeOffer offer = book(0, "mending", 1, 14);
        TradeRule rule = TradeRule.parse("mending");
        assertEquals(Verdict.BUY, TradeBudget.UNLIMITED.check(offer, rule, 64, 0, 0));
        assertEquals(Verdict.TOO_EXPENSIVE, TradeBudget.UNLIMITED.check(offer, rule.withMaxPrice(10), 64, 0, 0));
        assertEquals(Verdict.BUY, TradeBudget.UNLIMITED.check(offer, rule.withMaxPrice(14), 64, 0, 0));
        assertEquals(Verdict.BUDGET_SPENT, new TradeBudget(20, 0, 0).check(offer, rule, 64, 10, 0), "10 + 14 > 20");
        assertEquals(Verdict.BUY, new TradeBudget(24, 0, 0).check(offer, rule, 64, 10, 0), "exactly the budget");
        assertEquals(Verdict.BELOW_RESERVE, new TradeBudget(0, 10, 0).check(offer, rule, 20, 0, 0), "20 - 14 < 10");
        assertEquals(Verdict.BUY, new TradeBudget(0, 6, 0).check(offer, rule, 20, 0, 0), "20 - 14 = 6 keeps the reserve");
        assertEquals(Verdict.VISIT_CAP, new TradeBudget(0, 0, 2).check(offer, rule, 64, 0, 2));
        assertEquals(Verdict.NOT_ENOUGH_EMERALDS, TradeBudget.UNLIMITED.check(offer, rule, 13, 0, 0));
        TradeOffer soldOut = new TradeOffer(0, TradeOffer.ENCHANTED_BOOK, 1, Map.of("minecraft:mending", 1),
                TradeOffer.EMERALD, 14, BOOK, 1, 12, 12);
        assertEquals(Verdict.OUT_OF_STOCK, TradeBudget.UNLIMITED.check(soldOut, rule, 64, 0, 0));
    }

    @Test
    void emeraldsInEitherCostSlotCount() {
        TradeOffer both = new TradeOffer(0, "minecraft:diamond_sword", 1, Map.of(), "minecraft:emerald", 10,
                "minecraft:emerald", 5, 0, 3);
        assertEquals(15, both.emeraldPrice());
        assertEquals(0, new TradeOffer(0, "minecraft:emerald", 1, Map.of(), "minecraft:wheat", 20, null, 0, 0, 16).emeraldPrice());
    }

    // ---- planner

    @Test
    void plannerPicksTheCheapestOfferPerBook() {
        List<VillagerOffers> seen = List.of(
                villager("a", 5, 0, book(0, "mending", 1, 30), book(1, "unbreaking", 3, 12)),
                villager("b", 5, 10, book(0, "mending", 1, 18)));
        EnchantPlanner.Plan plan = EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("mending", "unbreaking3")),
                seen, 0, 64, 0);
        assertTrue(plan.complete());
        assertEquals(30, plan.totalEmeralds(), "18 at b + 12 at a");
        assertEquals(2, plan.stops().size());
    }

    @Test
    void plannerPrefersVillagersAlreadyOnTheRouteOnATie() {
        List<VillagerOffers> seen = List.of(
                villager("a", 5, 0, book(0, "mending", 1, 20)),
                villager("b", 5, 10, book(0, "unbreaking", 3, 15), book(1, "mending", 1, 20)));
        EnchantPlanner.Plan plan = EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("unbreaking", "3", "mending")),
                seen, 0, 64, 0);
        assertEquals(1, plan.stops().size(), EnchantPlanner.explain(plan).toString());
        assertEquals("b", plan.stops().get(0).villager().uuid());
        assertEquals(35, plan.totalEmeralds());
    }

    @Test
    void plannerSkipsLowLevelsAndSoldOutOffers() {
        TradeOffer soldOut = new TradeOffer(0, TradeOffer.ENCHANTED_BOOK, 1, Map.of("minecraft:unbreaking", 3),
                TradeOffer.EMERALD, 5, BOOK, 1, 12, 12);
        List<VillagerOffers> seen = List.of(villager("a", 5, 0, book(0, "unbreaking", 2, 5), soldOut));
        EnchantPlanner.Plan plan = EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("unbreaking3")), seen, 0, 64, 0);
        assertFalse(plan.complete());
        assertEquals("minecraft:unbreaking", plan.missing().get(0).enchantment());
    }

    @Test
    void missingBooksSuggestTheNearestRerollableLibrarian() {
        List<VillagerOffers> seen = List.of(
                villager("traded", 30, 1, book(0, "sharpness", 1, 5)),
                villager("far", 0, 50, book(0, "sharpness", 1, 5)),
                villager("near", 0, 8, book(0, "sharpness", 1, 5)));
        EnchantPlanner.Plan plan = EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("mending")), seen, 0, 64, 0);
        assertEquals("near", plan.reroll().uuid());
        assertTrue(EnchantPlanner.explain(plan).stream().anyMatch(line -> line.contains("Reroll the librarian at 8 64 0")));
        assertNull(EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("sharpness")), seen, 0, 64, 0).reroll());
    }

    @Test
    void plannerRejectsNonEnchantments() {
        assertThrows(IllegalArgumentException.class, () -> EnchantPlanner.parseWants(List.of("slot", "2")));
        assertThrows(IllegalArgumentException.class, () -> EnchantPlanner.parseWants(List.of()));
    }

    // ---- cache

    @Test
    void cacheRoundTripsAndSurvivesDamage(@TempDir Path dir) throws Exception {
        OfferCache cache = new OfferCache();
        cache.put(villager("a", 0, 3, book(0, "mending", 1, 14), item(1, "bookshelf", 9)));
        Path file = dir.resolve("offers").resolve("world.json");
        cache.save(file);
        OfferCache loaded = OfferCache.load(file);
        assertEquals(1, loaded.size());
        VillagerOffers a = loaded.get("a");
        assertEquals(2, a.offers().size());
        assertEquals(1, a.offers().get(0).enchantments().get("minecraft:mending"));
        assertEquals("mending 1 – 14e", a.label());

        Files.writeString(file, "{ not json");
        assertEquals(0, OfferCache.load(file).size());
        assertEquals(0, OfferCache.load(dir.resolve("missing.json")).size());
    }

    @Test
    void labelFallsBackToTheCheapestOffer() {
        assertEquals("bread – 1e", villager("f", 0, 0, item(0, "bread", 1), item(1, "cake", 3)).label());
        assertNull(villager("e", 0, 0).label());
    }

    @Test
    void purchaseConfirmationWaitsLongerOnLaggierServers() {
        assertEquals(4, MerchantBuyer.settleTicksFor(0));
        assertEquals(8, MerchantBuyer.settleTicksFor(100));
        assertTrue(MerchantBuyer.settleTicksFor(300) > MerchantBuyer.settleTicksFor(100));
        assertEquals(MerchantBuyer.SERVER_TIMEOUT / 2, MerchantBuyer.settleTicksFor(10_000));
    }
}
