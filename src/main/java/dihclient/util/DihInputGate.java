package dihclient.util;

import dihclient.gui.screen.DihModuleScreen;
import dihclient.gui.vanillaui.components.CompactTextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;

public final class DihInputGate {
    private static final Minecraft MC = Minecraft.getInstance();

    private DihInputGate() {
    }

    public static boolean canRunDihKeybinds() {
        DihConfig config = DihConfig.getGlobal();
        if (MC == null) return false;

        if (CompactTextInput.anyFocused()) return false;
        if (MC.gui.screen() == null) return true;
        if (config == null || !config.keybindInsideGui) return false;
        if (MC.gui.screen() instanceof ChatScreen || MC.gui.screen() instanceof InBedChatScreen) return false;
        if (MC.gui.screen() instanceof AbstractSignEditScreen) return false;
        if (MC.gui.screen() instanceof BookEditScreen || MC.gui.screen() instanceof BookSignScreen) return false;
        if (isTypingTarget(MC.gui.screen().getFocused())) return false;
        if (hasFocusedTextInput(MC.gui.screen(), 0)) return false;
        if (DihOverlayManager.get().isAnyTextFieldFocused()) return false;

        if (!dihclient.util.DihLiteVariant.enabled()
                && MC.gui.screen() instanceof DihModuleScreen moduleScreen) {
            return !moduleScreen.blocksGlobalKeybinds();
        }
        return true;
    }

    private static boolean hasFocusedTextInput(GuiEventListener listener, int depth) {
        if (listener == null || depth > 4) return false;
        if (listener instanceof EditBox || listener instanceof MultiLineEditBox) return isTypingTarget(listener);
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                if (hasFocusedTextInput(child, depth + 1)) return true;
            }
        }
        return false;
    }

    private static boolean isTypingTarget(GuiEventListener listener) {
        if (listener instanceof EditBox editBox) return editBox.isFocused() && editBox.isVisible();

        if (listener instanceof MultiLineEditBox multiLine) return multiLine.isFocused();
        return false;
    }
}
