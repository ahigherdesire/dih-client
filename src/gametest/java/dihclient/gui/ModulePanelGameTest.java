package dihclient.gui;

import dihclient.gui.screen.DihAiSetupScreen;
import dihclient.gui.screen.DihModuleScreen;
import dihclient.modules.DihModule;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihConfig;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihUiScale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Method;

/**
 * The single-window module menu in a real client: it opens by default, typing searches every category and Enter
 * toggles the top hit, a click on the rail switches tabs, a module opens straight onto its settings, open windows stay
 * on screen beside it, and the classic floating windows are still one setting away. Screenshots cover the new look
 * (panel, a window, the AI setup screen, the HUD and the title screen).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ModulePanelGameTest implements FabricClientGameTest {
    private static final String TAG = "[ModulePanelGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            config.customMainMenu = false;
            config.classicModuleMenu = false;
            config.moduleMenuTab = "ALL";
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            context.waitTicks(40);
            try {
                opensAsPanel(context);
                System.out.println(TAG + "panel ok");
                searchAndEnter(context);
                System.out.println(TAG + "search ok");
                railClick(context);
                System.out.println(TAG + "rail ok");
                settingsOf(context);
                System.out.println(TAG + "settings ok");
                windowsBeside(context);
                System.out.println(TAG + "windows ok");
                classic(context);
                System.out.println(TAG + "classic ok");
                otherScreens(context);
                System.out.println(TAG + "screens ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            }
        } finally {
            context.runOnClient(client -> {
                DihConfig config = DihConfig.getGlobal();
                config.classicModuleMenu = false;
                config.moduleMenuTab = "ALL";
                config.save();
            });
        }
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = true);
        context.setScreen(TitleScreen::new);
        context.waitTicks(40);
        context.takeScreenshot("ui-title");
        // Fabric's runner needs the vanilla title screen, at the end of this test and when the next run starts, so the
        // custom menu is left off (and saved off) whatever it was before.
        context.runOnClient(client -> {
            DihConfig.getGlobal().customMainMenu = false;
            DihConfig.getGlobal().save();
        });
        context.setScreen(TitleScreen::new);
    }

    private static void opensAsPanel(ClientGameTestContext context) {
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(10);
        context.takeScreenshot("ui-panel-all");
    }

    private static void searchAndEnter(ClientGameTestContext context) {
        String id = moduleIdNamed(context, "AntiHunger");
        context.runOnClient(client -> ModuleRegistry.get(id).setEnabled(false));
        context.getInput().typeChars("antihung");
        context.waitTicks(5);
        context.takeScreenshot("ui-panel-search");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(3);
        boolean on = context.computeOnClient(client -> ModuleRegistry.get(id).isEnabled());
        if (!on) throw new AssertionError("Enter in the search box should toggle the top hit (AntiHunger) on");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(3);
        boolean off = !context.computeOnClient(client -> ModuleRegistry.get(id).isEnabled());
        if (!off) throw new AssertionError("a second Enter should toggle AntiHunger back off");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        if (!(context.computeOnClient(client -> client.gui.screen()) instanceof DihModuleScreen)) {
            throw new AssertionError("the first Esc should only leave the search box");
        }
    }

    /** Clicks the rail's "Enabled" row, the second entry, at the panel's fixed geometry. */
    private static void railClick(ClientGameTestContext context) {
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(5);
        int[] point = context.computeOnClient(client -> {
            int sw = DihUiScale.getVirtualScreenWidth();
            int sh = DihUiScale.getVirtualScreenHeight();
            int w = Math.max(Math.min(300, sw - 4), Math.min(660, sw - 16));
            int h = Math.max(Math.min(200, sh - 4), Math.min(410, sh - 16));
            int panelX = (sw - w) / 2;
            int panelY = (sh - h) / 2;
            int railW = w < 470 ? 30 : w >= 600 ? 126 : 110;
            int bodyY = panelY + 28;
            return new int[] {panelX + 1 + railW / 2, bodyY + 4 + 17 + 8};
        });
        context.getInput().setCursorPos(DihUiScale.virtualToFramebufferX(point[0]), DihUiScale.virtualToFramebufferY(point[1]));
        context.getInput().pressMouse(0);
        context.waitTicks(3);
        String tab = context.computeOnClient(client -> DihConfig.getGlobal().moduleMenuTab);
        if (!"ENABLED".equals(tab)) throw new AssertionError("clicking the rail's Enabled row should switch tabs, got " + tab);
        context.takeScreenshot("ui-panel-enabled-tab");
        context.runOnClient(client -> DihConfig.getGlobal().moduleMenuTab = "ALL");
    }

    private static void settingsOf(ClientGameTestContext context) {
        String id = moduleIdNamed(context, "KillAura");
        context.setScreen(() -> new DihModuleScreen(null).openingSettingsOf(id));
        context.waitTicks(10);
        context.takeScreenshot("ui-panel-killaura");
        context.setScreen(() -> null);
    }

    private static void windowsBeside(ClientGameTestContext context) {
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(5);
        context.runOnClient(client -> {
            try {
                Method runUtility = DihModuleScreen.class.getDeclaredMethod("runUtility", String.class);
                runUtility.setAccessible(true);
                runUtility.invoke(client.gui.screen(), "keys");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("ui-panel-with-settings-window");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            DihPacketLoggerOverlay logger = DihModule.get().getPacketLoggerOverlay();
            DihOverlayManager.get().register(logger);
            logger.setVisible(true);
            logger.setPinned(true);
        });
        context.waitTicks(10);
        context.takeScreenshot("ui-logger-window");
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(10);
        context.takeScreenshot("ui-logger-over-panel");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            DihPacketLoggerOverlay logger = DihModule.get().getPacketLoggerOverlay();
            logger.setPinned(false);
            logger.setVisible(false);
            DihOverlayManager.get().clear();
        });
    }

    private static void classic(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().classicModuleMenu = true);
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(10);
        context.takeScreenshot("ui-classic-menu");
        context.setScreen(() -> null);
        context.runOnClient(client -> DihConfig.getGlobal().classicModuleMenu = false);
    }

    private static void otherScreens(ClientGameTestContext context) {
        context.setScreen(() -> new DihAiSetupScreen(null));
        context.waitTicks(10);
        context.takeScreenshot("ui-ai-setup");
        context.setScreen(() -> null);
        context.waitTicks(10);
        context.takeScreenshot("ui-hud");
    }

    private static String moduleIdNamed(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            for (Module module : ModuleRegistry.all()) {
                if (module.name().equalsIgnoreCase(name)) return module.id();
            }
            throw new AssertionError("no module named " + name);
        });
    }
}
