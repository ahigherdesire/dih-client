package baritone.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Memory per world (a singleplayer save or a server) plus a small global memory for the player's preferences. Files
 * live in {@code baritone/ai_memory/<key>.json}; the old single {@code ai_memory.json} moves into the global one once.
 */
public final class AiMemories {

    public static final String GLOBAL = "global";

    private final Path dir;
    private final AiMemory global;
    private String worldKey = GLOBAL;
    private AiMemory world;

    public AiMemories(Path dir, Path legacyFile) {
        this.dir = dir;
        Path globalFile = dir.resolve(GLOBAL + ".json");
        if (legacyFile != null && Files.exists(legacyFile) && !Files.exists(globalFile)) {
            try {
                Files.createDirectories(dir);
                Files.copy(legacyFile, globalFile);
                Files.move(legacyFile, legacyFile.resolveSibling(legacyFile.getFileName() + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("[DIH] could not move ai_memory.json into ai_memory/: " + e.getMessage());
            }
        }
        this.global = new AiMemory(globalFile);
        this.world = this.global;
    }

    /** The memory key for a singleplayer save folder or a server address; "global" when neither is known. */
    public static String worldKey(String singleplayerFolder, String serverAddress) {
        if (singleplayerFolder != null && !singleplayerFolder.isBlank()) {
            String clean = clean(singleplayerFolder);
            return clean.isEmpty() ? GLOBAL : "sp-" + clean;
        }
        if (serverAddress != null && !serverAddress.isBlank()) {
            String clean = clean(serverAddress);
            return clean.isEmpty() ? GLOBAL : "mp-" + clean;
        }
        return GLOBAL;
    }

    private static String clean(String text) {
        return text.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
    }

    /** Switches to {@code key}'s memory (loading it); the global key means no world. */
    public synchronized void setWorld(String key) {
        String next = key == null || key.isBlank() ? GLOBAL : key;
        if (next.equals(this.worldKey)) return;
        this.worldKey = next;
        this.world = next.equals(GLOBAL) ? this.global : new AiMemory(this.dir.resolve(next + ".json"));
    }

    public synchronized String worldKey() {
        return this.worldKey;
    }

    public AiMemory global() {
        return this.global;
    }

    /** This world's memory; the global one outside a world. */
    public synchronized AiMemory world() {
        return this.world;
    }

    /** What goes in a prompt: the player's preferences, then this world's facts. */
    public synchronized String digest() {
        if (this.world == this.global) return this.global.digest();
        String global = this.global.all().isEmpty() ? "" : "About the player:\n" + this.global.digest() + "\n";
        return global + "About this world:\n" + this.world.digest();
    }
}
