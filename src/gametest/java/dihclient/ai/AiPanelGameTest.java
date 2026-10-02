package dihclient.ai;

import baritone.ai.AiBrain;
import baritone.ai.AiConfig;
import baritone.ai.ChatModel;
import baritone.ai.LlmClient;
import baritone.ai.director.Director;
import baritone.ai.director.DirectorState;
import baritone.ai.director.RunStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dihclient.commands.DihCommands;
import dihclient.gui.screen.DihAiSetupScreen;
import dihclient.gui.screen.DihStyledButton;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.util.DihAiPanelOverlay;
import dihclient.util.DihConfig;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihUiScale;
import dihclient.util.DihWindowLayout;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * WP 09 in a real client: {@code .ai setup} tests a provider (a local fake one) and says what's wrong in plain words,
 * then saves; {@code .ai start} opens the AI panel, whose Pause / Resume / Why? / Stop buttons work mid-run, which
 * pins, and whose confirm / deny answer a step waiting for the player's OK.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AiPanelGameTest implements FabricClientGameTest {

    private static final String GOOD_KEY = "sk-good-0123456789abcdefGOOD";
    private static final String BAD_KEY = "sk-wrong-0123456789abcdefBAD1";

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
                    "/execute in minecraft:overworld run fill 3 100 -2 5 104 2 minecraft:oak_log",
                    "/clear @p",
                    "/tp @p 0 100 0 -90 0")) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(20);
            AiConfig saved = context.computeOnClient(client -> copy(brain().getConfig()));
            try {
                setup(context);
                System.out.println("[AiPanelGameTest] setup ok");
                restore(context, saved);
                panel(context);
                System.out.println("[AiPanelGameTest] panel ok");
                confirmAndDeny(context);
                System.out.println("[AiPanelGameTest] confirm / deny ok");
            } catch (Throwable t) {
                System.out.println("[AiPanelGameTest] FAILED: " + t);
                throw t;
            } finally {
                restore(context, saved);
                context.runOnClient(client -> {
                    DihAiPanelOverlay.get().setPinned(false);
                    DihAiPanelOverlay.get().setVisible(false);
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }

    // ---------------------------------------------------------------- setup screen

    /** A provider that knows one key and one model. */
    private static HttpServer fakeProvider() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            int status;
            String reply;
            if (auth == null || !auth.equals("Bearer " + GOOD_KEY)) {
                status = 401;
                reply = "{\"error\":{\"message\":\"Incorrect API key provided: " + (auth == null ? "" : auth.substring(7)) + "\"}}";
            } else if (body.contains("\"model\":\"missing\"")) {
                status = 404;
                reply = "{\"error\":{\"message\":\"The model `missing` does not exist\",\"code\":\"model_not_found\"}}";
            } else {
                status = 200;
                reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":5}}";
            }
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        server.start();
        return server;
    }

    private static void setup(ClientGameTestContext context) {
        HttpServer server;
        try {
            server = fakeProvider();
        } catch (IOException e) {
            throw new AssertionError("no fake provider: " + e);
        }
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            say(context, DihCommands.effectivePrefix() + "ai setup");
            context.waitFor(client -> client.gui.screen() instanceof DihAiSetupScreen, 100);
            context.waitTicks(3);

            fill(context, url, "fake-model", "");
            context.takeScreenshot("ai-setup-nokey");

            fill(context, url, "fake-model", BAD_KEY);
            expectTest(context, "Key rejected."::equals);
            fill(context, url, "missing", GOOD_KEY);
            expectTest(context, "Model not found: missing."::equals);
            int dead = freePort();
            fill(context, "http://127.0.0.1:" + dead + "/v1", "fake-model", GOOD_KEY);
            expectTest(context, ("Can't reach 127.0.0.1:" + dead + ".")::equals);
            fill(context, url, "fake-model", GOOD_KEY);
            expectTest(context, result -> result.startsWith("Works ("));
            context.takeScreenshot("ai-setup-works");

            click(context, "Save");
            AiConfig now = context.computeOnClient(client -> copy(brain().getConfig()));
            if (!url.equals(now.baseUrl) || !"fake-model".equals(now.model) || !GOOD_KEY.equals(now.apiKey)) {
                throw new AssertionError("Save didn't keep the settings: " + now.baseUrl + " " + now.model);
            }
            context.setScreen(() -> null);
            List<String> chat = context.computeOnClient(AiPanelGameTest::hud);
            for (String line : chat) {
                if (line.contains(GOOD_KEY) || line.contains(BAD_KEY)) throw new AssertionError("a key showed in chat: " + line);
            }
        } finally {
            server.stop(0);
        }
    }

    private static int freePort() {
        try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return free.getLocalPort();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The setup screen's three boxes are URL, model and key, in that order. */
    private static void fill(ClientGameTestContext context, String url, String model, String key) {
        context.runOnClient(client -> {
            List<EditBox> boxes = client.gui.screen().children().stream()
                    .filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            if (boxes.size() != 3) throw new AssertionError("setup screen boxes: " + boxes.size());
            boxes.get(0).setValue(url);
            boxes.get(1).setValue(model);
            boxes.get(2).setValue(key);
        });
    }

    /** Clicks the Test button with the mouse and waits for its answer. */
    private static void expectTest(ClientGameTestContext context, Predicate<String> expected) {
        click(context, "Test");
        try {
            context.waitFor(client -> {
                String result = result(client);
                return !result.startsWith("Checking") && !result.isEmpty() && !result.startsWith("Use your") && !result.startsWith("Local");
            }, 400);
        } catch (Throwable t) {
            throw new AssertionError("Test never answered: " + context.computeOnClient(AiPanelGameTest::result));
        }
        String result = context.computeOnClient(AiPanelGameTest::result);
        if (!expected.test(result)) throw new AssertionError("Test said: " + result);
        System.out.println("[AiPanelGameTest] Test said: " + result);
    }

    private static String result(Minecraft client) {
        try {
            Field field = DihAiSetupScreen.class.getDeclaredField("result");
            field.setAccessible(true);
            return String.valueOf(field.get(client.gui.screen()));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** A real mouse click on the setup screen's button labelled {@code label}. */
    private static void click(ClientGameTestContext context, String label) {
        int[] center = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof DihStyledButton button && button.getMessage().getString().equals(label)) {
                    return new int[]{button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
                }
            }
            throw new AssertionError("no " + label + " button");
        });
        clickAt(context, center[0], center[1]);
    }

    private static void clickAt(ClientGameTestContext context, int virtualX, int virtualY) {
        context.getInput().setCursorPos(DihUiScale.virtualToFramebufferX(virtualX), DihUiScale.virtualToFramebufferY(virtualY));
        context.getInput().pressMouse(0);
        context.waitTicks(2);
    }

    // ---------------------------------------------------------------- panel

    private static void panel(ClientGameTestContext context) {
        context.runOnClient(client -> brain().getConfig().apiKey = "");
        if (context.computeOnClient(client -> brain().getConfig().hasKey())) {
            System.out.println("[AiPanelGameTest] a key is set in the environment; the run may use the smart director");
        }
        context.runOnClient(client -> DihAiPanelOverlay.get().setVisible(false));
        say(context, DihCommands.effectivePrefix() + "ai start get 60 oak logs");
        context.waitFor(client -> DihAiPanelOverlay.isOpen(), 100);
        waitForStatus(context, status -> status == RunStatus.RUNNING || status == RunStatus.WAITING, 400);
        context.waitTicks(40);
        context.takeScreenshot("ai-panel-running");

        // Buttons are clicked with the chat open, where windows take the mouse.
        openChat(context);
        pressPanel(context, 0);
        waitForStatus(context, status -> status == RunStatus.PAUSED, 100);
        context.takeScreenshot("ai-panel-paused");
        pressPanel(context, 0);
        waitForStatus(context, status -> status != RunStatus.PAUSED, 100);
        pressPanel(context, 2);
        waitForHud(context, "Why:", 60);

        // Pinned, it outlives the chat screen and stays on screen in-game.
        context.runOnClient(client -> {
            DihAiPanelOverlay panel = DihAiPanelOverlay.get();
            panel.setPinned(true);
            DihOverlayManager.get().clear();
            if (!DihOverlayManager.get().getOverlays().contains(panel) || !panel.isVisible()) {
                throw new AssertionError("the pinned panel was cleared");
            }
        });
        context.setScreen(() -> null);
        context.waitTicks(20);
        context.takeScreenshot("ai-panel-pinned-ingame");

        openChat(context);
        pressPanel(context, 1);
        waitForStatus(context, status -> status == RunStatus.STOPPED, 100);
        context.setScreen(() -> null);
        System.out.println("[AiPanelGameTest] panel checklist: " + context.computeOnClient(client -> state().steps()));
    }

    /** Plays back one plan: whisper to yourself, a step that waits for the player's OK. */
    private static final class WhisperModel implements ChatModel {
        final String player;
        int calls;

        WhisperModel(String player) {
            this.player = player;
        }

        @Override
        public LlmClient.Reply chat(JsonArray messages, JsonArray tools) {
            this.calls++;
            JsonObject raw = new JsonObject();
            raw.addProperty("role", "assistant");
            String plan = "{\"summary\":\"say hi\",\"steps\":[{\"tool\":\"whisper\",\"args\":{\"player\":\"" + this.player
                    + "\",\"message\":\"hi from the panel test\"},\"reason\":\"the test asked to\"}]}";
            return new LlmClient.Reply("", List.of(new LlmClient.ToolCall("call_" + this.calls, "submit_plan",
                    JsonParser.parseString(plan).getAsJsonObject())), raw);
        }
    }

    private static void confirmAndDeny(ClientGameTestContext context) {
        String player = context.computeOnClient(client -> client.player.getName().getString());

        // Panel open: Deny stops the run.
        context.runOnClient(client -> DihAiPanelOverlay.open());
        startScripted(context, new WhisperModel(player));
        context.waitFor(client -> state().awaitingConfirm(), 200);
        openChat(context);
        context.takeScreenshot("ai-panel-confirm");
        pressPanel(context, 4);
        waitForStatus(context, status -> status == RunStatus.STOPPED, 100);
        if (!state().lastReason().contains("denied whisper")) throw new AssertionError("deny: " + state().lastReason());

        // Panel open: Confirm lets the step run.
        startScripted(context, new WhisperModel(player));
        context.waitFor(client -> state().awaitingConfirm(), 200);
        pressPanel(context, 3);
        waitForStatus(context, status -> status == RunStatus.DONE, 200);
        context.setScreen(() -> null);

        // Panel closed: the question goes to chat with a clickable [confirm].
        context.runOnClient(client -> DihAiPanelOverlay.get().setVisible(false));
        startScripted(context, new WhisperModel(player));
        context.waitFor(client -> state().awaitingConfirm(), 200);
        waitForHud(context, "[confirm] [deny]", 60);
        context.runOnClient(client -> {
            if (!AiConfirmPrompt.handleClick("/" + AiConfirmPrompt.CONFIRM)) throw new AssertionError("[confirm] wasn't ours");
        });
        waitForStatus(context, status -> status == RunStatus.DONE, 200);
        if (context.computeOnClient(client -> DihAiPanelOverlay.isOpen())) throw new AssertionError("the panel reopened itself");
    }

    private static void startScripted(ClientGameTestContext context, ChatModel model) {
        String error = context.computeOnClient(client -> brain().startRun("say hi to myself", model));
        if (error != null) throw new AssertionError(error);
    }

    // ---------------------------------------------------------------- helpers

    private static AiBrain brain() {
        AiBrain brain = ToolRunner.brain();
        if (brain == null) throw new AssertionError("no AI brain");
        return brain;
    }

    private static DirectorState state() {
        Director director = brain().director();
        return director == null ? DirectorState.IDLE : director.state();
    }

    private static AiConfig copy(AiConfig config) {
        AiConfig copy = new AiConfig();
        copy.baseUrl = config.baseUrl;
        copy.model = config.model;
        copy.apiKey = config.apiKey;
        return copy;
    }

    private static void restore(ClientGameTestContext context, AiConfig saved) {
        context.runOnClient(client -> {
            AiConfig config = brain().getConfig();
            config.baseUrl = saved.baseUrl;
            config.model = saved.model;
            config.apiKey = saved.apiKey;
            config.save();
        });
    }

    private static void openChat(ClientGameTestContext context) {
        context.setScreen(() -> new ChatScreen("", false));
        context.waitTicks(3);
    }

    /** A real click on panel button {@code index}: pause-or-resume, stop, why, confirm, deny. */
    private static void pressPanel(ClientGameTestContext context, int index) {
        DihWindowLayout bounds = context.computeOnClient(client -> DihAiPanelOverlay.get().getBounds());
        clickAt(context, bounds.x + 6 + index * 58 + 27, bounds.y + bounds.height - 21 + 7);
    }

    private static void waitForStatus(ClientGameTestContext context, Predicate<RunStatus> wanted, int ticks) {
        try {
            context.waitFor(client -> wanted.test(state().status()), ticks);
        } catch (Throwable t) {
            DirectorState state = context.computeOnClient(client -> state());
            throw new AssertionError("run is " + state.status() + ": " + state.lastReason());
        }
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

    private static void say(ClientGameTestContext context, String line) {
        context.runOnClient(client -> client.player.connection.sendChat(line));
    }
}
