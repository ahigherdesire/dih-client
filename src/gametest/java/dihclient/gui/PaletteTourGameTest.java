package dihclient.gui;

import dihclient.gui.screen.DihCommandPaletteScreen;
import dihclient.gui.screen.DihDonateScreen;
import dihclient.gui.screen.DihModuleScreen;
import dihclient.gui.screen.DihTour;
import dihclient.gui.screen.DihTourScreen;
import dihclient.modules.ModuleRegistry;
import dihclient.palette.PaletteIndex;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * WP 05 in a real client: no tour or palette appears by itself in automated runs; Ctrl+K opens the palette in-game,
 * in the module menu and in a chest, never from chat or a plain K; searching and Enter toggle a module; Esc goes back;
 * and the tour, replayed from Settings, walks its five cards and records completion.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PaletteTourGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            context.waitTicks(40);
            try {
                nothingAppearsByItself(context);
                System.out.println("[PaletteTourGameTest] nothing by itself ok");
                paletteInGame(context);
                System.out.println("[PaletteTourGameTest] in-game ok");
                paletteInModuleMenu(context);
                System.out.println("[PaletteTourGameTest] module menu ok");
                paletteInChest(context, world);
                System.out.println("[PaletteTourGameTest] chest ok");
                neverWhileTyping(context);
                System.out.println("[PaletteTourGameTest] typing ok");
                tourReplay(context);
                System.out.println("[PaletteTourGameTest] tour ok");
            } catch (Throwable t) {
                System.out.println("[PaletteTourGameTest] FAILED: " + t);
                throw t;
            }
        }
        firstLaunch(context);
        context.setScreen(TitleScreen::new);
    }

    /**
     * A real first launch: with the automated-run flag cleared and nothing shown yet this launch, the title screen
     * opens behind the donate card, closing it starts the tour, and neither comes back on the next title screen.
     */
    private static void firstLaunch(ClientGameTestContext context) {
        String flag = System.getProperty("fabric.client.gametest");
        context.runOnClient(client -> {
            System.clearProperty("fabric.client.gametest");
            DihConfig.getGlobal().tourCompletedVersion = 0;
            resetShown(DihDonateScreen.class);
            resetShown(DihTour.class);
        });
        try {
            context.setScreen(TitleScreen::new);
            context.waitFor(client -> client.gui.screen() instanceof DihDonateScreen, 40);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() instanceof DihTourScreen, 40);
            context.takeScreenshot("tour-after-donate");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() instanceof TitleScreen, 40);
            context.runOnClient(client -> {
                if (DihConfig.getGlobal().tourCompletedVersion != DihTour.TOUR_VERSION) throw new AssertionError("skipping didn't record the tour");
            });
            context.setScreen(TitleScreen::new);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (!(client.gui.screen() instanceof TitleScreen)) throw new AssertionError("second title screen showed " + client.gui.screen());
            });
            System.out.println("[PaletteTourGameTest] first launch ok");
        } finally {
            context.runOnClient(client -> {
                if (flag != null) System.setProperty("fabric.client.gametest", flag);
            });
        }
    }

    private static void resetShown(Class<?> type) {
        try {
            java.lang.reflect.Field field = type.getDeclaredField("shownThisLaunch");
            field.setAccessible(true);
            field.setBoolean(null, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void ctrlK(ClientGameTestContext context) {
        context.getInput().holdKey(InputConstants.KEY_LCONTROL);
        context.getInput().holdKeyFor(InputConstants.KEY_K, 3);
        context.getInput().releaseKey(InputConstants.KEY_LCONTROL);
        context.waitTicks(3);
    }

    private static void nothingAppearsByItself(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig.getGlobal().tourCompletedVersion = 0;
            if (DihTour.pending()) throw new AssertionError("the tour must not be due in an automated run");
            if (client.gui.screen() != null) throw new AssertionError("something opened by itself: " + client.gui.screen());
        });
    }

    private static void paletteInGame(ClientGameTestContext context) {
        context.getInput().holdKeyFor(InputConstants.KEY_K, 3);
        context.waitTicks(3);
        context.runOnClient(client -> {
            if (client.gui.screen() != null) throw new AssertionError("a plain K opened " + client.gui.screen());
        });
        ctrlK(context);
        context.waitFor(client -> client.gui.screen() instanceof DihCommandPaletteScreen, 20);
        context.getInput().typeChars("antihunger");
        context.waitTicks(2);
        context.takeScreenshot("palette-antihunger");
        context.runOnClient(client -> {
            DihCommandPaletteScreen palette = (DihCommandPaletteScreen) client.gui.screen();
            PaletteIndex.Entry first = palette.results().isEmpty() ? null : palette.results().get(0);
            if (first == null || !"module:anti-hunger".equals(first.action())) {
                throw new AssertionError("typing antihunger should put AntiHunger first, got " + palette.results());
            }
            if (ModuleRegistry.get("anti-hunger").isEnabled()) throw new AssertionError("AntiHunger started on");
        });
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(3);
        context.runOnClient(client -> {
            if (!ModuleRegistry.get("anti-hunger").isEnabled()) throw new AssertionError("Enter didn't toggle AntiHunger");
            if (client.gui.screen() != null) throw new AssertionError("the palette should close back to the game");
            if (!DihConfig.getGlobal().paletteRecent.contains("module:anti-hunger")) throw new AssertionError("not remembered as recent");
            ModuleRegistry.get("anti-hunger").setEnabled(false);
        });
    }

    private static void paletteInModuleMenu(ClientGameTestContext context) {
        context.setScreen(() -> new DihModuleScreen(null));
        context.waitTicks(5);
        ctrlK(context);
        context.waitFor(client -> client.gui.screen() instanceof DihCommandPaletteScreen, 20);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() instanceof DihModuleScreen, 20);
        context.setScreen(() -> null);
    }

    private static void paletteInChest(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/setblock 2 -60 0 minecraft:chest");
        context.waitTicks(5);
        context.runOnClient(client -> {
            BlockPos chest = new BlockPos(2, -60, 0);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
        });
        context.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 60);
        ctrlK(context);
        context.waitFor(client -> client.gui.screen() instanceof DihCommandPaletteScreen, 20);
        context.takeScreenshot("palette-over-chest");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 20);
        context.runOnClient(client -> client.player.closeContainer());
        context.waitFor(client -> client.gui.screen() == null, 20);
    }

    private static void neverWhileTyping(ClientGameTestContext context) {
        context.setScreen(() -> new ChatScreen("", false));
        context.waitTicks(3);
        ctrlK(context);
        context.getInput().typeChars("k");
        context.waitTicks(3);
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof ChatScreen)) throw new AssertionError("Ctrl+K in chat opened " + client.gui.screen());
        });
        context.setScreen(() -> null);
    }

    private static void tourReplay(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig.getGlobal().tourCompletedVersion = 0;
            DihTour.replay(null);
        });
        context.waitFor(client -> client.gui.screen() instanceof DihTourScreen, 20);
        context.takeScreenshot("tour-card-1");
        for (int card = 0; card < 4; card++) {
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(2);
        }
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof DihTourScreen tour) || tour.index() != 4) {
                throw new AssertionError("expected the fifth card, have " + client.gui.screen());
            }
        });
        context.takeScreenshot("tour-card-5");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(client -> client.gui.screen() == null, 20);
        context.runOnClient(client -> {
            if (DihConfig.getGlobal().tourCompletedVersion != DihTour.TOUR_VERSION) throw new AssertionError("finishing didn't record the tour");
        });
    }
}
