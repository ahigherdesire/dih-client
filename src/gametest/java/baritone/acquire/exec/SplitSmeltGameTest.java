package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A big smelt split across furnaces: on a stone pad with 32 raw iron, four furnaces and planks, {@code #acquire
 * iron_ingot 32} sets one up, places the other three beside it, loads each with a share, collects the ingots and
 * takes every furnace back. In about a quarter of the 320 seconds one furnace takes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SplitSmeltGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 5 * 60 * 20;
    /** One furnace takes 32 x 200 ticks; four take 8 x 200, and placing, loading and taking back three comes on top. */
    private static final int SPLIT_TICKS = 3500;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> events = new ArrayList<>();
        boolean[] ended = {false};
        boolean[] succeeded = {false};
        long[] ticks = {0};
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            for (String command : List.of(
                    "/difficulty peaceful",
                    "/time set 3000",
                    "/clear @p",
                    "/execute in minecraft:overworld run fill -8 99 -8 8 99 8 minecraft:stone",
                    "/execute in minecraft:overworld run fill -8 100 -8 8 106 8 minecraft:air",
                    "/tp @p 0 100 0",
                    "/give @p minecraft:stone_pickaxe",
                    "/give @p minecraft:furnace 4",
                    "/give @p minecraft:raw_iron 32",
                    "/give @p minecraft:oak_planks 64")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> count(client, Items.RAW_IRON) == 32 && count(client, Items.FURNACE) == 4);
            context.waitTicks(20);
            try {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    acquire.addListener(event -> {
                        events.add(event.kind() + " " + event.message());
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE) succeeded[0] = true;
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE
                                || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED) ended[0] = true;
                    });
                    events.add("plan:\n" + acquire.plan("iron_ingot", 32));
                    events.add("start: " + acquire.start("iron_ingot", 32));
                    ticks[0] = client.level.getGameTime();
                });
                context.waitFor(client -> ended[0], TIMEOUT_TICKS);
                context.runOnClient(client -> ticks[0] = client.level.getGameTime() - ticks[0]);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                });
                context.takeScreenshot("split-smelt");
                System.out.println("[SplitSmeltGameTest] " + ticks[0] + " ticks\n  " + String.join("\n  ", events));
            }
            context.runOnClient(client -> {
                String log = "\n" + String.join("\n", events);
                if (!succeeded[0] || count(client, Items.IRON_INGOT) < 32) throw new AssertionError("short of 32 ingots" + log);
                if (events.stream().noneMatch(e -> e.contains("in 4 furnaces"))) throw new AssertionError("not split across 4 furnaces" + log);
                if (count(client, Items.FURNACE) < 4) throw new AssertionError("furnaces left behind: holds " + count(client, Items.FURNACE) + log);
                if (ticks[0] > SPLIT_TICKS) throw new AssertionError("took " + ticks[0] + " ticks, more than " + SPLIT_TICKS + log);
            });
        }
        context.setScreen(TitleScreen::new);
    }

    private static int count(Minecraft client, Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }
}
