package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Goal;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * WP 11 part 2: obsidian nobody has seen is cast. On a stone pad with a 3x3 lava pool at one end and a 2x2 water pool
 * at the other, a player with a diamond pickaxe and two empty buckets asks {@code #acquire} for 3 obsidian. It has to
 * fill a bucket with water, scoop lava, cast each block away from the pool, and leave no lava or water loose on the pad.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ObsidianCastGameTest implements FabricClientGameTest {
    private static final int WANT = 3;
    private static final int TICKS = 20 * 240;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed("8675309");
                    state.setDifficulty(Difficulty.PEACEFUL);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.OVERWORLD, -12, -12, 12, 12);
            for (String command : List.of(
                    "/gamerule advance_time false",
                    "/time set 3000",
                    "/execute in minecraft:overworld run fill -12 98 -12 12 99 12 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -12 100 -12 12 110 12 minecraft:air",
                    "/execute in minecraft:overworld run fill 6 99 -1 8 99 1 minecraft:lava",
                    "/execute in minecraft:overworld run fill -8 99 -1 -7 99 0 minecraft:water",
                    "/tp @p 0 100 0 -90 0",
                    "/clear @p",
                    "/give @p minecraft:diamond_pickaxe",
                    "/give @p minecraft:bucket 2",
                    "/give @p minecraft:cooked_beef 16")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(Items.COOKED_BEEF)));
            context.waitTicks(40);
            TestPads.expect(world, Level.OVERWORLD, new BlockPos(12, 98, 12), Blocks.SMOOTH_STONE);
            TestPads.expect(world, Level.OVERWORLD, new BlockPos(7, 99, 0), Blocks.LAVA);
            List<String> events = new ArrayList<>();
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                if (event.kind() != AcquireControl.AcquireEvent.Kind.STEP) events.add(event.kind() + " " + event.message());
                else events.add(event.message());
            }));

            long start = System.currentTimeMillis();
            boolean[] dead = {false};
            context.runOnClient(client -> events.add("start: "
                    + AcquireControl.get().startGoal(new Goal.ItemGoal("minecraft:obsidian", WANT))));
            try {
                context.waitFor(client -> {
                    if (client.player == null || client.player.isDeadOrDying()) {
                        dead[0] = true;
                        return true;
                    }
                    return !AcquireControl.get().isActive();
                }, TICKS);
            } catch (Throwable timeout) {
                events.add("timed out: " + context.computeOnClient(client -> AcquireControl.get().status()));
            } finally {
                context.runOnClient(client -> {
                    AcquireControl.get().stop();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                });
            }
            double took = (System.currentTimeMillis() - start) / 1000.0;
            if (dead[0]) fail(context, "died casting obsidian", events);
            int obsidian = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:obsidian"));
            if (obsidian < WANT) fail(context, "has " + obsidian + "/" + WANT + " obsidian", events);
            if (events.stream().noneMatch(e -> e.startsWith("DONE"))) fail(context, "acquire didn't finish", events);

            // Flowing water drains within a few seconds of its source going.
            context.waitTicks(100);
            String loose = world.getServer().computeOnServer(server -> loose(server.getLevel(Level.OVERWORLD)));
            if (!loose.isEmpty()) fail(context, "fluid left on the pad: " + loose, events);
            context.takeScreenshot("obsidian-cast-done");
            System.out.printf("[ObsidianCastGameTest]%n  cast %d obsidian in %.0f s%n  %s%n", obsidian, took,
                    String.join("\n  ", events));
        }
        context.setScreen(TitleScreen::new);
    }

    /** Lava or water on the pad outside the two pools. */
    private static String loose(ServerLevel level) {
        List<String> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(-12, 99, -12, 12, 110, 12)) {
            boolean lavaPool = pos.getY() == 99 && pos.getX() >= 6 && pos.getX() <= 8 && pos.getZ() >= -1 && pos.getZ() <= 1;
            boolean waterPool = pos.getY() == 99 && pos.getX() >= -8 && pos.getX() <= -7 && pos.getZ() >= -1 && pos.getZ() <= 0;
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.LAVA) && !lavaPool) found.add("lava " + pos.toShortString());
            else if (fluid.is(FluidTags.WATER) && !waterPool) found.add("water " + pos.toShortString());
            if (found.size() > 8) break;
        }
        return String.join(", ", found);
    }

    private static void fail(ClientGameTestContext context, String why, List<String> events) {
        context.takeScreenshot("obsidian-cast-failed");
        throw new AssertionError(why + "\n" + String.join("\n", events));
    }
}
