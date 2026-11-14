package dihclient.addons.bundled;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dihclient.api.ApiVersion;
import dihclient.api.DihAddon;
import dihclient.api.DihAddons;
import dihclient.api.hud.HudElementProvider;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Built-in addon: remembers where you last died this session, with a HUD line and {@code .deathpoint}. */
public final class DeathPointAddon extends DihAddon {
    public static final String ID = "dih-deathpoint";

    private static boolean wasDead;
    private static boolean has;
    private static int x, y, z;
    private static String dimension = "";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        DihAddons.events().onTick(DeathPointAddon::tick);
        DihAddons.commands().register(new DeathPointCommand());
        DihAddons.hud().register(new Hud());
    }

    @Override
    public String getPackage() {
        return "dihclient.addons.bundled";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            wasDead = false;
            return;
        }
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            x = mc.player.getBlockX();
            y = mc.player.getBlockY();
            z = mc.player.getBlockZ();
            dimension = mc.level.dimension() == Level.NETHER ? "Nether"
                : mc.level.dimension() == Level.END ? "End" : "Overworld";
            has = true;
            DihClientMessaging.sendPrefixed("§cDied at " + describe() + " §7(.deathpoint to see it again)");
        }
        wasDead = dead;
    }

    private static String describe() {
        return String.format(Locale.ROOT, "%d %d %d (%s)", x, y, z, dimension);
    }

    /** Horizontal distance to the death point, or -1 if unknown. */
    private static int distance(Minecraft mc) {
        if (!has || mc.player == null) return -1;
        double dx = mc.player.getX() - x;
        double dz = mc.player.getZ() - z;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static final class Hud implements HudElementProvider {
        private static final int PAD = 2;

        @Override public String id() { return ID + ":last-death"; }
        @Override public String label() { return "Last Death"; }
        @Override public String description() { return "Your last death location and the distance to it."; }

        private static String text() {
            if (!has) return "Last death: none";
            return "Death: " + describe() + " - " + distance(Minecraft.getInstance()) + "m";
        }

        @Override
        public int width() {
            Font font = Minecraft.getInstance().font;
            String t = text();
            return (font != null ? font.width(t) : t.length() * 6) + PAD * 2;
        }

        @Override public int height() { return 10; }

        @Override
        public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
            ctx.text(font, text(), x + PAD, y, has ? 0xFFFF7070 : 0xFFAAAAAA);
        }

        @Override public boolean defaultEnabled() { return true; }
        @Override public String defaultAnchor() { return "TOP_LEFT"; }
        @Override public int defaultX() { return 4; }
        @Override public int defaultY() { return 80; }
    }

    // .deathpoint shows the last death location; .deathpoint clear forgets it.
    private static final class DeathPointCommand extends Command {
        DeathPointCommand() {
            super("deathpoint", "Show where you last died.");
        }

        @Override
        public void build(LiteralArgumentBuilder<DihCommandSource> root) {
            root.executes(ctx -> {
                DihClientMessaging.sendPrefixed(has
                    ? "§cLast death: §f" + describe()
                    : "§7No death recorded this session.");
                return SUCCESS;
            });

            root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear").executes(ctx -> {
                has = false;
                DihClientMessaging.sendPrefixed("§aDeath point cleared.");
                return SUCCESS;
            }));
        }
    }
}
