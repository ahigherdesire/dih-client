package dihclient.util;

import dihclient.util.macro.DelayAction;
import dihclient.util.macro.SendChatAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihMacroFolderTest {

    @Test
    void folderRoundTripsThroughNbt() {
        DihMacro macro = new DihMacro("farm");
        macro.folder = "Skyblock";
        macro.actions = new ArrayList<>(List.of(new DelayAction()));

        DihMacro back = new DihMacro().fromTag(macro.toTag());

        assertEquals("Skyblock", back.folder);
        assertTrue(back.hasFolder());
        assertEquals("farm", back.name);
        assertEquals(1, back.actions.size());
    }

    @Test
    void anUnfolderedMacroWritesNoFolderKey() {
        DihMacro macro = new DihMacro("plain");
        assertFalse(macro.toTag().contains("folder"));
        assertFalse(new DihMacro().fromTag(macro.toTag()).hasFolder());
    }

    @Test
    void folderNamesAreCleanedUp() {
        assertEquals("", DihMacro.normalizeFolder(null));
        assertEquals("", DihMacro.normalizeFolder("   "));
        assertEquals("a b", DihMacro.normalizeFolder("  a   b "));
        assertEquals("pvp kits", DihMacro.normalizeFolder("pvp/kits"));
        assertEquals(40, DihMacro.normalizeFolder("x".repeat(90)).length());
    }

    /** A macro library written before folders existed, in that version's exact layout. */
    @Test
    void aMacroFileFromBeforeFoldersStillLoads(@TempDir Path dir) throws Exception {
        CompoundTag chat = new CompoundTag();
        chat.putString("type", "SEND_CHAT");
        chat.putString("message", "/home");
        CompoundTag delay = new CompoundTag();
        delay.putString("type", "DELAY");
        delay.putInt("delayTicks", 20);
        ListTag actions = new ListTag();
        actions.add(chat);
        actions.add(delay);
        CompoundTag old = new CompoundTag();
        old.putString("name", "go home");
        old.putString("description", "old");
        old.putBoolean("loop", false);
        old.putInt("loopCount", -1);
        old.putInt("keyCode", 72);
        old.put("actions", actions);
        ListTag macros = new ListTag();
        macros.add(old);
        CompoundTag library = new CompoundTag();
        library.put("macros", macros);
        Path file = dir.resolve("dih_macros.nbt");
        NbtIo.write(library, file);

        CompoundTag read = NbtIo.read(file);
        List<DihMacro> loaded = new ArrayList<>();
        for (Tag element : read.getList("macros").orElseThrow()) loaded.add(new DihMacro().fromTag((CompoundTag) element));

        assertEquals(1, loaded.size());
        DihMacro macro = loaded.get(0);
        assertEquals("go home", macro.name);
        assertEquals("", macro.folder);
        assertFalse(macro.hasFolder());
        assertEquals(72, macro.keyCode);
        assertEquals("/home", assertInstanceOf(SendChatAction.class, macro.actions.get(0)).message);
        assertInstanceOf(DelayAction.class, macro.actions.get(1));
    }
}
