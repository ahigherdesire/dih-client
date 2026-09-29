package dihclient.ai;

import baritone.ai.AiBrain;
import baritone.ai.ChatModel;
import baritone.ai.LlmClient;
import baritone.ai.director.Director;
import baritone.ai.director.DirectorState;
import baritone.ai.director.RunStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * WP 08 in a real client: {@code .ai start} in basic mode with no key gets oak logs; a scripted model's plan fails in
 * the game (an item nothing drops) and is re-planned once, as the journal shows; {@code #ai} still answers.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AiRunGameTest implements FabricClientGameTest {

    /** Plays back plans; counts calls. */
    private static final class ScriptedModel implements ChatModel {
        final Deque<String> plans = new ArrayDeque<>();
        int calls;

        @Override
        public LlmClient.Reply chat(JsonArray messages, JsonArray tools) throws IOException {
            this.calls++;
            String plan = this.plans.size() > 1 ? this.plans.poll() : this.plans.peek();
            if (plan == null) throw new IOException("no more plans");
            JsonObject raw = new JsonObject();
            raw.addProperty("role", "assistant");
            return new LlmClient.Reply("", List.of(new LlmClient.ToolCall("call_" + this.calls, "submit_plan",
                    JsonParser.parseString(plan).getAsJsonObject())), raw);
        }
    }

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
                    "/execute in minecraft:overworld run fill 3 100 -2 5 101 2 minecraft:oak_log",
                    "/clear @p",
                    "/tp @p 0 100 0 -90 0")) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(20);
            try {
                basicMode(context);
                System.out.println("[AiRunGameTest] basic mode ok");
                replan(context);
                System.out.println("[AiRunGameTest] re-plan ok");
                oldCommand(context);
                System.out.println("[AiRunGameTest] #ai ok");
            } catch (Throwable t) {
                System.out.println("[AiRunGameTest] FAILED: " + t);
                throw t;
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static AiBrain brain() {
        AiBrain brain = ToolRunner.brain();
        if (brain == null) throw new AssertionError("no AI brain");
        return brain;
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
                    + context.computeOnClient(client -> hud(client).stream().limit(10).toList()));
        }
    }

    private static DirectorState waitForEnd(ClientGameTestContext context, int ticks) {
        try {
            context.waitFor(client -> {
                Director director = brain().director();
                return director != null && director.state().status().isOver();
            }, ticks);
        } catch (Throwable t) {
            DirectorState state = brain().director().state();
            throw new AssertionError("the run didn't end: " + state.status() + " " + state.lastReason() + " " + state.steps());
        }
        return brain().director().state();
    }

    private static int logs(Minecraft client) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(Items.OAK_LOG)).mapToInt(stack -> stack.getCount()).sum();
    }

    private static void say(ClientGameTestContext context, String line) {
        context.runOnClient(client -> client.player.connection.sendChat(line));
    }

    private static void basicMode(ClientGameTestContext context) {
        context.runOnClient(client -> brain().getConfig().apiKey = "");
        if (context.computeOnClient(client -> brain().getConfig().hasKey())) {
            System.out.println("[AiRunGameTest] a key is set in the environment; basic mode is checked with a null model");
        }
        say(context, DihCommands.effectivePrefix() + "ai start get 10 oak logs");
        waitForHud(context, "Basic mode", 100);
        DirectorState state = waitForEnd(context, 3600);
        if (state.status() != RunStatus.DONE) throw new AssertionError("basic run ended " + state.status() + ": " + state.lastReason());
        if (!state.basic() || state.modelCalls() != 0) throw new AssertionError("not basic: " + state);
        int logs = context.computeOnClient(AiRunGameTest::logs);
        if (logs < 10) throw new AssertionError("only " + logs + " oak logs");
        say(context, DihCommands.effectivePrefix() + "ai status");
        waitForHud(context, "✔ 1. acquire", 60);
        context.takeScreenshot("ai-run-basic");
    }

    private static void replan(ClientGameTestContext context) {
        ScriptedModel model = new ScriptedModel();
        model.plans.add("""
                {"summary":"bedrock first","steps":[{"tool":"acquire","args":{"item":"bedrock","count":1},"reason":"asked"}]}""");
        model.plans.add("""
                {"summary":"logs instead","steps":[{"tool":"acquire","args":{"item":"oak_log","count":12},"reason":"bedrock can't be got"}]}""");
        String error = context.computeOnClient(client -> brain().startRun("get bedrock, or else more logs", model));
        if (error != null) throw new AssertionError(error);
        DirectorState state = waitForEnd(context, 3600);
        if (state.status() != RunStatus.DONE) throw new AssertionError("run ended " + state.status() + ": " + state.lastReason());
        if (model.calls != 2 || state.modelCalls() != 2) throw new AssertionError("model calls: " + model.calls);
        if (state.planVersion() != 2) throw new AssertionError("plans: " + state.planVersion());

        JsonObject journal = newestJournal(context);
        if (journal.getAsJsonArray("plans").size() != 2) throw new AssertionError("journal plans: " + journal);
        JsonObject second = journal.getAsJsonArray("plans").get(1).getAsJsonObject();
        if (!second.has("because") || !second.get("because").getAsString().contains("bedrock")) {
            throw new AssertionError("the re-plan doesn't say why: " + second);
        }
        if (!"done".equals(journal.get("outcome").getAsString())) throw new AssertionError("outcome: " + journal.get("outcome"));
        System.out.println("[AiRunGameTest] journal: " + journal.get("plans").getAsJsonArray().size() + " plans, because "
                + second.get("because").getAsString());
    }

    private static JsonObject newestJournal(ClientGameTestContext context) {
        Path runs = context.computeOnClient(client -> brain().getBaritone().getDirectory().resolve("ai_runs"));
        try (Stream<Path> files = Files.list(runs)) {
            Path newest = files.filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.toFile().lastModified())).orElseThrow();
            return JsonParser.parseString(Files.readString(newest, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("no run journal in " + runs + ": " + e);
        }
    }

    private static void oldCommand(ClientGameTestContext context) {
        say(context, "#ai status");
        waitForHud(context, "DIH Client brain", 60);
    }
}
