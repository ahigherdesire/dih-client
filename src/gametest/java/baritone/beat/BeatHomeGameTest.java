package baritone.beat;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.acquire.exec.InventoryReader;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * WP 11 part 5's acceptance, the whole first half of the game: from an empty inventory at the world spawn, {@code
 * #beat} gets its gear, casts a portal, gets 7 blaze rods and the pearls, and comes home through the same portal, so
 * the campaign reaches its eyes phase in the Overworld holding the rods and at least 12 pearls. Easy difficulty with
 * mobs on, as a player would play it. A death is counted and the player respawned (the acquire re-plans); a pause is
 * resumed as a player would, {@code DIH_BEAT_RESUMES} times at most (default 3). The time, deaths and pauses are
 * reported. {@code DIH_BEAT_SEEDS=a,b} picks the seeds and {@code DIH_BEAT_MINUTES} the limit (default 100 minutes).
 */
@SuppressWarnings("UnstableApiUsage")
public final class BeatHomeGameTest implements FabricClientGameTest {
    private static final List<String> SEEDS = List.of("8675309");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> seeds = env("DIH_BEAT_SEEDS") == null ? SEEDS : List.of(env("DIH_BEAT_SEEDS").split(","));
        int ticks = 20 * 60 * (env("DIH_BEAT_MINUTES") == null ? 100 : Integer.parseInt(env("DIH_BEAT_MINUTES")));
        int resumes = env("DIH_BEAT_RESUMES") == null ? 3 : Integer.parseInt(env("DIH_BEAT_RESUMES"));
        List<String> report = new ArrayList<>();
        try {
            for (String seed : seeds) report.add(seed(context, seed.trim(), ticks, resumes));
        } finally {
            System.out.println("[BeatHomeGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BeatCampaign beat() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getBeatCampaign();
    }

    private static String seed(ClientGameTestContext context, String seed, int ticks, int maxResumes) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(seed);
                    state.setDifficulty(Difficulty.EASY);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/clear @p");
            context.waitTicks(40);
            context.runOnClient(client -> {
                baritone.acquire.exec.PortalMemory.clear();
                beat().forget();
                try {
                    Files.deleteIfExists(beat().file());
                } catch (IOException ignored) {
                }
            });
            List<String> events = new ArrayList<>();
            // What was held the moment the trip home finished, before the eyes phase crafts anything.
            int[] home = {-1, -1};
            boolean[] overworld = {false};
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                String line = event.kind() == AcquireControl.AcquireEvent.Kind.STEP ? event.message() : event.kind() + " " + event.message();
                events.add(line);
                System.out.println("[BeatHomeGameTest] " + seed + ": " + line);
                if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE && event.item().equals("at_overworld")) {
                    home[0] = InventoryReader.count(client.player, "minecraft:blaze_rod");
                    home[1] = InventoryReader.count(client.player, "minecraft:ender_pearl");
                    overworld[0] = client.level.dimension() == Level.OVERWORLD;
                }
            }));
            long start = System.currentTimeMillis();
            long gameStart = context.computeOnClient(client -> client.level.getGameTime());
            int[] deaths = {0};
            int resumes = 0;
            List<String> pauses = new ArrayList<>();
            context.runOnClient(client -> events.add("start: " + beat().start()));
            long deadline = gameStart + ticks;
            while (true) {
                try {
                    context.waitFor(client -> {
                        if (client.player != null && client.player.isDeadOrDying() && client.gui.screen() instanceof DeathScreen) {
                            deaths[0]++;
                            events.add("died at " + client.player.blockPosition().toShortString() + ": " + beat().status());
                            client.player.respawn();
                            return false;
                        }
                        String status = beat().status();
                        if (status.startsWith("Phase 6/9")) {
                            beat().stop();
                            return true;
                        }
                        return !beat().isRunning();
                    }, (int) Math.max(1, deadline - context.computeOnClient(client -> client.level.getGameTime())));
                } catch (Throwable timeout) {
                    String status = context.computeOnClient(client -> beat().status());
                    context.runOnClient(client -> beat().stop());
                    context.takeScreenshot("beat-home-" + seed + "-timeout");
                    throw new AssertionError("seed " + seed + ": out of time at " + status + "; " + deaths[0] + " deaths, pauses "
                            + pauses + "\n" + String.join("\n", events.subList(Math.max(0, events.size() - 30), events.size())));
                }
                if (home[0] >= 0) break;
                String status = context.computeOnClient(client -> beat().status());
                pauses.add(status);
                if (++resumes > maxResumes) {
                    context.takeScreenshot("beat-home-" + seed + "-paused");
                    throw new AssertionError("seed " + seed + ": paused " + pauses.size() + " times, last at " + status + "; "
                            + deaths[0] + " deaths\n" + String.join("\n", events.subList(Math.max(0, events.size() - 30), events.size())));
                }
                context.runOnClient(client -> events.add("resume: " + beat().resume()));
            }
            context.runOnClient(client -> {
                AcquireControl.get().stop();
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            });
            double real = (System.currentTimeMillis() - start) / 1000.0;
            long game = context.computeOnClient(client -> client.level.getGameTime()) - gameStart;
            context.takeScreenshot("beat-home-" + seed);
            if (!overworld[0]) throw new AssertionError("seed " + seed + ": home phase done outside the Overworld");
            if (home[0] < Phase.BLAZE_RODS_WANTED || home[1] < 12) {
                throw new AssertionError("seed " + seed + ": home with " + home[0] + " rods and " + home[1] + " pearls");
            }
            return String.format("seed %s: home with %d rods and %d pearls after %.1f game minutes (%.0f s real), %d deaths, "
                    + "%d pauses resumed %s", seed, home[0], home[1], game / 1200.0, real, deaths[0], pauses.size(), pauses);
        }
    }
}
