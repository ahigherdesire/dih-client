package dihclient.util;

import dihclient.util.macro.DelayAction;
import dihclient.util.macro.SendChatAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroShareCodeTest {

    private static DihMacro macro(String name, String message) {
        DihMacro macro = new DihMacro(name);
        SendChatAction chat = new SendChatAction();
        chat.message = message;
        macro.actions = new ArrayList<>(List.of(chat, new DelayAction()));
        macro.keyCode = 71;
        return macro;
    }

    private static String rawCode(CompoundTag root) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return MacroShareCode.PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void oneMacroRoundTripsWithoutItsKeybind() throws Exception {
        String code = MacroShareCode.encode(macro("hello", "/say hi"));

        assertTrue(code.startsWith("DIHM1:"));
        MacroShareCode.Decoded decoded = MacroShareCode.decode(code);
        assertEquals("", decoded.folder());
        assertEquals(1, decoded.macros().size());
        DihMacro back = decoded.macros().get(0);
        assertEquals("hello", back.name);
        assertEquals(-1, back.keyCode);
        assertEquals("/say hi", assertInstanceOf(SendChatAction.class, back.actions.get(0)).message);
        assertInstanceOf(DelayAction.class, back.actions.get(1));
    }

    @Test
    void aFolderRoundTripsAndKeepsItsName() throws Exception {
        DihMacro a = macro("a", "one");
        DihMacro b = macro("b", "two");
        a.folder = "Kits";
        b.folder = "Kits";

        MacroShareCode.Decoded decoded = MacroShareCode.decode(MacroShareCode.encode(List.of(a, b), "Kits"));

        assertEquals("Kits", decoded.folder());
        assertEquals(List.of("a", "b"), decoded.macros().stream().map(m -> m.name).toList());
        assertTrue(decoded.macros().stream().allMatch(m -> "Kits".equals(m.folder)));
    }

    @Test
    void pastedCodesMayContainLineBreaks() throws Exception {
        String code = MacroShareCode.encode(macro("wrap", "x"));
        String wrapped = code.substring(0, 20) + "\n  " + code.substring(20);
        assertEquals("wrap", MacroShareCode.decode(wrapped).macros().get(0).name);
    }

    @Test
    void codesOverSixtyFourKilobytesAreRefused() throws Exception {
        // Random letters barely compress, so this many macros can't fit in one code.
        Random random = new Random(1);
        List<DihMacro> many = new ArrayList<>();
        for (int m = 0; m < 500; m++) {
            StringBuilder noise = new StringBuilder();
            for (int i = 0; i < 200; i++) noise.append((char) ('a' + random.nextInt(26)));
            many.add(macro("m" + m, noise.toString()));
        }
        MacroShareCode.InvalidCodeException tooBig = assertThrows(MacroShareCode.InvalidCodeException.class,
            () -> MacroShareCode.encode(many, "Huge"));
        assertTrue(tooBig.getMessage().contains("64 KB"), tooBig.getMessage());

        String oversized = MacroShareCode.PREFIX + "A".repeat(MacroShareCode.MAX_CODE_CHARS);
        MacroShareCode.InvalidCodeException refused = assertThrows(MacroShareCode.InvalidCodeException.class,
            () -> MacroShareCode.decode(oversized));
        assertTrue(refused.getMessage().contains("64 KB"), refused.getMessage());
    }

    @Test
    void unknownStepTypesAreNamedAndRefused() throws Exception {
        CompoundTag good = new CompoundTag();
        good.putString("type", "DELAY");
        CompoundTag addon = new CompoundTag();
        addon.putString("type", "someaddon:explode");
        CompoundTag future = new CompoundTag();
        future.putString("type", "TIME_TRAVEL");
        ListTag actions = new ListTag();
        actions.add(good);
        actions.add(addon);
        actions.add(future);
        CompoundTag macroTag = new CompoundTag();
        macroTag.putString("name", "sus");
        macroTag.put("actions", actions);
        ListTag macros = new ListTag();
        macros.add(macroTag);
        CompoundTag root = new CompoundTag();
        root.putInt("v", 1);
        root.put("macros", macros);

        MacroShareCode.InvalidCodeException e = assertThrows(MacroShareCode.InvalidCodeException.class,
            () -> MacroShareCode.decode(rawCode(root)));
        assertTrue(e.getMessage().contains("TIME_TRAVEL") && e.getMessage().contains("someaddon:explode"), e.getMessage());
    }

    @Test
    void otherTextIsRefusedWithAReason() throws Exception {
        assertThrows(MacroShareCode.InvalidCodeException.class, () -> MacroShareCode.decode("hello"));
        assertThrows(MacroShareCode.InvalidCodeException.class, () -> MacroShareCode.decode("DIHM1:@@@not-base64@@@"));
        assertThrows(MacroShareCode.InvalidCodeException.class, () -> MacroShareCode.decode("DIHM1:aGVsbG8="));
        CompoundTag newer = new CompoundTag();
        newer.putInt("v", 2);
        MacroShareCode.InvalidCodeException e = assertThrows(MacroShareCode.InvalidCodeException.class,
            () -> MacroShareCode.decode(rawCode(newer)));
        assertTrue(e.getMessage().contains("newer"), e.getMessage());
    }
}
