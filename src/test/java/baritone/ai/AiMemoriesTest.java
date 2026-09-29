package baritone.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Memory per world plus a small global one, and the old single file moving into the global one. */
final class AiMemoriesTest {

    @TempDir
    Path dir;

    @Test
    void worldKeysNameTheSaveOrTheServer() {
        assertEquals("sp-new_world", AiMemories.worldKey("New World", null));
        assertEquals("mp-play.example.net_25565", AiMemories.worldKey(null, "Play.Example.net:25565"));
        assertEquals("sp-a_b", AiMemories.worldKey("../a/b", null), "no path tricks");
        assertEquals("global", AiMemories.worldKey(null, null));
        assertEquals("global", AiMemories.worldKey("  ", ""));
    }

    @Test
    void eachWorldRemembersItsOwnFacts() {
        AiMemories memories = new AiMemories(this.dir.resolve("ai_memory"), this.dir.resolve("ai_memory.json"));
        memories.global().remember("The player likes oak.");
        memories.setWorld("sp-one");
        memories.world().remember("Base at 0 64 0.");
        memories.setWorld("mp-two");
        memories.world().remember("Spawn is at 100 70 100.");
        assertEquals(List.of("Spawn is at 100 70 100."), memories.world().all());
        String digest = memories.digest();
        assertTrue(digest.contains("The player likes oak.") && digest.contains("Spawn is at 100 70 100."), digest);
        assertFalse(digest.contains("Base at 0 64 0."), "another world's facts stay out");

        AiMemories reloaded = new AiMemories(this.dir.resolve("ai_memory"), this.dir.resolve("ai_memory.json"));
        reloaded.setWorld("sp-one");
        assertEquals(List.of("Base at 0 64 0."), reloaded.world().all());
        assertEquals(List.of("The player likes oak."), reloaded.global().all());
        assertTrue(Files.exists(this.dir.resolve("ai_memory").resolve("sp-one.json")));
    }

    @Test
    void theOldFileMovesIntoGlobalMemoryOnce() throws IOException {
        Path legacy = this.dir.resolve("ai_memory.json");
        Files.writeString(legacy, "[\"Old fact one.\", \"Old fact two.\"]", StandardCharsets.UTF_8);
        AiMemories memories = new AiMemories(this.dir.resolve("ai_memory"), legacy);
        assertEquals(List.of("Old fact one.", "Old fact two."), memories.global().all());
        assertFalse(Files.exists(legacy), "moved, not copied");
        assertTrue(Files.exists(this.dir.resolve("ai_memory.json.migrated")));

        memories.global().forget("one");
        AiMemories again = new AiMemories(this.dir.resolve("ai_memory"), legacy);
        assertEquals(List.of("Old fact two."), again.global().all(), "migration doesn't run twice");
    }
}
