package dihclient.gui.screen;

import dihclient.modules.PackHideState;
import dihclient.util.DihConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The first-run tour: five short cards shown once, after the donate card on the first title screen, or on the first
 * world join if the title screen was skipped. Finishing or skipping it records {@link #TOUR_VERSION}, so it never
 * shows again until a later version adds new cards. Settings can replay it.
 */
public final class DihTour {
    /** Raise this when the tour gains cards worth showing to people who have seen it. */
    public static final int TOUR_VERSION = 1;

    private static boolean shownThisLaunch;

    private DihTour() {
    }

    /** Whether this player still needs the tour (and it may show here: not hidden, not an automated run). */
    public static boolean pending() {
        DihConfig config = DihConfig.getGlobal();
        return config != null && config.tourCompletedVersion < TOUR_VERSION
            && !PackHideState.isActive() && !DihDonateScreen.automated();
    }

    /** The screen to show instead of {@code next}: the tour the first time it's due, else {@code next}. */
    public static Screen after(Screen next) {
        if (shownThisLaunch || !pending()) return next;
        shownThisLaunch = true;
        return new DihTourScreen(next);
    }

    /** Called every client tick in a world: starts the tour if it's due and nothing else is on screen. */
    public static void tickInWorld(Minecraft mc) {
        if (shownThisLaunch || mc.player == null || mc.gui.screen() != null || !pending()) return;
        mc.gui.setScreen(after(null));
    }

    /** Settings' "Replay tour" button. */
    public static void replay(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new DihTourScreen(parent));
    }

    static void complete() {
        DihConfig config = DihConfig.getGlobal();
        if (config.tourCompletedVersion >= TOUR_VERSION) return;
        config.tourCompletedVersion = TOUR_VERSION;
        config.save();
    }
}
