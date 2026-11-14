package dihclient.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihKeyCodesTest {
    private static final int GLFW_RSHIFT = 344;
    private static final int SDL_RSHIFT = 229;
    private static final int GLFW_V = 86;
    private static final int SDL_V = 25;
    private static final int GLFW_K = 75;
    private static final int SDL_K = 14;
    private static final int GLFW_GRAVE = 96;
    private static final int SDL_GRAVE = 53;

    private static String other() {
        return DihKeyCodes.GLFW.equals(DihKeyCodes.CURRENT) ? DihKeyCodes.SDL : DihKeyCodes.GLFW;
    }

    @Test
    void rightShiftRenumbersBothWays() {
        assertEquals(SDL_RSHIFT, DihKeyCodes.convert(GLFW_RSHIFT, DihKeyCodes.GLFW, DihKeyCodes.SDL));
        assertEquals(GLFW_RSHIFT, DihKeyCodes.convert(SDL_RSHIFT, DihKeyCodes.SDL, DihKeyCodes.GLFW));
        assertEquals(SDL_V, DihKeyCodes.convert(GLFW_V, DihKeyCodes.GLFW, DihKeyCodes.SDL));
        assertEquals(GLFW_RSHIFT, DihKeyCodes.convert(GLFW_RSHIFT, DihKeyCodes.GLFW, DihKeyCodes.GLFW));
    }

    @Test
    void noneStaysNone() {
        assertEquals(-1, DihKeyCodes.convert(-1, DihKeyCodes.GLFW, DihKeyCodes.SDL));
        assertEquals(-1, DihKeyCodes.convert(-1, null, DihKeyCodes.GLFW));
    }

    @Test
    void mouseButtonsRenumber() {
        int glfwRight = DihBindUtil.encodeMouseButton(1);
        int sdlRight = DihBindUtil.encodeMouseButton(3);
        int middle = DihBindUtil.encodeMouseButton(2);
        assertEquals(sdlRight, DihKeyCodes.convert(glfwRight, DihKeyCodes.GLFW, DihKeyCodes.SDL));
        assertEquals(glfwRight, DihKeyCodes.convert(sdlRight, DihKeyCodes.SDL, DihKeyCodes.GLFW));
        assertEquals(middle, DihKeyCodes.convert(middle, DihKeyCodes.GLFW, DihKeyCodes.SDL));
        // Side button 4: GLFW 3, SDL 4.
        assertEquals(DihBindUtil.encodeMouseButton(4),
            DihKeyCodes.convert(DihBindUtil.encodeMouseButton(3), DihKeyCodes.GLFW, DihKeyCodes.SDL));
    }

    /** The config that broke Right Shift on 26.3: 26.2 codes kept by an old 26.3 build, plus its own Ctrl+K default. */
    @Test
    void unmarkedConfigReadsGlfwCodesAsGlfwAndTheRestAsSdl() {
        assertEquals(SDL_RSHIFT, DihKeyCodes.convert(GLFW_RSHIFT, null, DihKeyCodes.SDL));
        assertEquals(SDL_V, DihKeyCodes.convert(GLFW_V, null, DihKeyCodes.SDL));
        assertEquals(SDL_GRAVE, DihKeyCodes.convert(GLFW_GRAVE, null, DihKeyCodes.SDL));
        assertEquals(SDL_K, DihKeyCodes.convert(SDL_K, null, DihKeyCodes.SDL));
        // The same file on 26.2: its codes stay, and the SDL Ctrl+K default becomes GLFW K.
        assertEquals(GLFW_RSHIFT, DihKeyCodes.convert(GLFW_RSHIFT, null, DihKeyCodes.GLFW));
        assertEquals(GLFW_K, DihKeyCodes.convert(SDL_K, null, DihKeyCodes.GLFW));
    }

    @Test
    void tableRoundTrips() {
        Set<Integer> seen = new HashSet<>();
        for (int glfw = 0; glfw < 400; glfw++) {
            int sdl = DihKeyCodes.convert(glfw, DihKeyCodes.GLFW, DihKeyCodes.SDL);
            if (sdl == glfw && glfw != 45) continue;
            assertTrue(seen.add(sdl), "two GLFW keys map to SDL " + sdl);
            assertEquals(glfw, DihKeyCodes.convert(sdl, DihKeyCodes.SDL, DihKeyCodes.GLFW));
        }
        assertTrue(seen.size() > 100);
    }

    @Test
    void configFromTheOtherVersionIsRenumberedOnce() {
        String other = other();
        DihConfig config = new DihConfig();
        config.keyCodes = other;
        int savedMenu = DihKeyCodes.GLFW.equals(other) ? GLFW_RSHIFT : SDL_RSHIFT;
        int savedBind = DihKeyCodes.GLFW.equals(other) ? GLFW_GRAVE : SDL_GRAVE;
        config.keybindModuleMenu = savedMenu;
        config.commandBinds.put(savedBind, "home");
        DihConfig.ModuleState state = new DihConfig.ModuleState();
        state.keybind = savedBind;
        state.settings.put("click-key", Integer.toString(DihBindUtil.encodeMouseButton(DihKeyCodes.GLFW.equals(other) ? 1 : 3)));
        state.settings.put("range", "4.5");
        config.modules.put("block-esp", state);

        assertTrue(config.migrateKeyCodes());
        int menu = DihKeyCodes.convert(savedMenu, other, DihKeyCodes.CURRENT);
        int bind = DihKeyCodes.convert(savedBind, other, DihKeyCodes.CURRENT);
        int right = DihBindUtil.encodeMouseButton(DihKeyCodes.GLFW.equals(DihKeyCodes.CURRENT) ? 1 : 3);
        assertEquals(DihKeyCodes.CURRENT, config.keyCodes);
        assertEquals(menu, config.keybindModuleMenu);
        assertEquals("home", config.commandBinds.get(bind));
        assertEquals(bind, state.keybind);
        assertEquals(Integer.toString(right), state.settings.get("click-key"));
        assertEquals("4.5", state.settings.get("range"));

        assertTrue(!config.migrateKeyCodes(), "a config already in this version's numbering is left alone");
        assertEquals(menu, config.keybindModuleMenu);
    }
}
