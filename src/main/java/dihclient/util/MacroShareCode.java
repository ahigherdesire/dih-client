package dihclient.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Share codes for macros: {@code DIHM1:} followed by base64 of the gzipped NBT of one macro or of a whole folder.
 * Codes carry no keybinds and are sanitized like any other shared macro. A code over 64 KB, from a newer format, or
 * using step types this build doesn't have is refused with a message that says why.
 */
public final class MacroShareCode {
    public static final String PREFIX = "DIHM1:";
    public static final int MAX_CODE_CHARS = 64 * 1024;
    private static final int VERSION = 1;
    private static final long MAX_NBT_BYTES = 4L * 1024 * 1024;

    private MacroShareCode() {
    }

    /** What a code contained: the macros, and the folder name when a whole folder was shared (else empty). */
    public record Decoded(List<DihMacro> macros, String folder) {
    }

    public static final class InvalidCodeException extends Exception {
        public InvalidCodeException(String message) {
            super(message);
        }
    }

    /** A code for one macro. */
    public static String encode(DihMacro macro) throws InvalidCodeException {
        return encode(List.of(macro), "");
    }

    /** A code for a folder of macros ({@code folder} non-empty) or for loose macros. */
    public static String encode(List<DihMacro> macros, String folder) throws InvalidCodeException {
        if (macros == null || macros.isEmpty()) throw new InvalidCodeException("There is nothing to share");
        CompoundTag root = new CompoundTag();
        root.putInt("v", VERSION);
        String cleanFolder = DihMacro.normalizeFolder(folder);
        if (!cleanFolder.isEmpty()) root.putString("folder", cleanFolder);
        ListTag list = new ListTag();
        for (DihMacro macro : macros) {
            if (macro == null) continue;
            CompoundTag tag = macro.toShareableTag();
            tag.putInt("keyCode", -1);
            list.add(tag);
        }
        root.put("macros", list);
        String code;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            code = PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new InvalidCodeException("Could not write the code: " + e.getMessage());
        }
        if (code.length() > MAX_CODE_CHARS) {
            throw new InvalidCodeException("That is too big for one code (" + code.length() / 1024
                + " KB, the limit is 64 KB). Share fewer macros at a time");
        }
        return code;
    }

    public static Decoded decode(String code) throws InvalidCodeException {
        if (code == null) throw new InvalidCodeException("Paste a code that starts with " + PREFIX);
        String compact = code.replaceAll("\\s+", "");
        if (!compact.startsWith(PREFIX)) {
            throw new InvalidCodeException("That isn't a DIH macro code (they start with " + PREFIX + ")");
        }
        if (compact.length() > MAX_CODE_CHARS) {
            throw new InvalidCodeException("That code is over the 64 KB limit, so it wasn't imported");
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(compact.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new InvalidCodeException("That code is damaged (it may have been cut off when copying)");
        }
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(MAX_NBT_BYTES));
        } catch (Exception e) {
            throw new InvalidCodeException("That code is damaged (it may have been cut off when copying)");
        }
        int version = root.getIntOr("v", 0);
        if (version != VERSION) {
            throw new InvalidCodeException(version > VERSION
                ? "That code was made by a newer DIH. Update to import it"
                : "That code's format isn't supported");
        }
        ListTag list = root.getList("macros").orElse(new ListTag());
        Set<String> unknown = new TreeSet<>();
        List<CompoundTag> macroTags = new ArrayList<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag macroTag)) continue;
            for (Tag action : macroTag.getList("actions").orElse(new ListTag())) {
                String type = action instanceof CompoundTag actionTag ? actionTag.getStringOr("type", "") : "";
                if (!DihMacro.isBuiltInActionType(type)) unknown.add(type.isEmpty() ? "(no type)" : type);
            }
            macroTags.add(macroTag);
        }
        if (!unknown.isEmpty()) {
            throw new InvalidCodeException("That code uses steps this DIH doesn't have: " + String.join(", ", unknown)
                + ". It may need a newer DIH or an addon");
        }
        if (macroTags.isEmpty()) throw new InvalidCodeException("That code has no macros in it");
        String folder = DihMacro.normalizeFolder(root.getStringOr("folder", ""));
        List<DihMacro> macros = new ArrayList<>();
        for (CompoundTag macroTag : macroTags) {
            DihMacro macro = new DihMacro().fromTag(macroTag).sanitizeForSharing();
            macro.keyCode = -1;
            if (!folder.isEmpty()) macro.folder = folder;
            macros.add(macro);
        }
        return new Decoded(macros, folder);
    }
}
