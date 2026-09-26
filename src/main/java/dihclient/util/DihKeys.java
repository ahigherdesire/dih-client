package dihclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLMouse;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

/**
 * Keyboard and mouse state where Minecraft 26.2 (GLFW) and 26.3 (SDL3) differ. Everything else about input is
 * the same on both as long as code uses {@link InputConstants} names ({@code KEY_*}, {@code MOUSE_BUTTON_*},
 * {@code MOD_*}), never raw GLFW numbers: 26.3 renumbered every key (SDL scancodes) and mouse button.
 *
 * <p>Don't do arithmetic on key codes either: digits, F13+ and the numpad aren't contiguous in SDL.
 */
public final class DihKeys {

    /** The code of an unknown or unbound key. */
    public static final int UNKNOWN = InputConstants.UNKNOWN.getValue();

    private DihKeys() {
    }

    /** Whether keyboard key {@code key} (an {@code InputConstants.KEY_*} value) is held right now. */
    public static boolean isKeyDown(int key) {
        Minecraft mc = Minecraft.getInstance();
        if (key == UNKNOWN || mc == null || mc.getWindow() == null) return false;
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(key);
        *///?} else {
        return InputConstants.isKeyDown(mc.getWindow(), key);
        //?}
    }

    /** Whether mouse button {@code button} (an {@code InputConstants.MOUSE_BUTTON_*} value) is held right now. */
    public static boolean isMouseDown(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        //? if >=26.3 {
        /*return (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
        *///?} else {
        return GLFW.glfwGetMouseButton(mc.getWindow().handle(), button) == GLFW.GLFW_PRESS;
        //?}
    }

    /**
     * A side mouse button as people count them (the first side button is 4) on both versions. Name left, right
     * and middle by their constants: GLFW orders them left/right/middle, SDL left/middle/right.
     */
    public static int mouseButtonNumber(int button) {
        return button - InputConstants.MOUSE_BUTTON_LEFT + 1;
    }

    /**
     * A key event's second code, the one {@code new KeyEvent(key, code, modifiers)} takes back: the platform
     * scancode on 26.2, the SDL keycode on 26.3. Pass it along; don't compare it with anything.
     */
    public static int secondaryCode(KeyEvent event) {
        //? if >=26.3 {
        /*return event.keycode();
        *///?} else {
        return event.scancode();
        //?}
    }

    /** The key's name as the controls screen shows it ("A", "Left Shift"). */
    public static String displayName(int key) {
        //? if >=26.3 {
        /*return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
        *///?} else {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        //?}
    }
}
