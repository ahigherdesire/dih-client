package dihclient.script;

import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.utils.BetterBlockPos;
import dihclient.script.lang.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptChatTest {
    /** Host whose ticks deliver queued chat lines, one per tick. */
    static final class ChatHost implements ScriptHost {
        final List<String> out = new ArrayList<>();
        final List<String> arriving = new ArrayList<>();
        int ticks;

        public void waitTicks(int t) {
            for (int i = 0; i < t; i++) {
                ticks++;
                if (!arriving.isEmpty()) ScriptChat.record(arriving.removeFirst());
            }
        }
        public boolean isCancelled() { return false; }
        public void print(String message) { out.add(message); }
    }

    private static final Library LIBRARY = new Library().include(CoreLibrary.create()).include(GameLibrary.create());

    @BeforeEach
    void reset() {
        ScriptChat.clearForTests();
    }

    private static ChatHost run(String src, String... arriving) {
        ChatHost host = new ChatHost();
        host.arriving.addAll(List.of(arriving));
        new Interpreter(Parser.parse(src), LIBRARY, host).run();
        return host;
    }

    @Test
    void chatFromBeforeTheScriptStartedIsIgnored() throws Exception {
        ScriptChat.record("Steve has requested to teleport to you");
        Thread.sleep(2);
        ChatHost h = run("function main() { print(chatSeen(\"teleport\"), lastChat()); }");
        assertEquals(List.of("false null"), h.out);
    }

    @Test
    void chatSeenIgnoresCapitalsColoursAndUsesEachMessageOnce() {
        ChatHost h = run("""
            function main() {
                wait(2);
                print(chatSeen("TPA"), chatSeen("tpa"), chatSeen("hello"));
            }
            """, "§eSteve: /tpa please", "<Alex> hello");
        assertEquals(List.of("true false true"), h.out);
    }

    @Test
    void separateSearchesDoNotUseUpEachOthersMessages() {
        ChatHost h = run("""
            function main() {
                wait(1);
                print(chatSeen("steve"), chatSeen("joined"), chatSeen("steve"));
            }
            """, "Steve joined the game");
        assertEquals(List.of("true true false"), h.out);
    }

    @Test
    void waitForChatReturnsTheWholeMessage() {
        ChatHost h = run("function main() { print(waitForChat(\"teleport\")); print(lastChat()); }",
            "Server restarting soon", "Steve has requested to teleport to you");
        assertEquals(List.of("Steve has requested to teleport to you", "Steve has requested to teleport to you"), h.out);
    }

    @Test
    void waitForChatGivesUpAfterTheTimeout() {
        ChatHost h = run("function main() { print(waitForChat(\"never\", 1)); }", "something else");
        assertEquals(List.of("null"), h.out);
        assertEquals(20, h.ticks);
    }

    @Test
    void emptyTextIsAnError() {
        assertThrows(ScriptException.class, () -> run("function main() { chatSeen(\"  \"); }"));
    }

    @Test
    void waypointNamesBeatTagsAndTagsAreTheFallback() {
        var named = new Waypoint("home", IWaypoint.Tag.USER, new BetterBlockPos(1, 64, 1), 100);
        var bed = new Waypoint("bed", IWaypoint.Tag.HOME, new BetterBlockPos(2, 64, 2), 200);
        var oldBase = new Waypoint("Base", IWaypoint.Tag.USER, new BetterBlockPos(3, 64, 3), 50);
        var newBase = new Waypoint("base", IWaypoint.Tag.USER, new BetterBlockPos(4, 64, 4), 300);
        var death = new Waypoint("death", IWaypoint.Tag.DEATH, new BetterBlockPos(5, 10, 5), 400);
        List<IWaypoint> all = List.of(named, bed, oldBase, newBase, death);
        assertEquals(new BlockPos(1, 64, 1), GameLibrary.findWaypoint(all, "home"));
        assertEquals(new BlockPos(4, 64, 4), GameLibrary.findWaypoint(all, "BASE"));
        assertEquals(new BlockPos(5, 10, 5), GameLibrary.findWaypoint(all, "death"));
        assertEquals(new BlockPos(2, 64, 2), GameLibrary.findWaypoint(List.of(bed, death), "home"));
        assertNull(GameLibrary.findWaypoint(all, "nether"));
    }
}
