package dihclient.modules;

import dihclient.DihClientAddon;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD drawing for modules that are not elements of the HUD module: the GIF image,
 * BedESP's bed summary, Danger's incoming-projectile warnings and the Schematics placing panel.
 * {@link dihclient.util.DihRuntimeActivity} sets HUD_OVERLAYS while any of them is enabled.
 */
public final class ModuleHudOverlays {
    private static final String[] MODULE_IDS = {"gif", "bed-esp", "danger", "schematics"};

    private ModuleHudOverlays() {
    }

    public static boolean anyEnabled() {
        for (String id : MODULE_IDS) {
            Module module = ModuleRegistry.get(id);
            if (module != null && module.isEnabled()) return true;
        }
        return false;
    }

    public static boolean hasWork() {
        return GifModule.shouldRender() || BedEspModule.hasHudWork() || DangerModule.hasHudWork()
            || SchematicsModule.hasHudWork();
    }

    public static void render(GuiGraphicsExtractor context) {
        try {
            if (GifModule.shouldRender()) GifModule.render(context);
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("GIF overlay failed", t);
        }
        try {
            BedEspModule.renderHud(context);
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("BedESP overlay failed", t);
        }
        try {
            DangerModule.renderHud(context);
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("Danger overlay failed", t);
        }
        try {
            SchematicsModule.renderHud(context);
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("Schematics placing panel failed", t);
        }
    }
}
