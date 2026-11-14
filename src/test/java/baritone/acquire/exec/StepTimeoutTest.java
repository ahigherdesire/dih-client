package baritone.acquire.exec;

import baritone.acquire.model.Location;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StepTimeoutTest {
    @Test
    void deepDiamondGetsLongerThanASurfaceLog() {
        Step.Mine log = new Step.Mine(List.of("minecraft:oak_log"), "minecraft:oak_log", 3, ToolReq.NONE, 3);
        Step.Mine diamond = new Step.Mine(List.of("minecraft:diamond_ore"), "minecraft:diamond", 3,
                new ToolReq("pickaxe", 3, true), 3);
        assertTrue(StepTimeout.ticks(diamond, 85, 52, 300) > StepTimeout.ticks(log, 10, 2, 300));
    }

    @Test
    void configuredFloorStillAppliesToQuickSteps() {
        Step.Mine log = new Step.Mine(List.of("minecraft:oak_log"), "minecraft:oak_log", 1, ToolReq.NONE, 1);
        assertEquals(300 * 20, StepTimeout.ticks(log, 2, 0, 300));
    }

    @Test
    void endermenAreLookedForSoGetLongerThanAMobInSight() {
        Step.Kill endermen = new Step.Kill("minecraft:enderman", "minecraft:ender_pearl", 12, 24);
        Step.Kill spiders = new Step.Kill("minecraft:spider", "minecraft:string", 12, 24);
        assertTrue(StepTimeout.ticks(endermen, Double.POSITIVE_INFINITY, 0, 300)
                >= StepTimeout.ticks(spiders, Double.POSITIVE_INFINITY, 0, 300) + 24 * 20 * 20);
    }

    @Test
    void aBarterGetsTimeForEveryTrade() {
        Step.Barter barter = new Step.Barter("minecraft:piglin", "minecraft:gold_ingot", "minecraft:ender_pearl", 12, 188);
        assertTrue(StepTimeout.ticks(barter, Double.POSITIVE_INFINITY, 0, 300) >= 188 * 60);
    }

    @Test
    void castingObsidianGetsAMinutePerBlockBeyondTheTravel() {
        Step.Mine obsidian = new Step.Mine(List.of("minecraft:obsidian"), "minecraft:obsidian", 10,
                new ToolReq("pickaxe", 4, true), 10);
        assertTrue(StepTimeout.ticks(obsidian, Double.POSITIVE_INFINITY, 0, 300) >= 20 * (45 + 64 * 3) + 10 * 20 * 60);
    }

    @Test
    void castingAPortalGetsTheSameMinutePerFrameBlock() {
        Step.Travel through = new Step.Travel(Location.OVERWORLD, Location.NETHER, Map.of());
        Step.Travel cast = new Step.Travel(Location.OVERWORLD, Location.NETHER, Map.of("minecraft:cobblestone", 20));
        Step.Travel built = new Step.Travel(Location.OVERWORLD, Location.NETHER, Map.of("minecraft:obsidian", 10));
        int walk = StepTimeout.ticks(through, 10, 0, 300);
        assertEquals(walk, StepTimeout.ticks(built, 10, 0, 300));
        assertTrue(StepTimeout.ticks(cast, 10, 0, 300) >= walk + 10 * 20 * 60);
    }
}
