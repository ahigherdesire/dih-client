package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Goal;
import baritone.acquire.model.Location;
import baritone.ai.AiBrain;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WP 11 part 2: the Nether trip. With 10 obsidian, a flint and steel and 4 cobblestone, {@code #acquire} builds a
 * portal frame on flat ground, lights it and goes through; then, asked to be in the Overworld, it goes home through
 * the portal it remembered. Each seed is a fresh survival world; any death or a trip that doesn't end fails the test.
 * {@code DIH_PORTAL_SEEDS=a,b,c} changes the seeds.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PortalGameTest implements FabricClientGameTest {
    private static final List<String> SEEDS = List.of("8675309", "20260929", "-4172144997902289642");
    private static final int TRIP_TICKS = 20 * 180;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String env = System.getenv("DIH_PORTAL_SEEDS");
        List<String> seeds = env == null || env.isBlank() ? SEEDS : List.of(env.split(","));
        List<String> report = new ArrayList<>();
        try {
            for (String seed : seeds) report.add(seed(context, seed.trim()));
        } finally {
            System.out.println("[PortalGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static String seed(ClientGameTestContext context, String seed) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(seed);
                    state.setDifficulty(Difficulty.PEACEFUL);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.OVERWORLD, -10, -10, 10, 10);
            for (String command : List.of(
                    "/gamerule advance_time false",
                    "/time set 3000",
                    "/execute in minecraft:overworld run fill -10 99 -10 10 99 10 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -10 100 -10 10 110 10 minecraft:air",
                    "/tp @p 0 100 0 -90 0",
                    "/clear @p",
                    "/item replace entity @p armor.head with minecraft:iron_helmet",
                    "/item replace entity @p armor.chest with minecraft:iron_chestplate",
                    "/item replace entity @p armor.legs with minecraft:iron_leggings",
                    "/item replace entity @p armor.feet with minecraft:iron_boots",
                    "/item replace entity @p weapon.offhand with minecraft:shield",
                    "/give @p minecraft:iron_sword",
                    "/give @p minecraft:iron_pickaxe",
                    "/give @p minecraft:obsidian 10",
                    "/give @p minecraft:flint_and_steel",
                    "/give @p minecraft:cobblestone 4",
                    "/give @p minecraft:cooked_beef 16")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(net.minecraft.world.item.Items.COOKED_BEEF)));
            context.waitTicks(40);
            context.runOnClient(client -> PortalMemory.clear());
            List<String> events = new ArrayList<>();
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                if (event.kind() != AcquireControl.AcquireEvent.Kind.STEP) events.add(event.kind() + " " + event.message());
                else events.add(event.message());
            }));

            long start = System.currentTimeMillis();
            trip(context, "the_nether", Location.NETHER, events, seed, TRIP_TICKS);
            double there = (System.currentTimeMillis() - start) / 1000.0;
            String worldKey = context.computeOnClient(client -> AiBrain.currentWorldKey());
            Map<String, BlockPos> portals = context.computeOnClient(client -> PortalMemory.all(worldKey));
            BlockPos built = portals.get("overworld");
            if (built == null || portals.get("the_nether") == null) fail(context, seed, "portals remembered: " + portals, events);
            boolean lit = world.getServer().computeOnServer(server ->
                    server.getLevel(Level.OVERWORLD).getBlockState(built).is(Blocks.NETHER_PORTAL));
            if (!lit) fail(context, seed, "no lit portal at the remembered " + built.toShortString(), events);
            int obsidian = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:obsidian"));
            if (obsidian != 0) fail(context, seed, obsidian + " obsidian left over: the frame wasn't all obsidian", events);
            context.takeScreenshot("portal-" + seed + "-nether");

            start = System.currentTimeMillis();
            trip(context, "overworld", Location.OVERWORLD, events, seed, TRIP_TICKS);
            double home = (System.currentTimeMillis() - start) / 1000.0;
            double away = context.computeOnClient(client -> Math.sqrt(client.player.blockPosition().distSqr(built)));
            if (away > 8) fail(context, seed, "home " + (int) away + " blocks from the portal it built", events);
            return String.format("seed %s: built and entered in %.0f s, home in %.0f s, portal at %s", seed, there, home,
                    built.toShortString());
        }
    }

    /** Asks {@code #acquire} to be in {@code to} and waits until it is, out of the portal and done. */
    static void trip(ClientGameTestContext context, String dimension, Location to, List<String> events, String seed, int ticks) {
        boolean[] dead = {false};
        int from = events.size();
        context.runOnClient(client -> events.add("start: " + AcquireControl.get().startGoal(new Goal.AtLocation(to))));
        try {
            context.waitFor(client -> {
                if (client.player == null || client.player.isDeadOrDying()) {
                    dead[0] = true;
                    return true;
                }
                return !AcquireControl.get().isActive();
            }, ticks);
        } catch (Throwable timeout) {
            events.add("timed out: " + context.computeOnClient(client -> AcquireControl.get().status()));
        } finally {
            context.runOnClient(client -> {
                AcquireControl.get().stop();
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            });
        }
        if (dead[0]) fail(context, seed, "died on the way to " + to.label(), events);
        String here = context.computeOnClient(client -> TravelRunner.dimensionId(client.level));
        boolean inPortal = context.computeOnClient(client ->
                client.level.getBlockState(client.player.blockPosition()).is(Blocks.NETHER_PORTAL));
        if (!here.equals(dimension) || inPortal) {
            fail(context, seed, "wanted " + dimension + " out of the portal, is in " + here + (inPortal ? " (in the portal)" : ""), events);
        }
        if (events.subList(from, events.size()).stream().noneMatch(e -> e.startsWith("DONE"))) fail(context, seed, "the trip to " + to.label() + " didn't finish", events);
        events.add("--- " + dimension + " ok");
    }

    static void fail(ClientGameTestContext context, String seed, String why, List<String> events) {
        context.takeScreenshot("portal-" + seed + "-failed");
        throw new AssertionError("seed " + seed + ": " + why + "\n" + String.join("\n", events));
    }
}
