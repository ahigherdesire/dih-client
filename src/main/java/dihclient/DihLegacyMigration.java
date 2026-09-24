package dihclient;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * One-time carry-over of user data from the client's previous names. Runs before the game
 * starts so every manager already sees the new files.
 *
 * <p>Copies (never moves) {@code xinyuan*} / {@code autism*} entries in the game and config
 * directories to their {@code dih*} names, recursively renaming nested entries with the same
 * prefix. Anything that already exists under the new name is left untouched, so this is safe to
 * run on every launch and never overwrites newer data. The old files stay for rollback.
 */
public final class DihLegacyMigration implements PreLaunchEntrypoint {

    private static final Logger LOG = LoggerFactory.getLogger("DIH Client");
    private static final String[] OLD_PREFIXES = {"xinyuan", "autism"};

    @Override
    public void onPreLaunch() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            migrateDir(loader.getGameDir());
            migrateDir(loader.getConfigDir());
        } catch (Throwable t) {
            LOG.warn("Couldn't migrate data from the old client name", t);
        }
    }

    static void migrateDir(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path old : (Iterable<Path>) entries::iterator) {
                String renamed = rename(old.getFileName().toString());
                if (renamed == null) continue;
                Path target = dir.resolve(renamed);
                if (Files.exists(target)) continue;
                copy(old, target);
                LOG.info("Migrated {} -> {}", old.getFileName(), renamed);
            }
        }
    }

    /** New name for an entry carrying an old prefix (any case), or {@code null} if it has none. */
    static String rename(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : OLD_PREFIXES) {
            if (lower.startsWith(prefix)) return "dih" + name.substring(prefix.length());
        }
        return null;
    }

    private static void copy(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.createDirectories(to);
        try (Stream<Path> children = Files.list(from)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                String name = child.getFileName().toString();
                String renamed = rename(name);
                copy(child, to.resolve(renamed == null ? name : renamed));
            }
        }
    }
}
