package dihclient.palette;

import dihclient.palette.PaletteIndex.Entry;
import dihclient.palette.PaletteIndex.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteIndexTest {

    private static Entry module(String title, String... keywords) {
        return new Entry(Kind.MODULE, title, List.of(keywords), "", "module:" + title.toLowerCase().replace(" ", "-"));
    }

    private static final Entry KILL_AURA = module("KillAura", "combat", "attack");
    private static final Entry AUTO_TRADE = module("AutoTrade", "villager", "reroll");
    private static final Entry AUTO_ARMOR = module("AutoArmor");
    private static final Entry AUTO_TOTEM = module("AutoTotem");
    private static final Entry FULLBRIGHT = module("Fullbright", "gamma");
    private static final Entry TRADE_CMD = new Entry(Kind.DOT_COMMAND, ".trade", List.of(), "", "dot:trade");
    private static final Entry ACQUIRE = new Entry(Kind.HASH_COMMAND, "#acquire", List.of("get", "craft"), "Gets an item", "hash:acquire");
    private static final Entry FARM_MACRO = new Entry(Kind.MACRO, "farm loop", List.of("Skyblock"), "", "macro:farm loop");
    private static final Entry SPEED_SETTING = new Entry(Kind.SETTING, "KillAura › Attack Speed", List.of("killaura", "cps"), "", "setting:killaura:speed");

    private final PaletteIndex index = new PaletteIndex(List.of(
        KILL_AURA, AUTO_TRADE, AUTO_ARMOR, AUTO_TOTEM, FULLBRIGHT, TRADE_CMD, ACQUIRE, FARM_MACRO, SPEED_SETTING));

    private List<Entry> search(String query, String... recent) {
        return index.search(query, 8, List.of(recent));
    }

    @Test
    void prefixBeatsWordStartBeatsSubsequence() {
        Entry prefix = module("Trade Helper");
        Entry wordStart = module("Auto Trade");
        Entry subsequence = module("Treasure Radar");
        PaletteIndex local = new PaletteIndex(List.of(subsequence, wordStart, prefix));

        assertEquals(List.of(prefix, wordStart, subsequence), local.search("tra", 8, List.of()));
    }

    @Test
    void exactTitleComesFirst() {
        assertEquals(FULLBRIGHT, search("fullbright").get(0));
    }

    @Test
    void shorterPrefixMatchesRankHigher() {
        List<Entry> results = search("auto");
        assertEquals(List.of(AUTO_ARMOR, AUTO_TOTEM, AUTO_TRADE), results.subList(0, 3));
    }

    @Test
    void keywordsFindThings() {
        assertEquals(FULLBRIGHT, search("gamma").get(0));
        assertEquals(AUTO_TRADE, search("reroll").get(0));
        assertEquals(ACQUIRE, search("craft").get(0));
    }

    @Test
    void lettersInOrderFindTheModule() {
        assertEquals(KILL_AURA, search("klra").get(0));
        assertEquals(AUTO_TRADE, search("atrd").get(0));
    }

    @Test
    void oneWrongLetterIsForgiven() {
        assertEquals(KILL_AURA, search("killauxa").get(0));
        assertEquals(FULLBRIGHT, search("fulbrihgt").get(0));
    }

    @Test
    void nonsenseFindsNothing() {
        assertTrue(search("zzqx").isEmpty());
    }

    @Test
    void recentlyRunEntriesAreBoosted() {
        assertEquals(AUTO_ARMOR, search("auto").get(0));
        assertEquals(AUTO_TRADE, search("auto", "module:autotrade").get(0));
    }

    @Test
    void theBoostDoesNotBeatAMuchBetterMatch() {
        // "fullbright" is an exact title; a recent substring-only hit shouldn't jump above it.
        assertEquals(FULLBRIGHT, search("fullbright", "module:autotrade").get(0));
    }

    @Test
    void anEmptyQueryListsRecentFirst() {
        List<Entry> results = search("", "macro:farm loop", "hash:acquire");
        assertEquals(FARM_MACRO, results.get(0));
        assertEquals(ACQUIRE, results.get(1));
        assertEquals(8, results.size());
    }

    @Test
    void settingsCarryTheirModuleName() {
        assertEquals(SPEED_SETTING, search("cps").get(0));
        assertTrue(search("attack speed").contains(SPEED_SETTING));
    }

    @Test
    void rememberKeepsTheMostRecentFirstWithoutDuplicates() {
        List<String> recent = PaletteIndex.remember(List.of("a", "b", "c"), "b", 3);
        assertEquals(List.of("b", "a", "c"), recent);
        assertEquals(List.of("d", "b", "a"), PaletteIndex.remember(recent, "d", 3));
    }
}
