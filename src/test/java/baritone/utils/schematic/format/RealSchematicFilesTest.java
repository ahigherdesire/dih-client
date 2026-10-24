package baritone.utils.schematic.format;

import baritone.utils.schematic.LoadedSchematic;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Loads every schematic file found on this machine, read-only. The folders are the TLauncher schematics folder and any
 * listed in the {@code DIH_REAL_SCHEMATICS} environment variable (separated by {@link File#pathSeparator}). Skipped when
 * there are none, so it passes on any machine.
 */
final class RealSchematicFilesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void everyRealSchematicLoads() {
        List<File> files = new ArrayList<>();
        for (File dir : folders()) {
            File[] listed = dir.listFiles();
            if (listed == null) {
                continue;
            }
            for (File file : listed) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (file.isFile() && (name.endsWith(".litematic") || name.endsWith(".schematic")
                    || name.endsWith(".schem") || name.endsWith(".nbt"))) {
                    files.add(file);
                }
            }
        }
        assumeFalse(files.isEmpty(), "no real schematic files on this machine");

        List<String> failures = new ArrayList<>();
        for (File file : files) {
            try {
                LoadedSchematic s = SchematicLoader.load(file);
                System.out.printf("REAL-SCHEMATIC OK   %s: %s, %dx%dx%d, %d blocks, %d block entities, %d regions, warnings=%s%n",
                    file, s.format(), s.widthX(), s.heightY(), s.lengthZ(), s.blockCount(), s.blockEntities().size(),
                    s.regions().size(), s.warnings());
                if (s.blockCount() == 0) {
                    failures.add(file + ": loaded with no blocks");
                }
            } catch (Exception e) {
                System.out.printf("REAL-SCHEMATIC FAIL %s: %s%n", file, e);
                failures.add(file + ": " + e);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static List<File> folders() {
        List<File> dirs = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            dirs.add(new File(appData, ".tlauncher/legacy/Minecraft/game/schematics"));
        }
        String extra = System.getenv("DIH_REAL_SCHEMATICS");
        if (extra != null) {
            for (String part : extra.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    dirs.add(new File(part.trim()));
                }
            }
        }
        return dirs;
    }
}
