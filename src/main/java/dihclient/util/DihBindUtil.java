package dihclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

public final class DihBindUtil {
    private static final int MOUSE_BIND_BASE = -1000;

    private DihBindUtil() {
    }

    public static boolean isMouseBind(int bindCode) {
        return bindCode <= MOUSE_BIND_BASE;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_BIND_BASE - button;
    }

    public static int decodeMouseButton(int bindCode) {
        return MOUSE_BIND_BASE - bindCode;
    }

    public static boolean isAllowedMouseButton(int button) {
        return button > InputConstants.MOUSE_BUTTON_LEFT && button <= InputConstants.MOUSE_BUTTON_8;
    }

    public static boolean isBindPressed(Minecraft client, int bindCode) {
        if (bindCode == -1 || client == null || client.getWindow() == null) return false;
        if (isMouseBind(bindCode)) {
            int button = decodeMouseButton(bindCode);
            return isAllowedMouseButton(button) && DihKeys.isMouseDown(button);
        }
        return DihKeys.isKeyDown(bindCode);
    }

    public static String getBindName(int bindCode) {
        if (bindCode == -1) return "None";
        if (isMouseBind(bindCode)) return getMouseButtonName(decodeMouseButton(bindCode));
        String name = DihKeys.displayName(bindCode);
        if (name.length() == 1) return name.toUpperCase();
        return switch (bindCode) {
            case InputConstants.KEY_F1 -> "F1";
            case InputConstants.KEY_F2 -> "F2";
            case InputConstants.KEY_F3 -> "F3";
            case InputConstants.KEY_F4 -> "F4";
            case InputConstants.KEY_F5 -> "F5";
            case InputConstants.KEY_F6 -> "F6";
            case InputConstants.KEY_F7 -> "F7";
            case InputConstants.KEY_F8 -> "F8";
            case InputConstants.KEY_F9 -> "F9";
            case InputConstants.KEY_F10 -> "F10";
            case InputConstants.KEY_F11 -> "F11";
            case InputConstants.KEY_F12 -> "F12";
            case InputConstants.KEY_LSHIFT -> "L.Shift";
            case InputConstants.KEY_RSHIFT -> "R.Shift";
            case InputConstants.KEY_LCONTROL -> "L.Ctrl";
            case InputConstants.KEY_RCONTROL -> "R.Ctrl";
            case InputConstants.KEY_LALT -> "L.Alt";
            case InputConstants.KEY_RALT -> "R.Alt";
            case InputConstants.KEY_TAB -> "Tab";
            case InputConstants.KEY_CAPSLOCK -> "CapsLk";
            case InputConstants.KEY_SPACE -> "Space";
            case InputConstants.KEY_RETURN -> "Enter";
            case InputConstants.KEY_BACKSPACE -> "Backsp";
            case InputConstants.KEY_DELETE -> "Delete";
            case InputConstants.KEY_INSERT -> "Insert";
            case InputConstants.KEY_HOME -> "Home";
            case InputConstants.KEY_END -> "End";
            case InputConstants.KEY_PAGEUP -> "PgUp";
            case InputConstants.KEY_PAGEDOWN -> "PgDn";
            case InputConstants.KEY_UP -> "Up";
            case InputConstants.KEY_DOWN -> "Down";
            case InputConstants.KEY_LEFT -> "Left";
            case InputConstants.KEY_RIGHT -> "Right";
            case InputConstants.KEY_NUMPADENTER -> "Num Enter";
            case InputConstants.KEY_NUMLOCK -> "NumLk";
            case InputConstants.KEY_PRINTSCREEN -> "PrtSc";
            case InputConstants.KEY_SCROLLLOCK -> "ScrLk";
            case InputConstants.KEY_PAUSE -> "Pause";
            default -> name;
        };
    }

    public static String getMouseButtonName(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> "LMB";
            case InputConstants.MOUSE_BUTTON_RIGHT -> "RMB";
            case InputConstants.MOUSE_BUTTON_MIDDLE -> "MMB";
            default -> "M" + DihKeys.mouseButtonNumber(button);
        };
    }
}
