package dihclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class DihDupeRadarTest {
    @Test
    void versionedProviderNamesMatchScannerPluginNames() {
        assertEquals(
            DihPluginNameMatcher.normalizeVersionless("quickshop-hikari"),
            DihPluginNameMatcher.normalizeVersionless("QuickShop-Hikari 6.2.0.10")
        );
        assertEquals(
            DihPluginNameMatcher.normalizeVersionless("topminions"),
            DihPluginNameMatcher.normalizeVersionless("TOPMINIONS-v2.6.1")
        );
        assertEquals(
            DihPluginNameMatcher.normalizeVersionless("protocollib"),
            DihPluginNameMatcher.normalizeVersionless("ProtocolLib_5.4.0-SNAPSHOT")
        );
    }

    @Test
    void officialPluginReferenceShapePreservesNameAndVersion() {
        var card = JsonParser.parseString("""
            {
              "name": "QuickShop-Hikari Balance Transfer Exploit",
              "status": "verified",
              "plugin_name": "QuickShop-Hikari",
              "plugin_version": "6.2.0.10",
              "plugins": [{"name": "QuickShop-Hikari", "version": "6.2.0.10"}]
            }
            """).getAsJsonObject();

        List<String> references = DihPluginNameMatcher.extractProviderReferences(card);
        assertTrue(references.contains("QuickShop-Hikari 6.2.0.10"));
        assertEquals(
            "QuickShop-Hikari 6.2.0.10",
            DihPluginNameMatcher.bestMatchingReference("quickshop-hikari", references, 0.90D)
        );
    }

    @Test
    void fuzzyReferenceValidationRejectsUnrelatedPlugins() {
        assertEquals(
            null,
            DihPluginNameMatcher.bestMatchingReference(
                "quickshop-hikari",
                List.of("ExcellentCrates 6.0.0", "WorldGuard 7.0.14"),
                0.90D
            )
        );
    }
}
