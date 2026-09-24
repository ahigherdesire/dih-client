package dihclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dihclient.util.macro.MacroAction;
import dihclient.util.macro.PayloadAction;
import dihclient.util.macro.SignEditAction;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class UntrustedMacroImportTest {

    private static CompoundTag typed(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("type", type);
        return t;
    }

    @Test
    void stripToBuiltInActionsDropsAddonAndUnknownBeforeDeserialize() {
        ListTag actions = new ListTag();
        actions.add(typed("DELAY"));
        actions.add(typed("PAYLOAD"));
        CompoundTag weaponized = typed("someaddon:rce");
        weaponized.putString("commandToRun", "cmd /c copy evil.bat %APPDATA%\\...\\Startup");
        actions.add(weaponized);
        actions.add(typed("unknown:thing"));
        CompoundTag macro = new CompoundTag();
        macro.put("actions", actions);

        int dropped = DihMacro.stripToBuiltInActions(macro);

        assertEquals(2, dropped);
        ListTag kept = (ListTag) macro.get("actions");
        assertEquals(2, kept.size());
        for (int i = 0; i < kept.size(); i++) {
            String type = ((CompoundTag) kept.get(i)).getStringOr("type", "");
            assertTrue(DihMacro.isBuiltInActionType(type), "unexpected surviving type: " + type);
        }
    }

    @Test
    void isBuiltInActionTypeRejectsAddonAndUnknown() {
        assertTrue(DihMacro.isBuiltInActionType("PAYLOAD"));
        assertTrue(DihMacro.isBuiltInActionType("SIGN_EDIT"));
        assertFalse(DihMacro.isBuiltInActionType("someaddon:custom"));
        assertFalse(DihMacro.isBuiltInActionType("unknown:thing"));
        assertFalse(DihMacro.isBuiltInActionType(""));
        assertFalse(DihMacro.isBuiltInActionType(null));
    }

    @Test
    void untrustedImportStripsKeybindButKeepsBuiltInCommandActions() throws Exception {

        PayloadAction payload = new PayloadAction();
        payload.payloadClassName = "net.minecraft.SomeKnownPayload";
        SignEditAction sign = new SignEditAction();
        sign.sendCommandAfter = true;
        sign.commandAfter = "/warp home";

        ListTag actions = new ListTag();
        actions.add(DihMacro.serializeAction(payload));
        actions.add(DihMacro.serializeAction(sign));
        CompoundTag macroTag = new CompoundTag();
        macroTag.putInt("keyCode", 50);
        macroTag.put("actions", actions);

        DihMacro imported = DihClipboardHelper.deserializeMacroFromBase64(encodeMacro(macroTag));

        assertNotNull(imported);
        assertEquals(-1, imported.keyCode, "imported macro must not carry a keybind (no 0-click auto-run)");
        assertEquals(2, imported.actions.size());

        PayloadAction importedPayload = (PayloadAction) find(imported, PayloadAction.class);
        SignEditAction importedSign = (SignEditAction) find(imported, SignEditAction.class);
        assertEquals("net.minecraft.SomeKnownPayload", importedPayload.payloadClassName);
        assertEquals("/warp home", importedSign.commandAfter);
        assertTrue(importedSign.sendCommandAfter);
    }

    private static MacroAction find(DihMacro macro, Class<?> type) {
        return macro.actions.stream().filter(type::isInstance).findFirst().orElseThrow();
    }

    private static String encodeMacro(CompoundTag macroTag) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.putString("type", "dih_macro");
        root.put("macro", macroTag);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
