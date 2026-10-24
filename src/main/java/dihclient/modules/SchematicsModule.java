package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.KeybindSetting;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.Schematics;
import dihclient.util.DihBindUtil;
import dihclient.util.DihClientMessaging;

/**
 * Shows schematic placements in the world and holds their keybinds. The placements themselves live in
 * {@link Schematics} and are managed with {@code .schem} and the Schematics screen.
 */
public final class SchematicsModule extends Module {

    static final String LAYER_ALL = "All";
    static final String LAYER_SINGLE = "Single";
    static final String LAYER_BELOW = "Below";
    static final String LAYER_ABOVE = "Above";

    /** Litematica's default overlay colours. */
    static final int MISSING = 0x2C33B3E6;
    static final int EXTRA = 0x4CFF4CE6;
    static final int WRONG_STATE = 0x4CFF9010;
    static final int WRONG_BLOCK = 0x4CFF3333;

    private static SchematicsModule cached;

    private int cachedRevision = -1;
    private SchematicOverlayRenderer.Style style;
    private final boolean[] keyWasDown = new boolean[4];

    public SchematicsModule() {
        super("schematics", "Schematics", ModuleCategory.RENDER,
            "Places schematic files in the world and shows what is still to build.");
        add(new BoolSetting("ghost-blocks", "Ghost Blocks", true)
            .group("Render").description("Draw the blocks still missing.").build());
        add(new IntSetting("ghost-alpha", "Ghost Alpha", 150, 20, 255, 5)
            .group("Render").description("Ghost block strength out of 255.").visibleWhen(() -> bool("ghost-blocks")).build());
        add(new BoolSetting("overlay", "Overlay", true)
            .group("Render").description("Tint every block that is not right yet.").build());
        add(new BoolSetting("outline", "Outline", true)
            .group("Render").description("Edge the tinted blocks.").build());
        add(new BoolSetting("see-through", "See Through", false)
            .group("Render").description("Draw the overlay through walls.").build());
        add(new IntSetting("render-range", "Render Range", 96, 16, 512, 16)
            .group("Render").description("Blocks from the camera that are drawn.").build());

        add(new ChoiceSetting("layer-mode", "Layers", LAYER_ALL, LAYER_ALL, LAYER_SINGLE, LAYER_BELOW, LAYER_ABOVE)
            .group("Layers").description("Draw all layers, one, or those below or above a height.").build());
        add(new IntSetting("layer-y", "Layer Y", 64, -64, 320, 1)
            .group("Layers").description("The height the layer mode uses.")
            .visibleWhen(() -> !LAYER_ALL.equals(choice("layer-mode"))).build());

        add(new ColorSetting("color-missing", "Missing", MISSING)
            .group("Colors").description("A block not placed yet.").build());
        add(new ColorSetting("color-extra", "Extra", EXTRA)
            .group("Colors").description("A block where the schematic has air.").build());
        add(new ColorSetting("color-wrong-state", "Wrong State", WRONG_STATE)
            .group("Colors").description("The right block, turned or set wrong.").build());
        add(new ColorSetting("color-wrong-block", "Wrong Block", WRONG_BLOCK)
            .group("Colors").description("Another block than the schematic's.").build());

        add(new KeybindSetting("menu-key", "Menu Key", -1)
            .group("Keys").description("Opens the Schematics screen.").build());
        add(new KeybindSetting("adjust-key", "Adjust Key", -1)
            .group("Keys").description("Moves the selected placement with the mouse.").build());
        add(new KeybindSetting("rotate-key", "Rotate Key", -1)
            .group("Keys").description("Turns the selected placement 90 degrees clockwise.").build());
        add(new KeybindSetting("mirror-key", "Mirror Key", -1)
            .group("Keys").description("Cycles the selected placement's mirror.").build());
    }

    static SchematicsModule instance() {
        SchematicsModule local = cached;
        if (local != null) {
            return local;
        }
        Module module = ModuleRegistry.get("schematics");
        return module instanceof SchematicsModule schematics ? (cached = schematics) : null;
    }

    public static void initialize() {
        SchematicOverlayRenderer.installRenderHook();
    }

    @Override
    public void onEnable() {
        SchematicOverlayRenderer.installRenderHook();
        SchematicOverlayRenderer.invalidate();
    }

    @Override
    public void onDisable() {
        SchematicOverlayRenderer.invalidate();
    }

    @Override
    public String info() {
        int count = Schematics.get().placements().size();
        return count == 0 ? "" : String.valueOf(count);
    }

    @Override
    public void tick() {
        if (MC.player == null) {
            return;
        }
        Schematics.get().tick(MC);
        key(0, "menu-key", () -> MC.gui.setScreen(new dihclient.gui.screen.DihSchematicsScreen(null)));
        key(1, "adjust-key", () -> {
            SchematicPlacement selected = selectedOrSay();
            if (selected != null) {
                MC.gui.setScreen(new dihclient.gui.screen.DihSchematicPlacementScreen(selected, null));
            }
        });
        key(2, "rotate-key", () -> {
            SchematicPlacement selected = selectedOrSay();
            if (selected != null) {
                selected.rotate(1);
                Schematics.get().changed();
            }
        });
        key(3, "mirror-key", () -> {
            SchematicPlacement selected = selectedOrSay();
            if (selected != null) {
                selected.cycleMirror();
                Schematics.get().changed();
            }
        });
    }

    private void key(int index, String setting, Runnable action) {
        int bind;
        try {
            bind = Integer.parseInt(value(setting));
        } catch (NumberFormatException ignored) {
            bind = -1;
        }
        boolean down = bind != -1 && DihBindUtil.isBindPressed(MC, bind);
        if (down && !this.keyWasDown[index] && MC.gui.screen() == null) {
            action.run();
        }
        this.keyWasDown[index] = down;
    }

    private static SchematicPlacement selectedOrSay() {
        SchematicPlacement selected = Schematics.get().selected();
        if (selected == null) {
            DihClientMessaging.sendPrefixed("§7No schematic placement selected. §fUse .schem place <file>");
        }
        return selected;
    }

    SchematicOverlayRenderer.Style style() {
        int revision = ModuleRegistry.revision();
        if (revision != this.cachedRevision || this.style == null) {
            this.cachedRevision = revision;
            this.style = new SchematicOverlayRenderer.Style(bool("ghost-blocks"), bool("overlay"), bool("outline"),
                bool("see-through"), integer("render-range"), choice("layer-mode"), integer("layer-y"),
                integer("ghost-alpha"),
                ModuleRenderUtil.color(this, "color-missing", MISSING),
                ModuleRenderUtil.color(this, "color-extra", EXTRA),
                ModuleRenderUtil.color(this, "color-wrong-state", WRONG_STATE),
                ModuleRenderUtil.color(this, "color-wrong-block", WRONG_BLOCK));
        }
        return this.style;
    }

    /**
     * Sets the layer mode ({@code All}, {@code Single}, {@code Below} or {@code Above}) and its height, for
     * {@code .schem layer}.
     */
    public void setLayer(String mode, Integer y) {
        setValue("layer-mode", mode);
        if (y != null) {
            setValue("layer-y", String.valueOf(y));
        }
    }
}
