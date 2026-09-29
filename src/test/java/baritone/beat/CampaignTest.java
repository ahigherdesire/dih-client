package baritone.beat;

import baritone.acquire.model.Goal;
import baritone.acquire.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The campaign's phases, status line and save file. */
final class CampaignTest {

    private static ToIntFunction<String> have(Map<String, Integer> counts) {
        return id -> counts.getOrDefault(id, 0);
    }

    @Test
    void eightPhasesInOrder() {
        assertEquals(List.of("gear", "portal", "blaze_rods", "pearls", "eyes", "stronghold", "end", "dragon"),
                Arrays.stream(Phase.values()).map(Phase::id).toList());
        assertEquals(List.of(new Goal.AtLocation(Location.NETHER)), Phase.PORTAL.goals(have(Map.of())));
        assertEquals(List.of(new Goal.DragonDead()), Phase.DRAGON.goals(have(Map.of())));
    }

    @Test
    void theStatusLineCountsThePhase() {
        Campaign campaign = new Campaign("sp-test", 1);
        campaign.setPhase(Phase.BLAZE_RODS);
        assertEquals("Phase 3/8: blaze rods 4/7", campaign.statusLine(have(Map.of("minecraft:blaze_rod", 4))));
        campaign.setPhase(Phase.GEAR);
        assertEquals("Phase 1/8: gear 2/6", campaign.statusLine(have(Map.of("minecraft:iron_helmet", 1, "minecraft:diamond_sword", 1))));
        campaign.setPhase(Phase.STRONGHOLD);
        assertEquals("Phase 6/8: the stronghold", campaign.statusLine(have(Map.of())));
    }

    @Test
    void gearGoalsAreTheMissingPieces() {
        List<Goal> goals = Phase.GEAR.goals(have(Map.of("minecraft:iron_helmet", 1, "minecraft:diamond_chestplate", 1,
                "minecraft:iron_leggings", 1, "minecraft:iron_boots", 1, "minecraft:shield", 1)));
        assertEquals(List.of(new Goal.ItemGoal("minecraft:iron_sword", 1)), goals);
        assertTrue(Phase.EYES.goals(have(Map.of("minecraft:ender_eye", 12))).isEmpty(), "a met item phase has nothing left");
    }

    @Test
    void nextStopsAfterTheDragon() {
        Campaign campaign = new Campaign("sp-test", 1);
        int moves = 0;
        while (campaign.next()) moves++;
        assertEquals(7, moves);
        assertEquals(Phase.DRAGON, campaign.phase());
        assertFalse(campaign.next());
    }

    @Test
    void aSavedCampaignComesBackTheSame(@TempDir Path dir) throws IOException {
        CampaignStore store = new CampaignStore(dir);
        Campaign campaign = new Campaign("sp-new_world", 1000);
        campaign.setPhase(Phase.BLAZE_RODS);
        campaign.count(have(Map.of("minecraft:blaze_rod", 4)));
        campaign.portals().put("overworld", new int[]{10, 64, -5});
        campaign.structures().put("fortress", new int[]{200, 70, 40});
        campaign.setProblem("Stopped at step 1/3");
        store.save(campaign, 2000);

        Campaign back = store.load("sp-new_world").orElseThrow();
        assertEquals(Phase.BLAZE_RODS, back.phase());
        assertEquals(1000, back.started());
        assertEquals(2000, back.saved());
        assertEquals(4, back.counters().get("blaze_rods"));
        assertArrayEquals(new int[]{10, 64, -5}, back.portals().get("overworld"));
        assertArrayEquals(new int[]{200, 70, 40}, back.structures().get("fortress"));
        assertEquals("Stopped at step 1/3", back.problem());
        assertFalse(Files.exists(dir.resolve("sp-new_world.json.tmp")), "no temp file left behind");
    }

    @Test
    void noFileMeansNoCampaignAndABrokenFileIsIgnored(@TempDir Path dir) throws IOException {
        CampaignStore store = new CampaignStore(dir.resolve("beat"));
        assertTrue(store.load("sp-fresh").isEmpty());
        Campaign fresh = new Campaign("sp-fresh", 5);
        store.save(fresh, 6);
        assertTrue(Files.isRegularFile(dir.resolve("beat").resolve("sp-fresh.json")), "the first save creates the folder and file");
        assertEquals(Phase.GEAR, store.load("sp-fresh").orElseThrow().phase());

        Files.writeString(store.file("sp-broken"), "{ not json");
        assertTrue(store.load("sp-broken").isEmpty());
    }
}
