package dihclient.modules;

import net.minecraft.world.item.ItemStack;

public final class DihAdminToolsBridge {
    private DihAdminToolsBridge() {
    }

    public static boolean fillNbtEditorSilently(ItemStack stack) {
        Module module = ModuleRegistry.get("admin-tools");
        if (module instanceof BuiltinModules.AdminToolsModule adminTools) {
            return adminTools.fillItemEditorFromStack(stack, false);
        }
        return false;
    }

    public static boolean openFilledAdminEditor(ItemStack stack) {
        if (!fillNbtEditorSilently(stack)) return false;
        try {
            dihclient.util.DihAdminToolsOverlay overlay =
                dihclient.util.DihAdminToolsOverlay.getSharedOverlay();
            dihclient.util.DihOverlayManager manager = dihclient.util.DihOverlayManager.get();
            manager.register(overlay);
            overlay.setVisible(true);
            overlay.showRawItemEditor();
            manager.bringToFront(overlay);
        } catch (Throwable ignored) {  }
        return true;
    }
}
