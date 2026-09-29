package dihclient.ai;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import com.mojang.brigadier.suggestion.Suggestion;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.util.DihConfig;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.macro.AiToolAction;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.macro.SendChatAction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WP 06 in a real client: {@code .tools} in chat (results, argument errors, tab-complete), a dangerous tool's
 * [confirm] link, and an AI_TOOL macro step that saves, reloads and reports {@code tool_status}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ToolsGameTest implements FabricClientGameTest {
    private static final List<String> SERVER_CHAT = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger DANGER_RUNS = new AtomicInteger();

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> SERVER_CHAT.add(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> SERVER_CHAT.add(message.getString()));
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            for (String command : List.of(
                    "/difficulty peaceful",
                    "/gamerule doDaylightCycle false",
                    "/time set 3000",
                    "/execute in minecraft:overworld run fill -6 99 -6 6 99 6 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run setblock 4 100 0 minecraft:oak_log",
                    "/tp @p 0 100 0 -90 0")) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(20);
            try {
                chatTools(context);
                System.out.println("[ToolsGameTest] chat tools ok");
                tabComplete(context);
                System.out.println("[ToolsGameTest] tab complete ok");
                confirmation(context);
                System.out.println("[ToolsGameTest] confirmation ok");
                macroStep(context);
                System.out.println("[ToolsGameTest] macro step ok");
            } catch (Throwable t) {
                System.out.println("[ToolsGameTest] FAILED: " + t);
                throw t;
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static void say(ClientGameTestContext context, String line) {
        context.runOnClient(client -> client.player.connection.sendChat(line));
    }

    private static List<Component> hud(Minecraft client) {
        return ((DihChatComponentAccessor) client.gui.hud.getChat()).dih$getAllMessages().stream()
                .map(message -> message.content()).toList();
    }

    private static boolean hudHas(Minecraft client, String text) {
        return hud(client).stream().anyMatch(line -> line.getString().contains(text));
    }

    private static void waitForHud(ClientGameTestContext context, String text) {
        try {
            context.waitFor(client -> hudHas(client, text), 200);
        } catch (Throwable t) {
            String recent = context.computeOnClient(client -> hud(client).stream().limit(8).map(Component::getString).toList().toString());
            throw new AssertionError("chat never showed \"" + text + "\"; it has " + recent);
        }
    }

    private static void chatTools(ClientGameTestContext context) {
        String dot = DihCommands.effectivePrefix();
        say(context, dot + "tools look_around");
        waitForHud(context, "look_around ok");
        say(context, dot + "tools find oak_log");
        waitForHud(context, "Nearest oak_log");
        context.takeScreenshot("tools-chat");
        say(context, dot + "tools wait 99");
        waitForHud(context, "seconds must be at most 30");
        say(context, dot + "tools nosuchtool");
        waitForHud(context, "No tool named nosuchtool");
    }

    private static List<String> complete(Minecraft client, String typed) {
        var dispatcher = DihCommands.dispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(typed, DihCommandSource.INSTANCE)).join()
                .getList().stream().map(Suggestion::getText).toList();
    }

    private static void tabComplete(ClientGameTestContext context) {
        context.runOnClient(client -> {
            List<String> tools = complete(client, "tools lo");
            if (!tools.contains("look_around") || !tools.contains("load_tools")) throw new AssertionError("tool names: " + tools);
            List<String> categories = complete(client, "tools load_tools co");
            if (!categories.equals(List.of("combat"))) throw new AssertionError("enum values: " + categories);
            List<String> keyed = complete(client, "tools list_tools category=mi");
            if (!keyed.equals(List.of("mining"))) throw new AssertionError("key=value values: " + keyed);
        });
    }

    private static void confirmation(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (ToolRegistry.standard().get("gametest_danger") == null) {
                ToolRegistry.standard().register(AiTool.builder("gametest_danger", ToolCategory.CLIENT)
                        .summary("A test tool that can't be undone.")
                        .dangerous()
                        .handler((ctx, args) -> {
                            DANGER_RUNS.incrementAndGet();
                            return ToolResult.ok("danger ran");
                        })
                        .build());
            }
        });
        say(context, DihCommands.effectivePrefix() + "tools gametest_danger");
        waitForHud(context, "[confirm]");
        context.waitTicks(10);
        if (DANGER_RUNS.get() != 0) throw new AssertionError("a dangerous tool ran without confirmation");
        String click = context.computeOnClient(client -> hud(client).stream()
                .map(ToolsGameTest::runCommandIn).filter(command -> command != null).findFirst().orElse(null));
        if (click == null) throw new AssertionError("the [confirm] link has no command");
        // The same path a chat click takes: the client intercepts it before the server sees it.
        context.runOnClient(client -> client.player.connection.sendUnattendedCommand(click, null));
        waitForHud(context, "danger ran");
        if (DANGER_RUNS.get() != 1) throw new AssertionError("confirming ran it " + DANGER_RUNS.get() + " times");
        context.runOnClient(client -> client.player.connection.sendUnattendedCommand(click, null));
        waitForHud(context, "expired or was already used");
        if (DANGER_RUNS.get() != 1) throw new AssertionError("a second click ran it again");
    }

    private static String runCommandIn(Component component) {
        if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) return run.command();
        for (Component sibling : component.getSiblings()) {
            String found = runCommandIn(sibling);
            if (found != null) return found;
        }
        return null;
    }

    private static void macroStep(ClientGameTestContext context) {
        SERVER_CHAT.clear();
        context.runOnClient(client -> {
            AiToolAction find = new AiToolAction();
            find.tool = "find";
            find.args = "oak_log";
            SendChatAction report = new SendChatAction();
            report.message = "/say tool_status={tool_status}";
            report.waitForGuiAfter = false;
            DihMacro macro = new DihMacro();
            macro.name = "gametest tools";
            macro.actions = new ArrayList<>(List.of(find, report));
            DihMacroManager manager = DihMacroManager.get();
            DihMacro old = manager.get(macro.name);
            if (old != null) manager.remove(old);
            manager.add(macro);
            manager.save();
            manager.load();
            DihMacro reloaded = manager.get(macro.name);
            if (reloaded == null || !(reloaded.actions.get(0) instanceof AiToolAction step)
                    || !"find".equals(step.tool) || !"oak_log".equals(step.args)) {
                throw new AssertionError("the AI_TOOL step didn't survive a save and reload");
            }
            MacroExecutor.execute(reloaded);
        });
        context.waitFor(client -> SERVER_CHAT.stream().anyMatch(line -> line.contains("tool_status=")), 200);
        String line = SERVER_CHAT.stream().filter(l -> l.contains("tool_status=")).findFirst().orElse("");
        if (!line.contains("tool_status=ok")) throw new AssertionError("macro said: " + line);
        context.runOnClient(client -> {
            DihMacro macro = DihMacroManager.get().get("gametest tools");
            if (macro != null) DihMacroManager.get().remove(macro);
            DihMacroManager.get().save();
        });
    }
}
