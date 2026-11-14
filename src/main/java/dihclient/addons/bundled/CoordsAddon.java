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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in addon: a coordinates HUD (with the matching Nether/Overworld position) and {@code .coords}. */
public final class CoordsAddon extends DihAddon {
    public static final String ID = "dih-coords";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        DihAddons.hud().register(new Hud());
        DihAddons.commands().register(new CoordsCommand());
    }

    @Override
    public String getPackage() {
        return "dihclient.addons.bundled";
    }

    static String position(Minecraft mc) {
        if (mc.player == null) return "XYZ: -";
        return String.format(Locale.ROOT, "XYZ: %d %d %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    static String facing(Minecraft mc) {
        if (mc.player == null) return "Facing: -";
        return "Facing: " + mc.player.getDirection().getName();
    }

    /** The matching position in the other dimension (x8 or /8), or null outside the Overworld and Nether. */
    static String otherDimension(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        int x = mc.player.getBlockX();
        int z = mc.player.getBlockZ();
        if (mc.level.dimension() == Level.OVERWORLD) {
            return String.format(Locale.ROOT, "Nether: %d %d", Math.floorDiv(x, 8), Math.floorDiv(z, 8));
        }
        if (mc.level.dimension() == Level.NETHER) {
            return String.format(Locale.ROOT, "Overworld: %d %d", x * 8, z * 8);
        }
        return null;
    }

    private static final class Hud implements HudElementProvider {
        private static final int PAD = 2;
        private static final int LINE_HEIGHT = 10;

        @Override public String id() { return ID + ":coords"; }
        @Override public String label() { return "Coordinates"; }
        @Override public String description() { return "Position, facing, and the matching Nether/Overworld coordinates."; }

        private static List<String> lines() {
            Minecraft mc = Minecraft.getInstance();
            List<String> lines = new ArrayList<>(3);
            lines.add(position(mc));
            lines.add(facing(mc));
            String other = otherDimension(mc);
            if (other != null) lines.add(other);
            return lines;
        }

        @Override
        public int width() {
            Font font = Minecraft.getInstance().font;
            int max = 90;
            for (String line : lines()) {
                int w = font != null ? font.width(line) : line.length() * 6;
                max = Math.max(max, w + PAD * 2);
            }
            return max;
        }

        @Override public int height() { return lines().size() * LINE_HEIGHT; }

        @Override
        public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, float alpha) {
            int row = 0;
            for (String line : lines()) {
                ctx.text(font, line, x + PAD, y + row * LINE_HEIGHT, 0xFFFFFFFF);
                row++;
            }
        }

        @Override public boolean defaultEnabled() { return true; }
        @Override public String defaultAnchor() { return "TOP_LEFT"; }
        @Override public int defaultX() { return 4; }
        @Override public int defaultY() { return 40; }
    }

    // .coords prints your position locally; .coords copy copies "x y z" to the clipboard.
    private static final class CoordsCommand extends Command {
        CoordsCommand() {
            super("coords", "Show or copy your coordinates.");
        }

        @Override
        public void build(LiteralArgumentBuilder<DihCommandSource> root) {
            root.executes(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                DihClientMessaging.sendPrefixed("§b" + position(mc));
                String other = otherDimension(mc);
                if (other != null) DihClientMessaging.sendPrefixed("§7" + other);
                return SUCCESS;
            });

            root.then(LiteralArgumentBuilder.<DihCommandSource>literal("copy").executes(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return 0;
                mc.keyboardHandler.setClipboard(mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
                DihClientMessaging.sendPrefixed("§aCoordinates copied.");
                return SUCCESS;
            }));
        }
    }
}
