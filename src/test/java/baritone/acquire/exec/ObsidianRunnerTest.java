package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObsidianRunnerTest {

    private static Step.Mine mine(String item) {
        return new Step.Mine(List.of(item), item, 10, ToolReq.NONE, 10);
    }

    @Test
    void twoBucketsOfAnyKindCastObsidian() {
        Step.Mine obsidian = mine("minecraft:obsidian");
        assertTrue(ObsidianRunner.supports(obsidian, 2, 0, 0));
        assertTrue(ObsidianRunner.supports(obsidian, 1, 1, 0));
        assertTrue(ObsidianRunner.supports(obsidian, 0, 1, 1));
    }

    @Test
    void oneBucketMinesObsidianWhereItLies() {
        assertFalse(ObsidianRunner.supports(mine("minecraft:obsidian"), 1, 0, 0));
        assertFalse(ObsidianRunner.supports(mine("minecraft:obsidian"), 0, 0, 0));
    }

    @Test
    void onlyObsidianIsCast() {
        assertFalse(ObsidianRunner.supports(mine("minecraft:stone"), 2, 0, 0));
    }
}
