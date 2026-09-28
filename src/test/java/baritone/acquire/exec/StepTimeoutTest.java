package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
