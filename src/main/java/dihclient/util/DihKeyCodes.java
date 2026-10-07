package dihclient.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Saved key codes across Minecraft versions. 26.2 numbers keys and mouse buttons the GLFW way, 26.3 the SDL way
 * (Right Shift is 344 on one and 229 on the other), and both read the same config folder when a launcher shares a
 * game directory. Files that hold key codes record which way they were written ({@code keyCodes}), and anything
 * written the other way is renumbered on load, so binds made on either version work on both.
 *
 * <p>Binds are DIH bind codes: -1 for none, {@link DihBindUtil#encodeMouseButton} codes for mouse buttons, key
 * codes otherwise.
 */
public final class DihKeyCodes {
    public static final String GLFW = "glfw";
    public static final String SDL = "sdl";

    /** How this build numbers keys. */
    public static final String CURRENT =
        //? if >=26.3 {
        /*SDL;
        *///?} else {
        GLFW;
        //?}

    /** {GLFW code, SDL code} for every key InputConstants names on both versions, plus a few it doesn't. */
    private static final int[][] KEYS = {
        {32, 44}, // KEY_SPACE
        {39, 52}, // KEY_APOSTROPHE
        {44, 54}, // KEY_COMMA
        {45, 45}, // KEY_MINUS
        {46, 55}, // KEY_PERIOD
        {47, 56}, // KEY_SLASH
        {48, 39}, // KEY_0
        {49, 30}, // KEY_1
        {50, 31}, // KEY_2
        {51, 32}, // KEY_3
        {52, 33}, // KEY_4
        {53, 34}, // KEY_5
        {54, 35}, // KEY_6
        {55, 36}, // KEY_7
        {56, 37}, // KEY_8
        {57, 38}, // KEY_9
        {59, 51}, // KEY_SEMICOLON
        {61, 46}, // KEY_EQUALS
        {65, 4}, // KEY_A
        {66, 5}, // KEY_B
        {67, 6}, // KEY_C
        {68, 7}, // KEY_D
        {69, 8}, // KEY_E
        {70, 9}, // KEY_F
        {71, 10}, // KEY_G
        {72, 11}, // KEY_H
        {73, 12}, // KEY_I
        {74, 13}, // KEY_J
        {75, 14}, // KEY_K
        {76, 15}, // KEY_L
        {77, 16}, // KEY_M
        {78, 17}, // KEY_N
        {79, 18}, // KEY_O
        {80, 19}, // KEY_P
        {81, 20}, // KEY_Q
        {82, 21}, // KEY_R
        {83, 22}, // KEY_S
        {84, 23}, // KEY_T
        {85, 24}, // KEY_U
        {86, 25}, // KEY_V
        {87, 26}, // KEY_W
        {88, 27}, // KEY_X
        {89, 28}, // KEY_Y
        {90, 29}, // KEY_Z
        {91, 47}, // KEY_LBRACKET
        {92, 49}, // KEY_BACKSLASH
        {93, 48}, // KEY_RBRACKET
        {96, 53}, // KEY_GRAVE
        {256, 41}, // KEY_ESCAPE
        {257, 40}, // KEY_RETURN
        {258, 43}, // KEY_TAB
        {259, 42}, // KEY_BACKSPACE
        {260, 73}, // KEY_INSERT
        {261, 76}, // KEY_DELETE
        {262, 79}, // KEY_RIGHT
        {263, 80}, // KEY_LEFT
        {264, 81}, // KEY_DOWN
        {265, 82}, // KEY_UP
        {266, 75}, // KEY_PAGEUP
        {267, 78}, // KEY_PAGEDOWN
        {268, 74}, // KEY_HOME
        {269, 77}, // KEY_END
        {280, 57}, // KEY_CAPSLOCK
        {281, 71}, // KEY_SCROLLLOCK
        {282, 83}, // KEY_NUMLOCK
        {283, 70}, // KEY_PRINTSCREEN
        {284, 72}, // KEY_PAUSE
        {290, 58}, // KEY_F1
        {291, 59}, // KEY_F2
        {292, 60}, // KEY_F3
        {293, 61}, // KEY_F4
        {294, 62}, // KEY_F5
        {295, 63}, // KEY_F6
        {296, 64}, // KEY_F7
        {297, 65}, // KEY_F8
        {298, 66}, // KEY_F9
        {299, 67}, // KEY_F10
        {300, 68}, // KEY_F11
        {301, 69}, // KEY_F12
        {302, 104}, // KEY_F13
        {303, 105}, // KEY_F14
        {304, 106}, // KEY_F15
        {305, 107}, // KEY_F16
        {306, 108}, // KEY_F17
        {307, 109}, // KEY_F18
        {308, 110}, // KEY_F19
        {309, 111}, // KEY_F20
        {310, 112}, // KEY_F21
        {311, 113}, // KEY_F22
        {312, 114}, // KEY_F23
        {313, 115}, // KEY_F24
        {320, 98}, // KEY_NUMPAD0
        {321, 89}, // KEY_NUMPAD1
        {322, 90}, // KEY_NUMPAD2
        {323, 91}, // KEY_NUMPAD3
        {324, 92}, // KEY_NUMPAD4
        {325, 93}, // KEY_NUMPAD5
        {326, 94}, // KEY_NUMPAD6
        {327, 95}, // KEY_NUMPAD7
        {328, 96}, // KEY_NUMPAD8
        {329, 97}, // KEY_NUMPAD9
        {330, 220}, // KEY_NUMPADCOMMA
        {331, 84}, // KP_DIVIDE
        {332, 85}, // KEY_MULTIPLY
        {333, 86}, // KP_SUBTRACT
        {334, 87}, // KEY_ADD
        {335, 88}, // KEY_NUMPADENTER
        {336, 103}, // KEY_NUMPADEQUALS
        {340, 225}, // KEY_LSHIFT
        {341, 224}, // KEY_LCONTROL
        {342, 226}, // KEY_LALT
        {343, 227}, // KEY_LSUPER
        {344, 229}, // KEY_RSHIFT
        {345, 228}, // KEY_RCONTROL
        {346, 230}, // KEY_RALT
        {347, 231}, // KEY_RSUPER
        {348, 101}, // MENU
    };

    private static final Map<Integer, Integer> GLFW_TO_SDL = new HashMap<>();
    private static final Map<Integer, Integer> SDL_TO_GLFW = new HashMap<>();

    static {
        for (int[] pair : KEYS) {
            GLFW_TO_SDL.put(pair[0], pair[1]);
            SDL_TO_GLFW.put(pair[1], pair[0]);
        }
    }

    private DihKeyCodes() {
    }

    /** The scheme a file says it was written in, or null when it says nothing (written before files said). */
    public static String normalize(String scheme) {
        if (GLFW.equalsIgnoreCase(scheme)) return GLFW;
        if (SDL.equalsIgnoreCase(scheme)) return SDL;
        return null;
    }

    /**
     * Bind {@code bind}, saved in {@code savedScheme}, as this build numbers it. A null scheme is an unmarked file
     * from before 5.1-beta.6a: older 26.3 builds kept the 26.2 codes they found and only wrote their own defaults,
     * so a code that is a GLFW key is read as one and anything else as SDL.
     */
    public static int toCurrent(int bind, String savedScheme) {
        return convert(bind, normalize(savedScheme), CURRENT);
    }

    static int convert(int bind, String from, String to) {
        if (bind == -1 || to.equals(from)) return bind;
        if (DihBindUtil.isMouseBind(bind)) {
            int button = DihBindUtil.decodeMouseButton(bind);
            String source = from != null ? from : (isGlfwMouseButton(button) ? GLFW : SDL);
            return source.equals(to) ? bind : DihBindUtil.encodeMouseButton(convertMouseButton(button, source));
        }
        String source = from != null ? from : (GLFW_TO_SDL.containsKey(bind) ? GLFW : SDL);
        if (source.equals(to)) return bind;
        Integer mapped = (GLFW.equals(source) ? GLFW_TO_SDL : SDL_TO_GLFW).get(bind);
        return mapped == null ? bind : mapped;
    }

    private static boolean isGlfwMouseButton(int button) {
        return button >= 0 && button <= 7;
    }

    /** GLFW orders buttons left 0, right 1, middle 2, then 3...; SDL left 1, middle 2, right 3, then 4... */
    static int convertMouseButton(int button, String from) {
        if (GLFW.equals(from)) {
            return switch (button) {
                case 0 -> 1;
                case 1 -> 3;
                case 2 -> 2;
                default -> button + 1;
            };
        }
        return switch (button) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> button - 1;
        };
    }
}
