package baritone.api;

import baritone.api.utils.SettingsUtil;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pre-gear settings file has no acquireGearUp line; the new field retains its default. */
final class AcquireSettingsCompatibilityTest {
    @Test
    void oldAcquireLinesLeaveGearAtItsDefault() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Settings settings = new Settings();
        SettingsUtil.parseAndApply(settings, "acquireplacestations", "false");
        SettingsUtil.parseAndApply(settings, "acquirekillmobs", "false");
        assertFalse(settings.acquirePlaceStations.value);
        assertFalse(settings.acquireKillMobs.value);
        assertTrue(settings.acquireGearUp.value);
    }
}
