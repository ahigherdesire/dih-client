package dihclient.dev;

import dihclient.gui.screen.DihAddonStoreScreen;
import dihclient.gui.screen.DihAddonsScreen;
import dihclient.gui.screen.DihModuleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

/**
 * Dev-only: {@code -Ddih.dev.screenshots=true} walks the main UI screens, saves a screenshot of each
 * to {@code run/screenshots} and quits. Used to review UI changes without touching the keyboard.
 * {@code -Ddih.dev.screenshots=addons} walks the Addons and Browse Online screens instead; adding
 * {@code -Ddih.dev.screenshots.uninstall=true} also uninstalls the first listing.
 */
public final class DihDevScreenshots {
    private static int ticks = -1;
    private static int step = 0;
    private static int waited = 0;
    private static boolean addons;

    private DihDevScreenshots() {
    }

    public static void registerIfRequested() {
        String mode = System.getProperty("dih.dev.screenshots", "");
        addons = "addons".equals(mode);
        if (!addons && !"true".equals(mode)) return;
        dihclient.platform.DihPlatform.onEndClientTick(DihDevScreenshots::tick);
    }

    private static void tick(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (ticks < 0) {
            boolean title = screen != null && screen.getClass().getName().contains("Title");
            if (!title && ++waited < 1200) return;
            ticks = 0;
        }
        ticks++;
        if (ticks % 60 != 0) return;
        if (addons) {
            switch (step++) {
                case 0 -> mc.gui.setScreen(new DihAddonsScreen(screen));
                case 1 -> Screenshot.grab(mc, false);
                case 2 -> mc.gui.setScreen(new DihAddonStoreScreen(screen));
                case 3 -> Screenshot.grab(mc, false);
                case 4 -> { if (screen instanceof DihAddonStoreScreen store) store.openFirstForScreenshot(); }
                case 5 -> Screenshot.grab(mc, false);
                case 6 -> {
                    // Uninstalls the first listing for real; only run this walk with a copy of the jar to put back.
                    if (Boolean.getBoolean("dih.dev.screenshots.uninstall") && screen instanceof DihAddonStoreScreen store) {
                        store.uninstallOpenForScreenshot();
                    }
                }
                case 7 -> Screenshot.grab(mc, false);
                case 8 -> mc.gui.setScreen(new DihAddonsScreen(screen));
                case 9 -> Screenshot.grab(mc, false);
                case 10 -> mc.stop();
                default -> { }
            }
            return;
        }
        switch (step++) {
            case 0 -> Screenshot.grab(mc, false);
            case 1 -> mc.gui.setScreen(new DihModuleScreen(screen));
            case 2 -> Screenshot.grab(mc, false);
            case 3 -> mc.stop();
            default -> { }
        }
    }
}
