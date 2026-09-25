package baritone.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ai.json files from older versions must keep loading, and new options must persist. */
final class AiConfigTest {

    @TempDir
    Path dir;

    @Test
    void anOldFileGetsTheNewDefaults() throws Exception {
        Path file = this.dir.resolve("ai.json");
        Files.writeString(file, "{\n  \"model\": \"qwen-max\",\n  \"enabled\": true,\n  \"trusted\": [\"Steve\"]\n}", StandardCharsets.UTF_8);
        AiConfig config = AiConfig.load(file);
        assertEquals("qwen-max", config.model);
        assertTrue(config.enabled);
        assertEquals(List.of("Steve"), config.trusted);
        assertTrue(config.acquireFollowUps);
        assertEquals(AiConfig.DEFAULT_AUTO_FOLLOW_UPS, config.maxAutoFollowUps);
        assertTrue(config.followUpsActive());
        assertFalse(config.isCommandAllowed("ai"), "the default deny list survives");
    }

    @Test
    void followUpOptionsPersist() throws Exception {
        Path file = this.dir.resolve("ai.json");
        AiConfig config = AiConfig.load(file);
        config.acquireFollowUps = false;
        config.maxAutoFollowUps = 2;
        config.save();

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"acquireFollowUps\": false"), json);
        assertFalse(json.contains("MAX_AUTO_FOLLOW_UPS_LIMIT"), "constants are not options");

        AiConfig reloaded = AiConfig.load(file);
        assertFalse(reloaded.acquireFollowUps);
        assertEquals(2, reloaded.maxAutoFollowUps);
        assertFalse(reloaded.followUpsActive());
    }

    @Test
    void theCapIsClampedOnLoad() throws Exception {
        Path file = this.dir.resolve("ai.json");
        Files.writeString(file, "{\"maxAutoFollowUps\": 9999}", StandardCharsets.UTF_8);
        assertEquals(AiConfig.MAX_AUTO_FOLLOW_UPS_LIMIT, AiConfig.load(file).maxAutoFollowUps);

        Files.writeString(file, "{\"maxAutoFollowUps\": -3}", StandardCharsets.UTF_8);
        AiConfig negative = AiConfig.load(file);
        assertEquals(0, negative.maxAutoFollowUps);
        assertFalse(negative.followUpsActive(), "a cap of 0 means no follow-ups");
    }

    @Test
    void aGarbledFileFallsBackToDefaults() throws Exception {
        Path file = this.dir.resolve("ai.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        AiConfig config = AiConfig.load(file);
        assertTrue(config.acquireFollowUps);
        assertFalse(config.enabled);
    }

    @Test
    void denyingAnyNameOfACommandBlocksItsAliases() {
        AiConfig config = new AiConfig();
        config.deniedCommands.add("search");
        assertFalse(config.allowsCommand("s", List.of("search", "s", "blocks")), "alias of a denied command");
        assertFalse(config.allowsCommand("SEARCH", List.of("search", "s", "blocks")));
        assertTrue(config.allowsCommand("goto", List.of("goto")));
        assertFalse(config.allowsCommand("ai", List.of()), "typed name alone");
        assertTrue(config.allowsCommand("unknown", null));
    }
}
