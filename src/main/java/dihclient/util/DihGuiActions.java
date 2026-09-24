package dihclient.util;

import dihclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

public final class DihGuiActions {
    private DihGuiActions() {
    }

    public static boolean saveCurrentGui(Minecraft mc) {
        return saveCurrentGui(mc, true);
    }

    public static boolean saveCurrentGui(Minecraft mc, boolean notify) {
        if (mc == null || mc.gui.screen() == null || mc.player == null) {
            if (notify) {
                DihNotifications.error("Failed to store GUI.");
            }
            return false;
        }

        DihSharedState.get().storeScreen(mc.gui.screen(), mc.player.containerMenu);
        if (notify) {
            DihNotifications.show(savedGuiMessage(), 0xFF35D873);
        }
        return true;
    }

    private static String savedGuiMessage() {
        int keyCode = DihConfig.getGlobal().keybindLoadGui;
        if (keyCode == -1) return "GUI stored.";
        return "GUI stored. Press " + DihKeybindOverlay.getKeyName(keyCode) + " to restore.";
    }

    public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket) {
        return closeCurrentScreen(mc, sendPacket, true);
    }

    public static boolean closeCurrentScreen(Minecraft mc, boolean sendPacket, boolean notify) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.gui.screen() == null) return false;

        Screen screen = mc.gui.screen();
        if (screen instanceof DihSpecialGuiActions special) {
            if (sendPacket) special.dih$closeWithPacket(notify);
            else special.dih$closeWithoutPacket(notify);
            return true;
        }

        if (sendPacket) {
            if (mc.player != null
                && mc.player.containerMenu != null
                && mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            } else {
                mc.gui.setScreen(null);
            }
        } else {
            if (mc.player != null
                && mc.player.containerMenu != null
                && mc.player.containerMenu != mc.player.inventoryMenu) {
                DihSharedState.get().setSuppressNextContainerClosePacket(true);
                mc.player.closeContainer();
                if (notify) {
                    DihClientMessaging.sendPrefixed("GUI closed without packet.");
                }
            } else {
                mc.gui.setScreen(null);
                if (notify) {
                    DihClientMessaging.sendPrefixed("Screen closed locally.");
                }
            }
        }
        return true;
    }

    public static boolean desyncCurrentScreen(Minecraft mc) {
        return desyncCurrentScreen(mc, true);
    }

    public static boolean desyncCurrentScreen(Minecraft mc, boolean notify) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.gui.screen() == null) return false;

        Screen screen = mc.gui.screen();
        if (screen instanceof DihSpecialGuiActions special) {
            special.dih$desync(notify);
            return true;
        }

        if (mc.getConnection() == null || mc.player == null || mc.player.containerMenu == null) {
            return false;
        }

        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        if (notify) {
            DihClientMessaging.sendPrefixed("GUI desynced: close packet sent while client screen stays open.");
        }
        return true;
    }
}
