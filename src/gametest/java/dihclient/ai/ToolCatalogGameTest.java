package dihclient.ai;

import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WP 07 in a real client: a spot-check of the catalog through {@code .tools}, at least one tool from every category
 * (nether_end by light_portal; its trips are the portal game test's) plus a {@code #} and a {@code .} adapter. Tools
 * that change the world are checked by what changed, not only by what they printed.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ToolCatalogGameTest implements FabricClientGameTest {
    private static final List<String> SERVER_CHAT = Collections.synchronizedList(new ArrayList<>());
    private static final BlockPos LOG = new BlockPos(4, 100, 0);
    /** Distinct tools that passed, in order. */
    private static final java.util.Set<String> PASSED = new java.util.LinkedHashSet<>();

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
                    "/execute in minecraft:overworld run fill -8 99 -8 8 99 8 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -8 100 -8 8 104 8 minecraft:air",
                    "/execute in minecraft:overworld run setblock 4 100 0 minecraft:oak_log",
                    "/clear @p",
                    "/give @p minecraft:dirt 16",
                    "/tp @p 0 100 0 -90 0")) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(20);
            try {
                // Quick, read-only tools.
                expect(context, "biome_here", "biome_here ok", "in the overworld");
                expect(context, "inventory", "inventory ok", "16 dirt");
                expect(context, "guardian_status", "guardian_status ok", "Guardian");
                // Offers seen earlier in the session (the trade test) are remembered, so either answer is right.
                expect(context, "read_offers", "read_offers ok", "read_offers ok");
                expect(context, "recipe_of torch", "recipe_of ok", "torch");
                expect(context, "web_search diamond ore height", "web_search failed", "Web tools are off");
                expect(context, "module_toggle Fullbright on", "module_toggle ok", "Fullbright is on");
                expect(context, "module_toggle Fullbright off", "module_toggle ok", "Fullbright is off");
                expect(context, "waypoint_add spot 2 100 2", "waypoint_add ok", "#wp save user spot 2 100 2");
                expect(context, "waypoint_list", "waypoint_list ok", "spot");
                expect(context, "home_set base", "home_set ok", "Ran #home");
                expect(context, "cmd_threats status", "cmd_threats ok", "It printed: Threats:");
                // A command that prints in red fails the tool, with what it printed.
                expect(context, "cmd_proc", "cmd_proc failed", "No process in control");
                expect(context, "dot_modules", "dot_modules ok", "Ran .modules");
                System.out.println("[ToolCatalogGameTest] read-only tools ok");

                whisper(context);
                System.out.println("[ToolCatalogGameTest] whisper ok");

                // Jobs, each checked by its effect on the world.
                expect(context, "mine_block oak_log 1", "mine_block running", "#mine");
                context.waitFor(client -> client.level.getBlockState(LOG).isAir() && count(client, Items.OAK_LOG) >= 1, 1200);
                expect(context, "stop", "stop ok", "#stop");
                System.out.println("[ToolCatalogGameTest] mine_block ok");

                expect(context, "craft oak_planks 4", "craft running", "");
                context.waitFor(client -> count(client, Items.OAK_PLANKS) >= 4, 1200);
                System.out.println("[ToolCatalogGameTest] craft ok");

                expect(context, "fill_region -2 100 -3 -1 100 -3 dirt", "fill_region running", "#sel set dirt");
                context.waitFor(client -> client.level.getBlockState(new BlockPos(-2, 100, -3)).is(Blocks.DIRT)
                        && client.level.getBlockState(new BlockPos(-1, 100, -3)).is(Blocks.DIRT), 1200);
                expect(context, "stop", "stop ok", "#stop");
                System.out.println("[ToolCatalogGameTest] fill_region ok");

                world.getServer().runCommand("/summon minecraft:item 3 100 4 {Item:{id:\"minecraft:oak_sapling\",count:1}}");
                context.waitTicks(10);
                expect(context, "collect_drops oak_sapling", "collect_drops running", "#pickup oak_sapling");
                context.waitFor(client -> count(client, Items.OAK_SAPLING) >= 1, 1200);
                expect(context, "stop", "stop ok", "#stop");
                System.out.println("[ToolCatalogGameTest] collect_drops ok");

                expect(context, "goto -5 100 5", "goto running", "#goto -5 100 5");
                context.waitFor(client -> client.player.blockPosition().distSqr(new BlockPos(-5, 100, 5)) <= 2, 1200);
                expect(context, "stop", "stop ok", "#stop");
                System.out.println("[ToolCatalogGameTest] goto ok");

                // An empty frame two blocks in front of the player, along X.
                world.getServer().runCommand("/fill -5 100 7 -2 104 7 minecraft:obsidian");
                world.getServer().runCommand("/fill -4 101 7 -3 103 7 minecraft:air");
                context.waitTicks(10);
                expect(context, "light_portal", "light_portal failed", "No flint and steel");
                world.getServer().runCommand("/give @p minecraft:flint_and_steel");
                context.waitFor(client -> count(client, Items.FLINT_AND_STEEL) >= 1, 100);
                expect(context, "light_portal", "light_portal ok", "Lit the portal frame");
                context.waitFor(client -> client.level.getBlockState(new BlockPos(-4, 101, 7)).is(Blocks.NETHER_PORTAL), 100);
                System.out.println("[ToolCatalogGameTest] light_portal ok");
                context.takeScreenshot("tool-catalog");
            } catch (Throwable t) {
                System.out.println("[ToolCatalogGameTest] FAILED after " + PASSED + ": " + t);
                throw t;
            }
            if (PASSED.size() < 15) throw new AssertionError("only " + PASSED.size() + " tools checked: " + PASSED);
            System.out.println("[ToolCatalogGameTest] " + PASSED.size() + " tools checked: " + PASSED);
        }
        context.setScreen(TitleScreen::new);
    }

    private static int count(Minecraft client, Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }

    private static List<Component> hud(Minecraft client) {
        return ((DihChatComponentAccessor) client.gui.hud.getChat()).dih$getAllMessages().stream()
                .map(message -> message.content()).toList();
    }

    /**
     * Runs {@code .tools line} and waits for the result line that starts with {@code head} (e.g. "goto running"),
     * then checks it, or one of the lines printed with it, contains {@code text}.
     */
    private static void expect(ClientGameTestContext context, String line, String head, String text) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        context.runOnClient(client -> client.player.connection.sendChat(DihCommands.effectivePrefix() + "tools " + line));
        try {
            context.waitFor(client -> hud(client).stream().anyMatch(l -> l.getString().contains(head)), 400);
        } catch (Throwable t) {
            throw new AssertionError(".tools " + line + ": never printed \"" + head + "\"; chat has " + recent(context));
        }
        context.waitTicks(2);
        boolean found = context.computeOnClient(client -> hud(client).stream().anyMatch(l -> l.getString().contains(text)));
        if (!found) throw new AssertionError(".tools " + line + ": no \"" + text + "\" in " + recent(context));
        PASSED.add(line.split(" ")[0]);
    }

    private static String recent(ClientGameTestContext context) {
        return context.computeOnClient(client -> hud(client).stream().limit(8).map(Component::getString).toList().toString());
    }

    /** A dangerous tool: it asks first, and only the [confirm] click sends the whisper. */
    private static void whisper(ClientGameTestContext context) {
        SERVER_CHAT.clear();
        String name = context.computeOnClient(client -> client.player.getGameProfile().name());
        expect(context, "whisper " + name + " catalog check", "[confirm]", "whisper");
        context.waitTicks(10);
        if (SERVER_CHAT.stream().anyMatch(l -> l.contains("catalog check"))) {
            throw new AssertionError("whisper sent before it was confirmed");
        }
        String click = context.computeOnClient(client -> hud(client).stream()
                .map(ToolCatalogGameTest::runCommandIn).filter(command -> command != null).findFirst().orElse(null));
        if (click == null) throw new AssertionError("the [confirm] link has no command");
        context.runOnClient(client -> client.player.connection.sendUnattendedCommand(click, null));
        try {
            context.waitFor(client -> SERVER_CHAT.stream().anyMatch(l -> l.contains("catalog check")), 200);
        } catch (Throwable t) {
            throw new AssertionError("the whisper never arrived; server chat: " + SERVER_CHAT);
        }
    }

    private static String runCommandIn(Component component) {
        if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) return run.command();
        for (Component sibling : component.getSiblings()) {
            String found = runCommandIn(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
