package baritone.beat;

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.director.Director;
import baritone.ai.director.DirectorState;
import baritone.ai.director.RunStatus;
import baritone.api.BaritoneAPI;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dihclient.ai.ToolRunner;
import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * WP 10 in a real client: {@code #beat plan} prints the whole chain; a first {@code #beat} creates the campaign file,
 * finishes the gear phase and stops honestly at the portal; the file outlives a restart and {@code #beat resume}
 * carries on from the saved phase; {@code .ai start beat the game} runs {@code #beat} in basic mode.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BeatGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            for (String command : List.of(
                    "/difficulty peaceful",
                    "/gamerule doDaylightCycle false",
                    "/time set 3000",
                    "/execute in minecraft:overworld run fill -10 99 -10 10 99 10 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -10 100 -10 10 106 10 minecraft:air",
                    "/clear @p",
                    "/tp @p 0 100 0 -90 0")) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(20);
            try {
                plan(context);
                System.out.println("[BeatGameTest] plan ok");
                // Everything for the first two phases but the boots, which the gear phase crafts.
                for (String item : List.of("iron_helmet", "iron_chestplate", "iron_leggings", "shield", "iron_sword",
                        "iron_ingot 4", "crafting_table", "obsidian 10", "flint_and_steel")) {
                    world.getServer().runCommand("/give @p minecraft:" + item);
                }
                context.waitTicks(10);
                firstRun(context);
                System.out.println("[BeatGameTest] first run ok");
                restartAndResume(context);
                System.out.println("[BeatGameTest] resume ok");
                aiStart(context);
                System.out.println("[BeatGameTest] .ai start ok");
            } catch (Throwable t) {
                System.out.println("[BeatGameTest] FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> {
                    beat().forget();
                    try {
                        Files.deleteIfExists(beat().file());
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static BeatCampaign beat() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getBeatCampaign();
    }

    private static void plan(ClientGameTestContext context) {
        Path file = context.computeOnClient(client -> beat().file());
        if (Files.exists(file)) throw new AssertionError("a campaign file before any #beat: " + file);
        say(context, "#beat plan");
        waitForHud(context, "Phase 1/8: gear", 200);
        waitForHud(context, "Phase 8/8: the dragon", 40);
        waitForHud(context, "The whole route: ", 40);
        for (String phase : List.of("Phase 2/8: the Nether", "Phase 3/8: blaze rods", "Phase 4/8: pearls", "Phase 5/8: eyes",
                "Phase 6/8: the stronghold", "Phase 7/8: the End")) {
            waitForHud(context, phase, 5);
        }
        waitForHud(context, "build and light a nether portal", 5);
        waitForHud(context, "kill the ender dragon", 5);
        if (Files.exists(file)) throw new AssertionError("#beat plan wrote a campaign file");
        context.takeScreenshot("beat-plan");
    }

    private static void firstRun(ClientGameTestContext context) {
        say(context, "#beat");
        waitForHud(context, "#beat started. Phase 1/8: gear 5/6", 100);
        Path file = context.computeOnClient(client -> beat().file());
        if (!Files.isRegularFile(file)) throw new AssertionError("the first #beat didn't create " + file);
        // The gear phase crafts the boots, then the portal phase stops at the first step with no runner.
        waitForHud(context, "Phase 2/8: the Nether", 1200);
        waitForHud(context, "#beat paused at Phase 2/8: the Nether (the reason is above)", 600);
        waitForHud(context, "Stopped at step 1/1 (build and light a nether portal", 5);
        waitForHud(context, "going to the Nether isn't built yet", 5);
        if (context.computeOnClient(client -> beat().isRunning())) throw new AssertionError("still running after the honest stop");
        JsonObject saved = read(file);
        if (!"portal".equals(saved.get("phase").getAsString())) throw new AssertionError("saved phase: " + saved);
        if (!saved.get("problem").getAsString().contains("isn't built yet")) throw new AssertionError("saved problem: " + saved);
        if (context.computeOnClient(client -> baritone.acquire.exec.InventoryReader.count(client.player, "minecraft:iron_boots")) < 1) {
            throw new AssertionError("no boots after the gear phase");
        }
        context.takeScreenshot("beat-honest-stop");
    }

    private static void restartAndResume(ClientGameTestContext context) {
        // A restart: nothing in memory, only the file.
        context.runOnClient(client -> beat().forget());
        String status = context.computeOnClient(client -> beat().status());
        if (!status.startsWith("Phase 2/8: the Nether")) throw new AssertionError("status from the file: " + status);
        clearChat(context);
        say(context, "#beat resume");
        waitForHud(context, "#beat resumes at Phase 2/8: the Nether", 100);
        waitForHud(context, "#beat paused at Phase 2/8", 600);
        say(context, "#beat status");
        waitForHud(context, "Phase 2/8: the Nether (stopped: Stopped at step 1/1", 60);
    }

    private static void aiStart(ClientGameTestContext context) {
        context.runOnClient(client -> brain().getConfig().apiKey = "");
        if (context.computeOnClient(client -> brain().getConfig().hasKey())) {
            System.out.println("[BeatGameTest] a key is set in the environment; skipping the basic-mode check");
            return;
        }
        clearChat(context);
        say(context, DihCommands.effectivePrefix() + "ai start beat the game");
        try {
            context.waitFor(client -> {
                Director director = brain().director();
                return director != null && director.state().status() == RunStatus.PAUSED;
            }, 1200);
        } catch (Throwable t) {
            DirectorState state = context.computeOnClient(client -> brain().director().state());
            throw new AssertionError(".ai start beat the game: " + state.status() + " " + state.lastReason() + " " + state.steps());
        }
        DirectorState state = context.computeOnClient(client -> brain().director().state());
        if (state.steps().isEmpty() || !state.steps().get(0).tool().equals("beat_stage")) throw new AssertionError("steps: " + state.steps());
        if (!state.lastReason().contains("isn't built yet")) throw new AssertionError("paused because: " + state.lastReason());
        context.runOnClient(client -> brain().director().stop("test over"));
    }

    private static AiBrain brain() {
        AiBrain brain = ToolRunner.brain();
        if (brain == null) throw new AssertionError("no AI brain");
        return brain;
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("can't read " + file + ": " + e);
        }
    }

    private static void say(ClientGameTestContext context, String line) {
        context.runOnClient(client -> client.player.connection.sendChat(line));
    }

    private static void clearChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
    }

    private static List<String> hud(Minecraft client) {
        return ((DihChatComponentAccessor) client.gui.hud.getChat()).dih$getAllMessages().stream()
                .map(message -> message.content()).map(Component::getString).toList();
    }

    private static void waitForHud(ClientGameTestContext context, String text, int ticks) {
        try {
            context.waitFor(client -> hud(client).stream().anyMatch(line -> line.contains(text)), ticks);
        } catch (Throwable t) {
            throw new AssertionError("chat never showed \"" + text + "\"; it has "
                    + context.computeOnClient(client -> hud(client).stream().limit(12).toList()));
        }
    }
}
