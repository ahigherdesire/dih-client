package baritone.acquire.exec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineRunnerTest {

    /**
     * From a game test: the planner chose iron blocks seen in a structure, there was no way to them, and every re-plan
     * chose them again until the run gave up. Blocks mined for themselves that gave nothing are left out for the run.
     */
    @Test
    void aPlacedBlockWithNoWayToItIsLeftOut() {
        assertTrue(MineRunner.unreachable(0, 0, "minecraft:iron_block", List.of("minecraft:iron_block")));
    }

    @Test
    void someProgressOrOreKeepsIt() {
        assertFalse(MineRunner.unreachable(0, 2, "minecraft:iron_block", List.of("minecraft:iron_block")), "mined some");
        assertFalse(MineRunner.unreachable(0, 0, "minecraft:raw_iron", List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
                "ore lies everywhere; another spot may do");
        assertFalse(MineRunner.unreachable(-1, 0, "minecraft:iron_block", List.of("minecraft:iron_block")), "never started");
    }
}
