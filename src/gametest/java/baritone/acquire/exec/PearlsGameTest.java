package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * WP 11 part 4: pearls, both ways. {@code endermen}: at night on an open pad on the ground in the Overworld with Nether gear and no
 * gold, {@code #acquire ender_pearl 12} hunts the endermen about until it holds 12. {@code barter}: in a glass pen in
 * the Nether with six piglins, 256 gold ingots and a pair of gold boots carried but not worn, {@code #acquire
 * ender_pearl 2} plans a barter, puts the boots on, throws the piglins gold and picks up the pearls; no piglin is
 * hurt or turns on the player. Easy difficulty (neither mob stays on peaceful); a death fails the scenario.
 * {@code DIH_PEARLS_SCENARIOS=barter} runs one.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PearlsGameTest implements FabricClientGameTest {
    private static final String SEED = "8675309";
    private static final int PIGLINS = 6;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String env = System.getenv("DIH_PEARLS_SCENARIOS");
        List<String> scenarios = env == null || env.isBlank() ? List.of("barter", "endermen") : List.of(env.split(","));
        List<String> report = new ArrayList<>();
        try {
            for (String scenario : scenarios) {
                report.add(switch (scenario.trim()) {
                    case "barter" -> barter(context);
                    case "endermen" -> endermen(context);
                    default -> throw new IllegalArgumentException("no pearls scenario " + scenario);
                });
            }
        } finally {
            System.out.println("[PearlsGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static TestSingleplayerContext world(ClientGameTestContext context) {
        return context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(SEED);
                    state.setDifficulty(Difficulty.EASY);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create();
    }

    private static final List<String> GEAR = List.of(
            "/clear @p",
            "/item replace entity @p armor.head with minecraft:iron_helmet",
            "/item replace entity @p armor.chest with minecraft:iron_chestplate",
            "/item replace entity @p armor.legs with minecraft:iron_leggings",
            "/item replace entity @p armor.feet with minecraft:iron_boots",
            "/item replace entity @p weapon.offhand with minecraft:shield",
            "/give @p minecraft:iron_sword",
            "/give @p minecraft:cooked_beef 32");

    private static String barter(ClientGameTestContext context) {
        try (TestSingleplayerContext world = world(context)) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.NETHER, -9, -9, 9, 9);
            List<String> commands = new ArrayList<>(List.of(
                    "/gamerule spawn_mobs false",
                    // A sealed glass pen with a stone floor, well above the lava sea.
                    "/execute in minecraft:the_nether run fill -9 99 -9 9 107 9 minecraft:glass hollow",
                    "/execute in minecraft:the_nether run fill -8 99 -8 8 99 8 minecraft:smooth_stone",
                    "/execute in minecraft:the_nether run tp @p 0 100 0 0 0"));
            commands.addAll(GEAR);
            commands.add("/give @p minecraft:gold_ingot 256");
            commands.add("/give @p minecraft:golden_boots");
            for (String command : commands) world.getServer().runCommand(command);
            context.waitFor(client -> client.level != null && TravelRunner.dimensionId(client.level).equals("the_nether")
                    && client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> stack.is(Items.GOLDEN_BOOTS)));
            context.waitTicks(40);
            TestPads.expect(world, Level.NETHER, new BlockPos(8, 99, 8), Blocks.SMOOTH_STONE);
            // The piglins come last, just before the acquire starts: with no gold on, they'd go for the player.
            // Persistent so they stay.
            int[][] spots = {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}, {0, 6}, {0, -6}};
            for (int[] spot : spots) {
                world.getServer().runCommand("/execute in minecraft:the_nether run summon minecraft:piglin " + spot[0]
                        + " 100 " + spot[1] + " {PersistenceRequired:1b}");
            }
            List<String> events = listen(context, "barter");
            boolean dead = run(context, "ender_pearl", 2, 20 * 60 * 8, events);
            if (dead) PortalGameTest.fail(context, "barter", "died bartering", events);
            int pearls = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:ender_pearl"));
            int gold = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:gold_ingot"));
            boolean boots = context.computeOnClient(client -> client.player.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS));
            long piglins = context.computeOnClient(client -> client.level.getEntitiesOfClass(Piglin.class,
                    client.player.getBoundingBox().inflate(32), p -> p.isAlive() && p.getHealth() >= p.getMaxHealth()).size());
            float health = context.computeOnClient(client -> client.player.getHealth());
            context.takeScreenshot("pearls-barter");
            if (events.stream().noneMatch(line -> line.contains("barter ~"))) PortalGameTest.fail(context, "barter", "no barter step planned", events);
            if (pearls < 2) PortalGameTest.fail(context, "barter", pearls + "/2 pearls", events);
            if (!boots) PortalGameTest.fail(context, "barter", "the gold boots aren't worn", events);
            if (piglins != PIGLINS) PortalGameTest.fail(context, "barter", piglins + "/" + PIGLINS + " piglins unhurt", events);
            if (health < 16) PortalGameTest.fail(context, "barter", "hurt while bartering: " + health + " health", events);
            return String.format("barter: %d pearls for %d gold ingots, %d piglins unhurt, %.0f health, 0 deaths",
                    pearls, 256 - gold, piglins, health);
        }
    }

    private static String endermen(ClientGameTestContext context) {
        try (TestSingleplayerContext world = world(context)) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.OVERWORLD, -32, -32, 32, 32);
            // On the ground, not up in the air: an enderman hit teleports, and one that lands below a raised pad is out
            // of reach of a player with no blocks to climb back with, which no real hunt runs into.
            int y = world.getServer().computeOnServer(server -> server.getLevel(Level.OVERWORLD)
                    .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0));
            List<String> commands = new ArrayList<>(List.of(
                    "/gamerule advance_time false",
                    "/gamerule advance_weather false",
                    "/gamerule spawn_mobs false",
                    "/weather clear",
                    "/time set 18000",
                    "/execute in minecraft:overworld run fill -32 " + (y - 2) + " -32 32 " + (y - 1) + " 32 minecraft:smooth_stone"));
            // In slices: one fill may change at most 32768 blocks, and a single one over the whole pad was turned down,
            // leaving the trees and hills standing.
            for (int slice = 0; slice < 4; slice++) {
                commands.add("/execute in minecraft:overworld run fill -32 " + (y + slice * 4) + " -32 32 "
                        + (y + slice * 4 + 3) + " 32 minecraft:air");
            }
            commands.add("/tp @p 0 " + y + " 0 0 0");
            commands.addAll(GEAR);
            // 40 of them 9 blocks apart, none within 9 of the start: still more than a warped forest holds, but each
            // apart enough to be fought on its own.
            for (int x = -27; x <= 27; x += 9) {
                for (int z = -27; z <= 27; z += 9) {
                    if (Math.abs(x) < 10 && Math.abs(z) < 10) continue;
                    commands.add("/summon minecraft:enderman " + x + " " + y + " " + z + " {PersistenceRequired:1b}");
                }
            }
            for (String command : commands) world.getServer().runCommand(command);
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> stack.is(Items.COOKED_BEEF)));
            context.waitTicks(40);
            List<String> events = listen(context, "endermen");
            long start = context.computeOnClient(client -> client.level.getGameTime());
            boolean dead = run(context, "ender_pearl", 12, 20 * 60 * 15, events);
            if (dead) PortalGameTest.fail(context, "endermen", "died hunting endermen", events);
            int pearls = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:ender_pearl"));
            float health = context.computeOnClient(client -> client.player.getHealth());
            long game = context.computeOnClient(client -> client.level.getGameTime()) - start;
            context.takeScreenshot("pearls-endermen");
            if (pearls < 12) PortalGameTest.fail(context, "endermen", pearls + "/12 pearls", events);
            return String.format("endermen: %d pearls in %.1f game minutes, %.0f health left, 0 deaths", pearls, game / 1200.0, health);
        }
    }

    private static List<String> listen(ClientGameTestContext context, String scenario) {
        List<String> events = new ArrayList<>();
        context.runOnClient(client -> AcquireControl.get().addListener(event -> {
            String line = event.kind() == AcquireControl.AcquireEvent.Kind.STEP ? event.message() : event.kind() + " " + event.message();
            events.add(line);
            System.out.println("[PearlsGameTest] " + scenario + ": " + line);
        }));
        return events;
    }

    /** Acquires {@code count} of {@code item} until it ends or {@code ticks} pass; true if the player died. */
    private static boolean run(ClientGameTestContext context, String item, int count, int ticks, List<String> events) {
        boolean[] dead = {false};
        context.runOnClient(client -> events.add("start: " + AcquireControl.get().start(item, count)));
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
        return dead[0];
    }
}
