package dihclient.addons.bundled;

import dihclient.api.ApiVersion;
import dihclient.api.DihAddon;
import dihclient.api.DihAddons;
import dihclient.api.hud.HudElementProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** Built-in addon: a HUD line with time in the current world, blocks walked and FPS. */
public final class SessionStatsAddon extends DihAddon {
    public static final String ID = "dih-sessionstats";
    private static final double TELEPORT_THRESHOLD = 20.0;

    private static long joinedAtMs = System.currentTimeMillis();
    private static double lastX, lastZ;
    private static boolean haveLast;
    private static double blocks;

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        DihAddons.events().onGameJoin(SessionStatsAddon::onJoin);
        DihAddons.events().onTick(SessionStatsAddon::tick);
        DihAddons.hud().register(new Hud());
    }

    @Override
    public String getPackage() {
        return "dihclient.addons.bundled";
    }

    private static void onJoin() {
        joinedAtMs = System.currentTimeMillis();
        blocks = 0;
        haveLast = false;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) {
            haveLast = false;
            return;
        }
        double x = mc.player.getX();
        double z = mc.player.getZ();
        if (haveLast) {
            double dx = x - lastX;
            double dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < TELEPORT_THRESHOLD) blocks += d;
        }
        lastX = x;
        lastZ = z;
        haveLast = true;
    }

    private static String text() {
        long s = (System.currentTimeMillis() - joinedAtMs) / 1000;
        String elapsed = String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return elapsed + " | " + (int) blocks + " blocks | " + Minecraft.getInstance().getFps() + " fps";
    }

    private static final class Hud implements HudElementProvider {
        private static final int PAD = 2;

        @Override public String id() { return ID + ":stats"; }
        @Override public String label() { return "Session Stats"; }
        @Override public String description() { return "Time in this world, blocks walked, and FPS."; }

        @Override
        public int width() {
            Font font = Minecraft.getInstance().font;
            String t = text();
            return Math.max(120, (font != null ? font.width(t) : t.length() * 6) + PAD * 2);
        }

        @Override public int height() { return 10; }

        @Override
        public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
            ctx.text(font, text(), x + PAD, y, 0xFFB0E0FF);
        }

        @Override public boolean defaultEnabled() { return true; }
        @Override public String defaultAnchor() { return "TOP_LEFT"; }
        @Override public int defaultX() { return 4; }
        @Override public int defaultY() { return 100; }
    }
}
