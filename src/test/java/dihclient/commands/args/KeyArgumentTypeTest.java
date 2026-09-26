package dihclient.commands.args;

//? if <26.3 {
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
//?}

/** {@code .bind} key names. On 26.2 they must be exactly the names and codes the old GLFW reflection produced. */
final class KeyArgumentTypeTest {
    //? if <26.3 {
    @Test
    void everyGlfwKeyNameParsesToItsGlfwCode() throws Exception {
        int checked = 0;
        for (Field f : GLFW.class.getDeclaredFields()) {
            String n = f.getName();
            if (!n.startsWith("GLFW_KEY_") || n.equals("GLFW_KEY_UNKNOWN") || n.equals("GLFW_KEY_LAST")) continue;
            String name = n.substring("GLFW_KEY_".length());
            assertEquals(f.getInt(null), parse(name), name);
            assertEquals(f.getInt(null), parse(name.toLowerCase(Locale.ROOT)), name + " in lower case");
            checked++;
        }
        assertEquals(120, checked, "GLFW key names checked");
    }

    @Test
    void namesRoundTrip() throws CommandSyntaxException {
        assertEquals("LEFT_SHIFT", KeyArgumentType.keyName(parse("LEFT_SHIFT")));
        assertEquals("0", KeyArgumentType.keyName(parse("0")));
        assertEquals("NONE", KeyArgumentType.keyName(-1));
    }

    @Test
    void rejectsUnknownNames() {
        assertThrows(CommandSyntaxException.class, () -> parse("NOT_A_KEY"));
    }

    private static int parse(String name) throws CommandSyntaxException {
        return KeyArgumentType.key().parse(new StringReader(name));
    }
    //?}
}
