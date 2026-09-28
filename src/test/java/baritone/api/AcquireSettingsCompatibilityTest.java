package baritone.api;

import baritone.api.utils.SettingsUtil;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pre-gear settings file has no acquireGearUp line; the new field retains its default. */
final class AcquireSettingsCompatibilityTest {
    @Test
    void oldAcquireLinesLeaveGearAtItsDefault(@TempDir Path folder) throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path file = folder.resolve("settings.txt");
        Files.writeString(file, "acquirePlaceStations false\nacquireKillMobs false\n");
        Settings settings = new Settings();
        SettingsUtil.readAndApply(settings, file);
        assertFalse(settings.acquirePlaceStations.value);
        assertFalse(settings.acquireKillMobs.value);
        assertTrue(settings.acquireGearUp.value);
    }
}
