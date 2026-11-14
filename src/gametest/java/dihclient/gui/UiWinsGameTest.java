package dihclient.gui;

import dihclient.gui.screen.DihModuleScreen;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import dihclient.modules.DihModule;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihConfig;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.MacroShareCode;
import dihclient.util.macro.DelayAction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * WP 04 in a real client: Chat Feedback off/on, a pinned packet logger that stays on screen in-game and in chat and
 * survives screens closing, macro folders and share codes, and the module menu, macro list and settings window at
 * 150% text (screenshots).
 */
@SuppressWarnings("UnstableApiUsage")
public final class UiWinsGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            context.waitTicks(20);
            try {
                chatFeedback(context);
                System.out.println("[UiWinsGameTest] chat feedback ok");
                pinnedLogger(context);
                System.out.println("[UiWinsGameTest] pinned logger ok");
                foldersAndShareCodes(context);
                System.out.println("[UiWinsGameTest] folders and share codes ok");
                largeText(context);
                System.out.println("[UiWinsGameTest] large text ok");
            } catch (Throwable t) {
                // Printed before the world closes, in case closing it hangs.
                System.out.println("[UiWinsGameTest] FAILED: " + t);
                throw t;
            }
        } finally {
            context.runOnClient(client -> {
                DihConfig config = DihConfig.getGlobal();
                config.uiTextScale = 1.0;
                config.moduleToggleChat = true;
                config.save();
            });
        }
        context.setScreen(TitleScreen::new);
    }

    private static void chatFeedback(ClientGameTestContext context) {
        long before = context.computeOnClient(client -> chatLines(client, "AntiHunger: "));
        context.runOnClient(client -> {
            DihConfig.getGlobal().moduleToggleChat = false;
            Module module = ModuleRegistry.get("anti-hunger");
            module.setEnabled(true);
            module.setEnabled(false);
        });
        context.waitTicks(5);
        long whileOff = context.computeOnClient(client -> chatLines(client, "AntiHunger: "));
        if (whileOff != before) throw new AssertionError("Chat Feedback is off but toggling still printed to chat");
        context.runOnClient(client -> {
            DihConfig.getGlobal().moduleToggleChat = true;
            Module module = ModuleRegistry.get("anti-hunger");
            module.setEnabled(true);
            module.setEnabled(false);
        });
        context.waitTicks(10);
        // DIH replaces a module's previous toggle line in place, so only the latest ("disabled") remains.
        boolean printed = context.computeOnClient(client -> chatLines(client, "AntiHunger: disabled") > 0);
        if (!printed) {
            String recent = context.computeOnClient(client -> ((DihChatComponentAccessor) client.gui.hud.getChat())
                .dih$getAllMessages().stream().limit(6).map(message -> message.content().getString()).toList().toString());
            throw new AssertionError("Chat Feedback is on but no toggle line reached chat; chat has " + recent);
        }
    }

    /** Lines in the chat HUD containing {@code text} (DIH messages go straight to the HUD, not through chat events). */
    private static long chatLines(Minecraft client, String text) {
        return ((DihChatComponentAccessor) client.gui.hud.getChat()).dih$getAllMessages().stream()
            .filter(message -> message.content().getString().contains(text))
            .count();
    }

    private static void pinnedLogger(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihPacketLoggerOverlay logger = DihModule.get().getPacketLoggerOverlay();
            DihOverlayManager.get().register(logger);
            logger.setVisible(true);
            logger.setPinned(true);
            // Closing a screen clears unpinned windows; a pinned one must stay registered.
            DihOverlayManager.get().clear();
            if (!DihOverlayManager.get().getOverlays().contains(logger)) throw new AssertionError("the pinned logger was cleared");
            DihConfig.SavedWindowLayout saved = DihConfig.getGlobal().windowLayouts.get(DihPacketLoggerOverlay.OVERLAY_ID);
            if (saved == null || !saved.pinned || !saved.visible) throw new AssertionError("the pin was not saved to the config");
        });
        context.waitTicks(10);
        context.takeScreenshot("pinned-logger-ingame");
        context.setScreen(() -> new ChatScreen("", false));
        context.waitTicks(10);
        context.takeScreenshot("pinned-logger-chat");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            DihPacketLoggerOverlay logger = DihModule.get().getPacketLoggerOverlay();
            logger.setPinned(false);
            DihOverlayManager.get().clear();
            if (DihOverlayManager.get().getOverlays().contains(logger)) throw new AssertionError("an unpinned logger outlived its screen");
            logger.setVisible(false);
        });
    }

    private static void foldersAndShareCodes(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihMacroManager manager = DihMacroManager.get();
            for (String[] entry : new String[][]{{"kit pvp", "PvP"}, {"kit tank", "PvP"}, {"sell loop", "Skyblock"}, {"go home", ""}}) {
                DihMacro macro = new DihMacro(entry[0]);
                macro.folder = entry[1];
                macro.actions = new ArrayList<>(List.of(new DelayAction()));
                manager.add(macro);
            }
            try {
                List<DihMacro> pvp = manager.getAll().stream().filter(m -> "PvP".equals(m.folder)).toList();
                String code = MacroShareCode.encode(pvp, "PvP");
                MacroShareCode.Decoded decoded = MacroShareCode.decode(code);
                for (DihMacro macro : decoded.macros()) manager.addImportedCopy(macro, macro.name);
            } catch (MacroShareCode.InvalidCodeException e) {
                throw new AssertionError(e.getMessage());
            }
            if (manager.get("kit pvp (1)") == null || !"PvP".equals(manager.get("kit pvp (1)").folder)) {
                throw new AssertionError("importing a folder code should add renamed copies in the same folder");
            }
        });
    }

    private static void largeText(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig.getGlobal().uiTextScale = 1.5;
            DihConfig.getGlobal().collapsedMacroFolders.clear();
        });
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(10);
        context.runOnClient(client -> {
            try {
                Method runUtility = DihModuleScreen.class.getDeclaredMethod("runUtility", String.class);
                runUtility.setAccessible(true);
                runUtility.invoke(client.gui.screen(), "macros");
                runUtility.invoke(client.gui.screen(), "keys");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("text-150-module-menu");
        context.setScreen(() -> null);
        context.runOnClient(client -> DihConfig.getGlobal().uiTextScale = 1.0);
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(10);
        context.takeScreenshot("text-100-module-menu");
        context.setScreen(() -> null);
    }
}
