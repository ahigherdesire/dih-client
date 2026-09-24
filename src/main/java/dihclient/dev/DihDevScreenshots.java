package dihclient.dev;

import dihclient.gui.screen.DihModuleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

/**
 * Dev-only: {@code -Ddih.dev.screenshots=true} walks the main UI screens, saves a screenshot of each
 * to {@code run/screenshots} and quits. Used to review UI changes without touching the keyboard.
 */
public final class DihDevScreenshots {
    private static int ticks = -1;
    private static int step = 0;
    private static int waited = 0;

    private DihDevScreenshots() {
    }

    public static void registerIfRequested() {
        if (!Boolean.getBoolean("dih.dev.screenshots")) return;
        ClientTickEvents.END_CLIENT_TICK.register(DihDevScreenshots::tick);
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
        switch (step++) {
            case 0 -> Screenshot.grab(mc, false);
            case 1 -> mc.gui.setScreen(new DihModuleScreen(screen));
            case 2 -> Screenshot.grab(mc, false);
            case 3 -> mc.stop();
            default -> { }
        }
    }
}
