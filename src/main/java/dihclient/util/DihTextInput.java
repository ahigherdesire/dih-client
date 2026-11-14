package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps typing working in DIH's own text fields on 26.3. Its SDL input only sends typed characters while text input
 * is started, and vanilla starts it only when an {@code EditBox} takes focus; DIH draws its own fields (module search,
 * the palette, the macro editor...), so on 26.3 nothing reached their {@code charTyped}. While a DIH screen or a DIH
 * overlay is up, this keeps text input started, owned by that screen, and hands it back when it closes. 26.2 (GLFW)
 * always sends characters, so there this does nothing.
 */
public final class DihTextInput {
    private static Object owner;

    private DihTextInput() {
    }

    /** Every frame. */
    public static void sync(Minecraft mc) {
        Screen screen = mc == null || mc.gui == null ? null : mc.gui.screen();
        boolean want = screen != null
            && (screen.getClass().getName().startsWith("dihclient.") || DihOverlayManager.get().hasVisibleOverlay());
        //? if >=26.3 {
        /*var input = mc == null ? null : mc.textInputManager();
        if (input == null) return;
        if (want) {
            // Cheap while it is already started: it only takes ownership, so a vanilla EditBox in the screen losing
            // focus (stopTextInput(itself)) can't switch it off under DIH's fields.
            input.startTextInput(screen);
            owner = screen;
        } else if (owner != null) {
            input.stopTextInput(owner);  // only if DIH still owns it; an EditBox that took it over keeps it
            owner = null;
        }
        *///?} else {
        owner = want ? screen : null;
        //?}
    }
}
