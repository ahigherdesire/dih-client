package dihclient.commands.args;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.commands.DihCommandSource;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLScancode;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class KeyArgumentType implements ArgumentType<Integer> {
    private static final Map<String, Integer> NAMES = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown key"));

    // GLFW's key names (what .bind has always accepted), mapped to the running version's key codes.
    static {
        key("SPACE", InputConstants.KEY_SPACE);
        key("APOSTROPHE", InputConstants.KEY_APOSTROPHE);
        key("COMMA", InputConstants.KEY_COMMA);
        key("MINUS", InputConstants.KEY_MINUS);
        key("PERIOD", InputConstants.KEY_PERIOD);
        key("SLASH", InputConstants.KEY_SLASH);
        key("0", InputConstants.KEY_0);
        key("1", InputConstants.KEY_1);
        key("2", InputConstants.KEY_2);
        key("3", InputConstants.KEY_3);
        key("4", InputConstants.KEY_4);
        key("5", InputConstants.KEY_5);
        key("6", InputConstants.KEY_6);
        key("7", InputConstants.KEY_7);
        key("8", InputConstants.KEY_8);
        key("9", InputConstants.KEY_9);
        key("SEMICOLON", InputConstants.KEY_SEMICOLON);
        key("EQUAL", InputConstants.KEY_EQUALS);
        key("A", InputConstants.KEY_A);
        key("B", InputConstants.KEY_B);
        key("C", InputConstants.KEY_C);
        key("D", InputConstants.KEY_D);
        key("E", InputConstants.KEY_E);
        key("F", InputConstants.KEY_F);
        key("G", InputConstants.KEY_G);
        key("H", InputConstants.KEY_H);
        key("I", InputConstants.KEY_I);
        key("J", InputConstants.KEY_J);
        key("K", InputConstants.KEY_K);
        key("L", InputConstants.KEY_L);
        key("M", InputConstants.KEY_M);
        key("N", InputConstants.KEY_N);
        key("O", InputConstants.KEY_O);
        key("P", InputConstants.KEY_P);
        key("Q", InputConstants.KEY_Q);
        key("R", InputConstants.KEY_R);
        key("S", InputConstants.KEY_S);
        key("T", InputConstants.KEY_T);
        key("U", InputConstants.KEY_U);
        key("V", InputConstants.KEY_V);
        key("W", InputConstants.KEY_W);
        key("X", InputConstants.KEY_X);
        key("Y", InputConstants.KEY_Y);
        key("Z", InputConstants.KEY_Z);
        key("LEFT_BRACKET", InputConstants.KEY_LBRACKET);
        key("BACKSLASH", InputConstants.KEY_BACKSLASH);
        key("RIGHT_BRACKET", InputConstants.KEY_RBRACKET);
        key("GRAVE_ACCENT", InputConstants.KEY_GRAVE);
        key("ESCAPE", InputConstants.KEY_ESCAPE);
        key("ENTER", InputConstants.KEY_RETURN);
        key("TAB", InputConstants.KEY_TAB);
        key("BACKSPACE", InputConstants.KEY_BACKSPACE);
        key("INSERT", InputConstants.KEY_INSERT);
        key("DELETE", InputConstants.KEY_DELETE);
        key("RIGHT", InputConstants.KEY_RIGHT);
        key("LEFT", InputConstants.KEY_LEFT);
        key("DOWN", InputConstants.KEY_DOWN);
        key("UP", InputConstants.KEY_UP);
        key("PAGE_UP", InputConstants.KEY_PAGEUP);
        key("PAGE_DOWN", InputConstants.KEY_PAGEDOWN);
        key("HOME", InputConstants.KEY_HOME);
        key("END", InputConstants.KEY_END);
        key("CAPS_LOCK", InputConstants.KEY_CAPSLOCK);
        key("SCROLL_LOCK", InputConstants.KEY_SCROLLLOCK);
        key("NUM_LOCK", InputConstants.KEY_NUMLOCK);
        key("PRINT_SCREEN", InputConstants.KEY_PRINTSCREEN);
        key("PAUSE", InputConstants.KEY_PAUSE);
        key("F1", InputConstants.KEY_F1);
        key("F2", InputConstants.KEY_F2);
        key("F3", InputConstants.KEY_F3);
        key("F4", InputConstants.KEY_F4);
        key("F5", InputConstants.KEY_F5);
        key("F6", InputConstants.KEY_F6);
        key("F7", InputConstants.KEY_F7);
        key("F8", InputConstants.KEY_F8);
        key("F9", InputConstants.KEY_F9);
        key("F10", InputConstants.KEY_F10);
        key("F11", InputConstants.KEY_F11);
        key("F12", InputConstants.KEY_F12);
        key("F13", InputConstants.KEY_F13);
        key("F14", InputConstants.KEY_F14);
        key("F15", InputConstants.KEY_F15);
        key("F16", InputConstants.KEY_F16);
        key("F17", InputConstants.KEY_F17);
        key("F18", InputConstants.KEY_F18);
        key("F19", InputConstants.KEY_F19);
        key("F20", InputConstants.KEY_F20);
        key("F21", InputConstants.KEY_F21);
        key("F22", InputConstants.KEY_F22);
        key("F23", InputConstants.KEY_F23);
        key("F24", InputConstants.KEY_F24);
        key("KP_0", InputConstants.KEY_NUMPAD0);
        key("KP_1", InputConstants.KEY_NUMPAD1);
        key("KP_2", InputConstants.KEY_NUMPAD2);
        key("KP_3", InputConstants.KEY_NUMPAD3);
        key("KP_4", InputConstants.KEY_NUMPAD4);
        key("KP_5", InputConstants.KEY_NUMPAD5);
        key("KP_6", InputConstants.KEY_NUMPAD6);
        key("KP_7", InputConstants.KEY_NUMPAD7);
        key("KP_8", InputConstants.KEY_NUMPAD8);
        key("KP_9", InputConstants.KEY_NUMPAD9);
        key("KP_DECIMAL", InputConstants.KEY_NUMPADCOMMA);
        key("KP_MULTIPLY", InputConstants.KEY_MULTIPLY);
        key("KP_ADD", InputConstants.KEY_ADD);
        key("KP_ENTER", InputConstants.KEY_NUMPADENTER);
        key("KP_EQUAL", InputConstants.KEY_NUMPADEQUALS);
        key("LEFT_SHIFT", InputConstants.KEY_LSHIFT);
        key("LEFT_CONTROL", InputConstants.KEY_LCONTROL);
        key("LEFT_ALT", InputConstants.KEY_LALT);
        key("RIGHT_SHIFT", InputConstants.KEY_RSHIFT);
        key("RIGHT_CONTROL", InputConstants.KEY_RCONTROL);
        key("RIGHT_ALT", InputConstants.KEY_RALT);
        //? if >=26.3 {
        /*key("LEFT_SUPER", InputConstants.KEY_LGUI);
        key("RIGHT_SUPER", InputConstants.KEY_RGUI);
        key("KP_DIVIDE", SDLScancode.SDL_SCANCODE_KP_DIVIDE);
        key("KP_SUBTRACT", SDLScancode.SDL_SCANCODE_KP_MINUS);
        key("MENU", SDLScancode.SDL_SCANCODE_APPLICATION);
        *///?} else {
        key("LEFT_SUPER", InputConstants.KEY_LSUPER);
        key("RIGHT_SUPER", InputConstants.KEY_RSUPER);
        key("KP_DIVIDE", GLFW.GLFW_KEY_KP_DIVIDE);
        key("KP_SUBTRACT", GLFW.GLFW_KEY_KP_SUBTRACT);
        key("MENU", GLFW.GLFW_KEY_MENU);
        key("F25", InputConstants.KEY_F25);
        key("WORLD_1", GLFW.GLFW_KEY_WORLD_1);
        key("WORLD_2", GLFW.GLFW_KEY_WORLD_2);
        //?}
    }

    private static void key(String name, int code) {
        NAMES.put(name, code);
    }

    public static KeyArgumentType key() { return new KeyArgumentType(); }

    public static int get(CommandContext<DihCommandSource> ctx, String name) {
        return ctx.getArgument(name, Integer.class);
    }

    public static String keyName(int code) {
        if (code < 0) return "NONE";
        for (Map.Entry<String, Integer> e : NAMES.entrySet()) {
            if (e.getValue() == code) return e.getKey();
        }
        return "KEY_" + code;
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String raw = reader.readUnquotedString();
        String upper = raw.toUpperCase(Locale.ROOT);
        Integer code = NAMES.get(upper);
        if (code != null) return code;
        throw UNKNOWN.create();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (String n : NAMES.keySet()) {
            if (n.startsWith(remaining)) builder.suggest(n);
        }
        return builder.buildFuture();
    }
}
