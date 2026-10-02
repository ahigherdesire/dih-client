package baritone.combat;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.acquire.exec.TestPads;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WP 11 part 1: with M2 gear (iron armor, shield, iron sword, bow and arrows), {@code #acquire} kill steps and the
 * Guardian fight through the {@link CombatRunner}. Three scenarios, each {@link #RUNS} runs in a row, and any death
 * fails the test:
 * <ul>
 *   <li>one blaze, in a nether-brick hall in the Nether ({@code #acquire blaze_rod});</li>
 *   <li>three blazes at a blaze spawner that keeps spawning more (three kills needed);</li>
 *   <li>five zombies at night in the Overworld ({@code #acquire rotten_flesh}).</li>
 * </ul>
 * {@code DIH_COMBAT_ONLY=blaze|spawner|zombies} runs one scenario; {@code DIH_COMBAT_RUNS=n} changes the run count.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CombatGameTest implements FabricClientGameTest {
    private static final int RUNS = 5;

    private enum Scenario {
        BLAZE(EntityTypes.BLAZE, 1, "minecraft:blaze_rod", 120),
        SPAWNER(EntityTypes.BLAZE, 3, "minecraft:blaze_rod", 240),
        ZOMBIES(EntityTypes.ZOMBIE, 5, "minecraft:rotten_flesh", 150);

        final EntityType<?> mob;
        final int kills;
        final String item;
        final int seconds;

        Scenario(EntityType<?> mob, int kills, String item, int seconds) {
            this.mob = mob;
            this.kills = kills;
            this.item = item;
            this.seconds = seconds;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String only = System.getenv("DIH_COMBAT_ONLY");
        int runs = RUNS;
        try {
            runs = Math.max(1, Integer.parseInt(System.getenv().getOrDefault("DIH_COMBAT_RUNS", String.valueOf(RUNS))));
        } catch (NumberFormatException ignored) {
        }
        List<String> report = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.OVERWORLD, -24, -24, 24, 24);
            TestPads.load(context, world, Level.NETHER, -14, -14, 14, 14);
            for (String command : List.of(
                    "/difficulty normal",
                    "/gamerule spawn_mobs false",
                    "/gamerule advance_time false",
                    "/time set midnight",
                    // The Overworld field for the zombies.
                    "/execute in minecraft:overworld run fill -24 99 -24 24 99 24 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -24 100 -24 24 110 24 minecraft:air",
                    // A closed nether-brick hall in the Nether: a fortress to the planner, no lava or drops inside.
                    "/execute in minecraft:the_nether run fill -14 99 -14 14 114 14 minecraft:nether_bricks hollow")) {
                world.getServer().runCommand(command);
            }
            for (Scenario scenario : Scenario.values()) {
                if (only != null && !scenario.name().equalsIgnoreCase(only)) continue;
                for (int run = 1; run <= runs; run++) report.add(run(context, world, scenario, run));
            }
        } finally {
            System.out.println("[CombatGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static String run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario, int run) {
        String label = scenario.name().toLowerCase() + " #" + run;
        boolean nether = scenario != Scenario.ZOMBIES;
        String in = nether ? "/execute in minecraft:the_nether run " : "/execute in minecraft:overworld run ";
        for (String command : List.of(
                "/kill @e[type=!minecraft:player]",
                in + "setblock 0 100 9 minecraft:air",
                "/clear @p",
                "/effect clear @p",
                in + "tp @p 0 100 0",
                "/effect give @p minecraft:instant_health 1 10",
                "/effect give @p minecraft:saturation 1 10",
                "/item replace entity @p armor.head with minecraft:iron_helmet",
                "/item replace entity @p armor.chest with minecraft:iron_chestplate",
                "/item replace entity @p armor.legs with minecraft:iron_leggings",
                "/item replace entity @p armor.feet with minecraft:iron_boots",
                "/item replace entity @p weapon.offhand with minecraft:shield",
                "/give @p minecraft:iron_sword",
                "/give @p minecraft:bow",
                "/give @p minecraft:arrow 64",
                "/give @p minecraft:cooked_beef 16")) {
            world.getServer().runCommand(command);
        }
        context.waitFor(client -> client.player != null && client.player.getInventory().getNonEquipmentItems().stream()
                .anyMatch(stack -> stack.is(net.minecraft.world.item.Items.COOKED_BEEF)));
        context.waitTicks(40);
        switch (scenario) {
            case BLAZE -> summon(world, in, "blaze", new int[][]{{6, 103, 6}});
            case SPAWNER -> {
                world.getServer().runCommand(in + "setblock 0 100 9 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:blaze\"}}}");
                summon(world, in, "blaze", new int[][]{{-2, 102, 9}, {2, 103, 10}, {0, 104, 7}});
            }
            case ZOMBIES -> summon(world, in, "zombie", new int[][]{{8, 100, 0}, {-8, 100, 0}, {0, 100, 8}, {0, 100, -8}, {6, 100, 6}});
        }
        context.waitTicks(10);
        List<String> events = new ArrayList<>();
        Set<Integer> alive = new HashSet<>();
        Set<Integer> killed = new HashSet<>();
        boolean[] died = {false};
        int[] restarts = {0};
        int[] ticks = {0};
        float[] lowest = {20};
        int shotsBefore = context.computeOnClient(client -> combat().shots());
        long start = System.currentTimeMillis();
        context.runOnClient(client -> {
            AcquireControl acquire = AcquireControl.get();
            acquire.addListener(event -> events.add(event.kind() + " " + event.message()));
            events.add("start: " + acquire.start(scenario.item, 64));
        });
        try {
            context.waitFor(client -> {
                if (client.player == null || client.player.isDeadOrDying()) {
                    died[0] = true;
                    return true;
                }
                lowest[0] = Math.min(lowest[0], client.player.getHealth());
                if (ticks[0]++ % 20 == 0) events.add(trace(client, ticks[0] / 20));
                track(client, scenario.mob, alive, killed);
                if (killed.size() >= scenario.kills) return true;
                // The job gave up (nothing left in sight) while mobs remain: restart it, as the campaign would.
                AcquireControl acquire = AcquireControl.get();
                if (!acquire.isActive() && restarts[0]++ < 20) {
                    try {
                        events.add("restart: " + acquire.start(scenario.item, 64));
                    } catch (IllegalArgumentException e) {
                        events.add("restart refused: " + e.getMessage());
                    }
                }
                return false;
            }, scenario.seconds * 20);
        } catch (Throwable timeout) {
            events.add("timed out");
        } finally {
            context.runOnClient(client -> {
                AcquireControl.get().stop();
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                combat().release();
            });
        }
        double seconds = (System.currentTimeMillis() - start) / 1000.0;
        int shots = context.computeOnClient(client -> combat().shots()) - shotsBefore;
        String line = String.format("%s: %d/%d kills, %s, lowest %.0f HP, %d arrows, %.0f s", label, killed.size(),
                scenario.kills, died[0] ? "DIED" : "0 deaths", lowest[0], shots, seconds);
        if (died[0] || killed.size() < scenario.kills) {
            context.takeScreenshot("combat-" + scenario.name().toLowerCase() + "-" + run);
            if (died[0]) context.runOnClient(client -> client.player.respawn());
            throw new AssertionError(line + "\n" + String.join("\n", events));
        }
        return line;
    }

    /** One second of the fight, for the failure report: health, fire, the move, what the Guardian is doing. */
    private static String trace(Minecraft client, int second) {
        var player = client.player;
        net.minecraft.core.BlockPos feet = player.blockPosition();
        boolean inFire = client.level.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE);
        long fires = net.minecraft.core.BlockPos.betweenClosedStream(feet.offset(-3, -1, -3), feet.offset(3, 1, 3))
                .filter(p -> client.level.getBlockState(p).is(net.minecraft.tags.BlockTags.FIRE)).count();
        long blazes = client.level.getEntities(player, player.getBoundingBox().inflate(24), e -> e.getType() == EntityTypes.BLAZE).size();
        return String.format("%3ds %4.1f HP%s%s fires %d blazes %d move %s | %s", second, player.getHealth(),
                player.isOnFire() ? " burning" : "", inFire ? " IN FIRE" : "", fires, blazes, combat().lastMove(),
                ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getGuardianProcess().status());
    }

    private static void summon(TestSingleplayerContext world, String in, String mob, int[][] spots) {
        for (int[] at : spots) {
            world.getServer().runCommand(in + "summon minecraft:" + mob + " " + at[0] + " " + at[1] + " " + at[2] + " {PersistenceRequired:1b}");
        }
    }

    /** Counts a mob as killed once it was seen alive and then died or vanished near the player. */
    private static void track(Minecraft client, EntityType<?> type, Set<Integer> alive, Set<Integer> killed) {
        Set<Integer> now = new HashSet<>();
        for (Entity e : client.level.entitiesForRendering()) {
            if (e.getType() == type && e instanceof LivingEntity mob && !mob.isDeadOrDying() && e.distanceTo(client.player) < 48) {
                now.add(e.getId());
            }
        }
        for (Integer id : alive) if (!now.contains(id)) killed.add(id);
        alive.clear();
        alive.addAll(now);
    }

    private static CombatRunner combat() {
        return CombatRunner.of((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone());
    }
}
