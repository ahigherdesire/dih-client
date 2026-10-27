package dihclient.schematic;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Placing a new schematic the way Litematica does: the camera stays free and the placement is moved from the keyboard
 * until it is kept or dropped. Arrows move it relative to where the player faces, Page Up/Down raise and lower it, R
 * turns it, M mirrors it, G drops it onto the ground, Enter keeps it and Backspace removes it. These keys only do this
 * while a placement is being placed and no screen is open, so they never fight the game's own binds otherwise.
 * {@code .schem adjust} and the Adjust Key still open the mouse placement screen.
 */
public final class SchematicPlacing {

    /** Ticks a held move key waits before repeating, then ticks between repeats. */
    private static final int REPEAT_DELAY = 8;
    private static final int REPEAT_EVERY = 2;

    private static final int[] KEYS = {
        InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT,
        InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN,
        InputConstants.KEY_R, InputConstants.KEY_M, InputConstants.KEY_G,
        InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_BACKSPACE
    };

    private static SchematicPlacement placement;
    private static final int[] held = new int[KEYS.length];

    private SchematicPlacing() {}

    /**
     * Starts placing {@code p}: it is selected, and the keys move it until it is kept or removed.
     */
    public static void start(SchematicPlacement p) {
        placement = p;
        Schematics.get().select(p);
        // a key already down (the Enter that ran a command) must be released before it counts
        for (int i = 0; i < KEYS.length; i++) {
            held[i] = DihKeys.isKeyDown(KEYS[i]) ? Integer.MAX_VALUE : 0;
        }
    }

    /**
     * @return the placement being placed, or null when none is (it was kept, removed, or the world changed)
     */
    public static SchematicPlacement active() {
        if (placement != null && !Schematics.get().placements().contains(placement)) {
            placement = null;
        }
        return placement;
    }

    public static void keep() {
        SchematicPlacement p = active();
        if (p != null) {
            Schematics.get().changed();
            DihClientMessaging.sendPrefixed("§aKept §f" + p.name() + "§7. Move it again with §f.schem adjust§7.");
        }
        placement = null;
    }

    public static void remove() {
        SchematicPlacement p = active();
        if (p != null) {
            Schematics.get().remove(p);
            DihClientMessaging.sendPrefixed("§7Removed §f" + p.name());
        }
        placement = null;
    }

    /**
     * Reads the placing keys; called every client tick by the Schematics module.
     */
    public static void tick(Minecraft mc) {
        SchematicPlacement p = active();
        if (p == null || mc.player == null) {
            return;
        }
        boolean free = mc.gui.screen() == null;
        for (int i = 0; i < KEYS.length; i++) {
            boolean down = free && DihKeys.isKeyDown(KEYS[i]);
            if (!down) {
                held[i] = 0;
                continue;
            }
            if (held[i] == Integer.MAX_VALUE) {
                continue;
            }
            int t = held[i]++;
            boolean repeats = i < 6;
            if (t == 0 || repeats && t >= REPEAT_DELAY && (t - REPEAT_DELAY) % REPEAT_EVERY == 0) {
                press(mc, p, KEYS[i]);
                if (active() == null) {
                    return;
                }
            }
        }
    }

    private static void press(Minecraft mc, SchematicPlacement p, int key) {
        Direction facing = mc.player.getDirection();
        switch (key) {
            case InputConstants.KEY_UP -> move(p, facing);
            case InputConstants.KEY_DOWN -> move(p, facing.getOpposite());
            case InputConstants.KEY_LEFT -> move(p, facing.getCounterClockWise());
            case InputConstants.KEY_RIGHT -> move(p, facing.getClockWise());
            case InputConstants.KEY_PAGEUP -> move(p, Direction.UP);
            case InputConstants.KEY_PAGEDOWN -> move(p, Direction.DOWN);
            case InputConstants.KEY_R -> {
                p.rotate(1);
                Schematics.get().changed();
            }
            case InputConstants.KEY_M -> {
                p.cycleMirror();
                Schematics.get().changed();
            }
            case InputConstants.KEY_G -> {
                SchematicActions.ground(mc, p);
                Schematics.get().changed();
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> keep();
            case InputConstants.KEY_BACKSPACE -> remove();
            default -> {
            }
        }
    }

    private static void move(SchematicPlacement p, Direction d) {
        p.nudge(d.getStepX(), d.getStepY(), d.getStepZ());
        Schematics.get().changed();
    }

    /**
     * @return the lines for the placing panel on the HUD, or empty when nothing is being placed
     */
    public static List<String> hudLines() {
        SchematicPlacement p = active();
        if (p == null) {
            return List.of();
        }
        BlockPos o = p.origin();
        String turn = p.transform().rotation() + "°" + (p.transform().mirror() == SchematicTransform.Mirroring.NONE
            ? "" : ", mirror " + p.transform().mirror().id());
        return List.of(
            "Placing " + p.name() + "  at " + o.getX() + " " + o.getY() + " " + o.getZ() + ", " + turn,
            "Arrows: move   PgUp/PgDn: up/down   R: rotate   M: mirror   G: ground",
            "Enter: keep   Backspace: remove");
    }
}
