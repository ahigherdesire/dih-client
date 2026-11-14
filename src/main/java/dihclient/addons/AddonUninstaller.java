package dihclient.addons;

import dihclient.DihClientAddon;
import dihclient.platform.DihLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes a community addon's jar from the mods folder. Fabric keeps mod jars open in a way Windows still allows to be
 * moved, so the jar goes to a trash folder outside {@code mods} right away (the running game keeps its open handle) and
 * the trash is emptied on the next start. The addon stays loaded until the game restarts.
 */
public final class AddonUninstaller {
    private static final Set<String> REMOVED = ConcurrentHashMap.newKeySet();

    private AddonUninstaller() {}

    /** Thrown with a message the player can act on. */
    public static final class UninstallException extends Exception {
        UninstallException(String message) { super(message); }
    }

    private static Path mods() {
        return AddonManager.modsFolder().toPath().toAbsolutePath().normalize();
    }

    private static Path trash() {
        return DihLoader.gameDir().resolve(".dih-addon-trash").toAbsolutePath().normalize();
    }

    /** True once this session removed the addon's jar; it still runs until the restart. */
    public static boolean isRemoved(String modId) {
        return modId != null && REMOVED.contains(modId);
    }

    /** Mark a loaded addon as removed after its jar was taken out some other way. */
    public static void markRemoved(String modId) {
        if (modId != null) REMOVED.add(modId);
    }

    /** Forget a removal, for when the addon is installed again in the same session. */
    public static void clearRemoved(String modId) {
        if (modId != null) REMOVED.remove(modId);
    }

    /** Built-in addons and mods outside the mods folder (dev classpath, jar-in-jar) cannot be uninstalled here. */
    public static boolean canUninstall(String modId) {
        return !BundledAddons.isBundled(modId) && !isRemoved(modId) && !jarsInMods(modId).isEmpty();
    }

    /** The loaded addon's jars that sit directly in the mods folder. */
    public static List<Path> jarsInMods(String modId) {
        List<Path> out = new ArrayList<>();
        if (modId == null || modId.isBlank()) return out;
        Path mods = mods();
        for (Path origin : modPaths().getOrDefault(modId, List.of())) {
            Path p = origin.toAbsolutePath().normalize();
            if (Files.isRegularFile(p) && mods.equals(p.getParent()) && p.getFileName().toString().endsWith(".jar")) {
                out.add(p);
            }
        }
        return out;
    }

    /** Uninstall a loaded addon by id. */
    public static void uninstall(String modId) throws UninstallException {
        if (BundledAddons.isBundled(modId)) throw new UninstallException("Built-in addons cannot be uninstalled; turn it off instead.");
        List<Path> jars = jarsInMods(modId);
        if (jars.isEmpty()) throw new UninstallException("Its jar is not in the mods folder, so remove it where it was installed.");
        for (Path jar : jars) removeJar(jar);
        REMOVED.add(modId);
        DihClientAddon.LOG.info("[Addons] Uninstalled '{}' ({} jar(s)); it unloads on restart", modId, jars.size());
    }

    /**
     * Take one jar out of the mods folder: moved to the trash now if Windows allows it, otherwise deleted by
     * {@link AddonJanitor} as soon as Minecraft closes. Returns true if it is already gone.
     */
    public static boolean removeJar(Path jar) throws UninstallException {
        Path mods = mods();
        Path p = jar.toAbsolutePath().normalize();
        if (!mods.equals(p.getParent()) || !p.getFileName().toString().endsWith(".jar")) {
            throw new UninstallException("Not a jar in the mods folder.");
        }
        try {
            Path trash = trash();
            Files.createDirectories(trash);
            Files.move(p, trash.resolve(System.currentTimeMillis() + "-" + p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            // The game holds the jar open; normal on Windows for a loaded mod.
            DihClientAddon.LOG.info("[Addons] {} is in use; it is deleted when Minecraft closes", p.getFileName());
        }
        AddonJanitor.scheduleDelete(p);
        return false;
    }

    /** At startup: addons whose jar is still waiting to be deleted (the last cleanup did not finish) show as removed. */
    public static void restorePending() {
        if (!AddonJanitor.resumeIfPending()) return;
        for (Map.Entry<String, List<Path>> mod : modPaths().entrySet()) {
            for (Path origin : mod.getValue()) {
                if (AddonJanitor.isScheduled(origin)) {
                    REMOVED.add(mod.getKey());
                    break;
                }
            }
        }
    }

    /** Each loaded mod's files on disk. Only Fabric: the addon store and uninstall are Fabric features. */
    private static Map<String, List<Path>> modPaths() {
        Map<String, List<Path>> out = new java.util.LinkedHashMap<>();
        //? if fabric {
        for (var mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            if (mod.getOrigin().getKind() != net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH) continue;
            out.put(mod.getMetadata().getId(), mod.getOrigin().getPaths());
        }
        //?}
        return out;
    }

    /** Empty the trash left by the last session. Those jars were outside mods at launch, so nothing holds them. */
    public static void purgeTrash() {
        Path trash = trash();
        if (!Files.isDirectory(trash)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(trash, "*.jar")) {
            for (Path file : files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    DihClientAddon.LOG.warn("[Addons] Could not delete {}", file, e);
                }
            }
        } catch (IOException e) {
            DihClientAddon.LOG.warn("[Addons] Could not read the addon trash", e);
        }
    }
}
