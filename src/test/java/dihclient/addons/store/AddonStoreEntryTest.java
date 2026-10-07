package dihclient.addons.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonStoreEntryTest {
    private static final String SHA = "5a08d31cab6ee491fe80803ac99cbac7c0405807631db82f031ed412ae7ad1e4";
    private static final String REL = "https://github.com/someone/cool-hud/releases/download/v1.2.0/";

    private static JsonObject valid() {
        return JsonParser.parseString("""
            {"id":"cool-hud","name":"Cool HUD","version":"1.2.0","author":"someone","summary":"Short.",
             "description":"A HUD.","apiVersion":3,"license":"MIT","categories":["hud"],
             "source":"https://github.com/someone/cool-hud",
             "files":[
               {"minecraft":"26.2","jar":"%scool-hud-1.2.0.jar","size":12345,"sha256":"%s"},
               {"minecraft":"26.3","jar":"%scool-hud-1.2.0-26.3.jar","size":12346,"sha256":"%s"}]}
            """.formatted(REL, SHA, REL, SHA)).getAsJsonObject();
    }

    private static JsonObject file(JsonObject o) {
        return o.getAsJsonArray("files").get(0).getAsJsonObject();
    }

    private static AddonStoreEntry withFile(String key, String value) {
        JsonObject o = valid();
        file(o).addProperty(key, value);
        return AddonStoreEntry.parse(o);
    }

    private static AddonStoreEntry with(String key, String value) {
        JsonObject o = valid();
        o.addProperty(key, value);
        return AddonStoreEntry.parse(o);
    }

    @Test
    void validEntryHasNoProblems() {
        AddonStoreEntry entry = AddonStoreEntry.parse(valid());
        assertNotNull(entry);
        assertTrue(entry.problems().isEmpty(), entry.problems().toString());
        assertEquals("cool-hud-1.2.0.jar", entry.fileName());
        assertEquals("26.2, 26.3", entry.minecraftVersions());
        assertEquals("Short.", entry.summary());
    }

    @Test
    void summaryFallsBackToDescription() {
        JsonObject o = valid();
        o.remove("summary");
        assertEquals("A HUD.", AddonStoreEntry.parse(o).summary());
    }

    @Test
    void missingFieldDoesNotParse() {
        JsonObject o = valid();
        file(o).remove("sha256");
        assertNull(AddonStoreEntry.parse(o));
    }

    @Test
    void picksTheJarForTheGameVersion() {
        AddonStoreEntry entry = AddonStoreEntry.parse(valid());
        assertEquals("26.2", entry.fileFor("26.2").minecraft());
        assertEquals("26.2", entry.fileFor("26.2.1").minecraft());
        assertEquals("26.3", entry.fileFor("26.3").minecraft());
        assertNull(entry.fileFor("26.20"));
        assertNull(entry.fileFor("27.1"));
    }

    @Test
    void needsAtLeastOneFileAndNoDuplicateVersions() {
        JsonObject empty = valid();
        empty.add("files", new JsonArray());
        assertFalse(AddonStoreEntry.parse(empty).problems().isEmpty());
        assertFalse(withFile("minecraft", "26.3").problems().isEmpty());
    }

    @Test
    void jarMustBeAReleaseAssetOfTheSourceRepo() {
        assertFalse(withFile("jar", "https://evil.example/cool-hud.jar").problems().isEmpty());
        assertFalse(withFile("jar", "https://github.com/other/repo/releases/download/v1/cool-hud.jar").problems().isEmpty());
        assertFalse(withFile("jar", REL + "../../x.jar").problems().isEmpty());
        assertFalse(withFile("jar", REL.replace("https", "http") + "cool-hud.jar").problems().isEmpty());
    }

    @Test
    void imageMustLiveInTheSourceRepo() {
        assertTrue(with("image", "https://raw.githubusercontent.com/someone/cool-hud/main/docs/card.png").problems().isEmpty());
        assertTrue(with("image", REL + "card.jpg").problems().isEmpty());
        assertFalse(with("image", "https://evil.example/card.png").problems().isEmpty());
        assertFalse(with("image", "https://raw.githubusercontent.com/other/repo/main/card.png").problems().isEmpty());
        assertFalse(with("image", "https://raw.githubusercontent.com/someone/cool-hud/main/card.svg").problems().isEmpty());
        assertFalse(with("image", "https://raw.githubusercontent.com/someone/cool-hud/main/../../x/card.png").problems().isEmpty());
        assertEquals("", AddonStoreEntry.parse(valid()).image());
    }

    @Test
    void sourceMustBeGithub() {
        assertFalse(with("source", "https://gitlab.com/someone/cool-hud").problems().isEmpty());
    }

    @Test
    void idsAreSafeAndDihIsReserved() {
        assertFalse(with("id", "../mods").problems().isEmpty());
        assertFalse(with("id", "Cool").problems().isEmpty());
        assertFalse(with("id", "dih-coords").problems().isEmpty());
        assertFalse(with("id", "dih").problems().isEmpty());
    }

    @Test
    void checksumAndVersionAreStrict() {
        assertFalse(withFile("sha256", SHA.toUpperCase()).problems().isEmpty());
        assertFalse(withFile("sha256", "abc").problems().isEmpty());
        assertFalse(with("version", "1.0/../../x").problems().isEmpty());
        assertTrue(with("version", "2.1").problems().isEmpty());
    }

    @Test
    void sizeIsCapped() {
        JsonObject o = valid();
        file(o).addProperty("size", AddonStoreEntry.MAX_JAR_BYTES + 1);
        assertFalse(AddonStoreEntry.parse(o).problems().isEmpty());
    }

    @Test
    void searchMatchesNameAuthorSummaryAndCategory() {
        AddonStoreEntry entry = AddonStoreEntry.parse(valid());
        assertTrue(entry.matches("cool"));
        assertTrue(entry.matches("SOMEONE"));
        assertTrue(entry.matches("short"));
        assertTrue(entry.matches("hud"));
        assertFalse(entry.matches("pathing"));
    }
}
