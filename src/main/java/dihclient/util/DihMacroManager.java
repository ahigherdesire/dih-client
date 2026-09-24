package dihclient.util;

import dihclient.DihClientAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class DihMacroManager {
    private static DihMacroManager INSTANCE;
    private List<DihMacro> macros = new ArrayList<>();

    private volatile File saveFile;
    private volatile long revision;

    private volatile boolean suppressLanBroadcast = false;

    private DihMacroManager() {
        saveFile = sharedLibraryFile();
        load();
    }

    public static File sharedLibraryFile() {
        return new File(DihClientAddon.FOLDER, "dih_macros.nbt");
    }

    public static void writeEmptyLibrary(File file) {
        if (file == null) return;
        try {
            CompoundTag tag = new CompoundTag();
            tag.put("macros", new ListTag());
            Files.createDirectories(file.toPath().getParent());
            NbtIo.write(tag, file.toPath());
        } catch (Exception e) {
            DihClientAddon.LOG.warn("Could not write empty macro library {}", file, e);
        }
    }

    public static synchronized DihMacroManager get() {
        if (INSTANCE == null) {
            INSTANCE = new DihMacroManager();
        }
        return INSTANCE;
    }

    public synchronized String createUniqueName(String preferredName) {
        String baseName = preferredName == null || preferredName.isBlank() ? "New Macro" : preferredName.trim();
        String candidate = baseName;
        int suffix = 1;
        while (get(candidate) != null) {
            candidate = baseName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    public synchronized DihMacro addImportedCopy(DihMacro source, String preferredName) {
        if (source == null) return null;

        DihMacro copy = source.deepCopy();

        copy.sanitizeForSharing();
        copy.name = createUniqueName(preferredName != null && !preferredName.isBlank() ? preferredName : source.name);
        add(copy);
        return copy;
    }

    public synchronized void add(DihMacro macro) {
        if (macro == null) return;

        DihMacro existing = get(macro.name);
        if (existing != null && existing != macro) removeByIdentity(existing);
        macros.add(macro);
        save();
    }

    private boolean removeByIdentity(DihMacro macro) {
        for (java.util.Iterator<DihMacro> it = macros.iterator(); it.hasNext(); ) {
            if (it.next() == macro) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized DihMacro get(String name) {
        if (name == null) return null;
        for (DihMacro macro : macros) {
            if (macro != null && MacroNames.equal(macro.name, name)) return macro;
        }
        return null;
    }

    public synchronized List<DihMacro> getAll() {
        return new ArrayList<>(macros);
    }

    public long getRevision() {
        return revision;
    }

    public synchronized void remove(DihMacro macro) {
        if (macro == null) return;
        if (dihclient.util.macro.MacroExecutor.isMacroRunning(macro.name)) {
            dihclient.util.macro.MacroExecutor.stopMacro(macro.name);
            DihClientMessaging.sendPrefixed("§eStopped running macro before deletion: " + macro.name);
        }

        if (removeByIdentity(macro)) {
            String deletedName = macro.name == null ? "" : macro.name;
            save();
            DihClientMessaging.sendPrefixed("§aDeleted macro: " + macro.name);

            if (!deletedName.isBlank()) {

                if (!dihclient.util.DihLiteVariant.enabled()) {
                    dihclient.util.multi.MultiProfileManager.get().replaceMacroReferences(deletedName, "");
                    dihclient.util.multi.MultiManager liveMulti = dihclient.util.multi.MultiManager.getIfInitialized();
                    if (liveMulti != null) liveMulti.replaceMacroReference(deletedName, "");
                }
            }

        DihMacroEditorOverlay editor = DihMacroEditorOverlay.getSharedOverlay();
            if (editor != null && editor.isEditingMacro(macro)) {
                editor.close();
            }

            if (!suppressLanBroadcast && DihLANSync.getInstance().isInSession()) {
                DihLANSync.getInstance().broadcastMacroDeletion(macro.name);
            }
        }
    }

    public void delete(DihMacro macro) {
        remove(macro);
    }

    public void executeMacro(String name) {
        DihMacro macro = get(name);
        if (macro != null) {
            macro.execute();
            DihClientMessaging.sendPrefixed("§aExecuting macro: " + macro.name);
        } else {
            DihClientMessaging.sendPrefixed("§cMacro not found: " + name);
        }
    }

    public void stopMacro() {
        if (dihclient.util.multi.MultiTakeoverState.isActive()) {
            dihclient.util.multi.MultiManager multi = dihclient.util.multi.MultiManager.getIfInitialized();
            if (multi != null) {
                dihclient.util.multi.MultiManager.BroadcastResult result =
                    multi.stopMacroOnInteractiveScope(java.util.Set.of());
                DihClientMessaging.sendPrefixed("§eStop POV macro: " + result.summary());
            }
        } else if (dihclient.util.macro.MacroExecutor.isVisibleRunning()) {
            dihclient.util.macro.MacroExecutor.stop();
        } else {
            DihClientMessaging.sendPrefixed("§eNo macro is currently running.");
        }
    }

    public void save() {
        final CompoundTag tag;
        final File target;
        final boolean broadcast;
        synchronized (this) {
            revision++;
            target = saveFile;
            broadcast = !suppressLanBroadcast;
            tag = new CompoundTag();
            ListTag list = new ListTag();
            for (DihMacro macro : macros) {
                if (macro != null) list.add(macro.toTag());
            }
            tag.put("macros", list);
        }

        SaveCoordinator.enqueueLatest("macro-library:" + target.getAbsolutePath(), () -> writeTagAtomically(target, tag));

        if (broadcast && DihLANSync.getInstance().isInSession()) {
            DihLANSync.getInstance().broadcastMacroList();
        }
    }

    private static void writeTagAtomically(File targetFile, CompoundTag tag) {
        Path target = targetFile.toPath();
        Path temp = target.resolveSibling(targetFile.getName() + ".tmp");
        Path backup = target.resolveSibling(targetFile.getName() + ".bak");
        try {
            Files.createDirectories(target.getParent());
            NbtIo.write(tag, temp);
            if (Files.exists(target)) {
                try {
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception backupError) {
                    DihClientAddon.LOG.warn("Could not update macro backup {}; continuing with atomic save", backup, backupError);
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            DihClientAddon.LOG.error("Failed to save Dih macros", e);
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {  }
        }
    }

    public synchronized void switchBackingFile(File newFile, boolean seedIfMissing, boolean announce) {
        if (newFile == null) return;
        File old = this.saveFile;
        if (old != null && old.equals(newFile)) return;
        boolean prevSuppress = this.suppressLanBroadcast;
        this.suppressLanBroadcast = true;
        try {
            save();
            this.saveFile = newFile;
            File backup = new File(newFile.getParentFile(), newFile.getName() + ".bak");
            if (!newFile.exists() && !backup.exists()) {
                if (seedIfMissing) {
                    save();
                } else {
                    macros = new ArrayList<>();
                    revision++;
                }
            } else {
                load();
            }
        } finally {
            this.suppressLanBroadcast = prevSuppress;
        }
        if (announce && !this.suppressLanBroadcast && DihLANSync.getInstance().isInSession()) {
            DihLANSync.getInstance().broadcastMacroList();
        }
    }

    public synchronized void resetToSharedLibrary() {
        switchBackingFile(sharedLibraryFile(), false, true);
    }

    public synchronized File backingFile() {
        return saveFile;
    }

    public synchronized void load() {
        Path target = saveFile.toPath();
        Path backup = target.resolveSibling(saveFile.getName() + ".bak");
        if (!Files.exists(target) && !Files.exists(backup)) return;

        try {
            if (!Files.exists(target)) throw new IllegalStateException("Main macro file is missing");
            macros = loadFile(target);
            revision++;
        } catch (Exception e) {
            DihClientAddon.LOG.error("Failed to load Dih macros; trying backup", e);
            if (!Files.exists(backup)) return;
            try {
                macros = loadFile(backup);
                revision++;
                DihClientAddon.LOG.warn("Recovered Dih macros from {}", backup);
            } catch (Exception backupError) {
                DihClientAddon.LOG.error("Failed to load Dih macro backup", backupError);
            }
        }
    }

    private List<DihMacro> loadFile(Path path) throws Exception {
        CompoundTag tag = NbtIo.read(path);
        if (tag == null) throw new IllegalStateException("Macro file was empty");
        if (!(tag.get("macros") instanceof ListTag list)) {
            throw new IllegalStateException("Macro file has no macro list");
        }
        List<DihMacro> loaded = new ArrayList<>();

        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag macroTag)) continue;
            try {
                DihMacro macro = new DihMacro().fromTag(macroTag);
                Integer previous = seen.get(MacroNames.key(macro.name));
                if (previous != null) {
                    DihClientAddon.LOG.warn("Dropping macro '{}' shadowed by a name that differs only in case",
                        loaded.get(previous).name);
                    loaded.set(previous, macro);
                } else {
                    seen.put(MacroNames.key(macro.name), loaded.size());
                    loaded.add(macro);
                }
            } catch (Throwable macroError) {
                DihClientAddon.LOG.warn("Skipping one damaged macro entry from {}", path, macroError);
            }
        }
        return loaded;
    }
}
