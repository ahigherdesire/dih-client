package baritone.ai;

import baritone.acquire.AcquireControl;
import baritone.acquire.AcquireControl.AcquireEvent.Kind;
import baritone.ai.AcquireFollowUps.Action;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The acquire and plan_item tools against a fake executor, minus the game-thread hop. */
final class AcquireToolTest {

    private FakeAcquireControl control;
    private AcquireFollowUps followUps;
    private List<Action> actions;
    private AcquireControl previous;

    @BeforeEach
    void setUp() {
        this.previous = AcquireControl.get();
        this.control = new FakeAcquireControl();
        this.followUps = new AcquireFollowUps();
        this.actions = new ArrayList<>();
        this.followUps.attach(this.control,
                event -> this.actions.add(this.followUps.onEvent(event, System.currentTimeMillis(), true, 6)));
    }

    @AfterEach
    void restore() {
        AcquireControl.set(this.previous);
    }

    private static ItemRequest request(String item, int count) {
        JsonObject args = new JsonObject();
        args.addProperty("item", item);
        args.addProperty("count", count);
        return ItemRequest.parse(new LlmClient.ToolCall("call_1", "acquire", args));
    }

    private Action lastAction() {
        return this.actions.get(this.actions.size() - 1);
    }

    @Test
    void startPassesTheRequestThroughAndClaimsTheAcquire() {
        String result = AiTools.startAcquire(this.control, this.followUps, request("iron_ingot", 3), true);
        assertTrue(result.startsWith("Started: Acquiring."), result);
        assertTrue(result.contains("[event]"), "tells the model not to poll");
        assertEquals("iron_ingot", this.control.lastItem);
        assertEquals(3, this.control.lastCount);
        assertTrue(this.followUps.ownsCurrent());

        this.control.fire(Kind.DONE, "");
        assertEquals(Action.FOLLOW_UP, lastAction());
        assertEquals("[event] acquire of 3 iron_ingot is DONE", this.followUps.drainNotes());
    }

    @Test
    void anAsynchronousExecutorIsClaimedToo() {
        this.control.fireStartedSynchronously = false;
        AiTools.startAcquire(this.control, this.followUps, request("torch", 16), true);
        this.control.fire(Kind.STARTED, "");
        this.control.fire(Kind.FAILED, "no coal");
        assertEquals(Action.FOLLOW_UP, lastAction());
        assertTrue(this.followUps.drainNotes().contains("FAILED: no coal"));
    }

    @Test
    void anAcquireStartedByHandNeverFollowsUp() {
        this.control.start("iron_pickaxe", 1); // the user typed #acquire iron_pickaxe
        this.control.fire(Kind.DONE, "");
        assertEquals(Action.IGNORE, lastAction());
        assertFalse(this.followUps.isFollowUpOwed());
    }

    @Test
    void aUserAcquireAfterTheAisOneIsStillIgnored() {
        AiTools.startAcquire(this.control, this.followUps, request("iron_ingot", 3), true);
        this.control.fire(Kind.DONE, "");
        this.followUps.drainNotes();
        this.control.start("torch", 4);
        this.control.fire(Kind.DONE, "");
        assertEquals(Action.IGNORE, lastAction());
    }

    @Test
    void rejectionsComeBackAsReadableText() {
        this.control.rejectWith = "unknown item \"iron pick axe\".";
        String result = AiTools.startAcquire(this.control, this.followUps, request("iron pick axe", 1), true);
        assertTrue(result.startsWith("Could not start: unknown item \"iron pick axe\""), result);
        assertFalse(this.followUps.ownsCurrent());
        assertTrue(AiTools.planAcquire(this.control, request("iron pick axe", 1)).startsWith("Can't plan that: unknown item"));
    }

    @Test
    void crashesInTheExecutorDoNotEscape() {
        AcquireControl broken = new FakeAcquireControl() {
            @Override
            public String start(String itemText, int count) {
                throw new IllegalStateException("boom");
            }

            @Override
            public String plan(String itemText, int count) {
                throw new IllegalStateException("boom");
            }
        };
        assertTrue(AiTools.startAcquire(broken, this.followUps, request("torch", 1), true).contains("boom"));
        assertTrue(AiTools.planAcquire(broken, request("torch", 1)).contains("boom"));
        assertFalse(this.followUps.ownsCurrent());
    }

    @Test
    void withoutFollowUpsTheModelIsToldToCheckBack() {
        String result = AiTools.startAcquire(this.control, this.followUps, request("torch", 1), false);
        assertFalse(result.contains("[event]"));
        assertTrue(result.contains("look_around"));
    }

    @Test
    void planReturnsTheStepsAndStartsNothing() {
        String result = AiTools.planAcquire(this.control, request("wooden_pickaxe", 1));
        assertEquals("Plan for 1 wooden_pickaxe (nothing was started):\n1. mine 3 oak_log\n2. craft 12 oak_planks", result);
        assertFalse(this.control.isActive());
        assertTrue(this.actions.isEmpty());
    }

    @Test
    void longPlansAreCutAtALine() {
        StringBuilder plan = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            plan.append(i).append(". craft something long enough to matter\n");
        }
        this.control.planText = plan.toString();
        String result = AiTools.planAcquire(this.control, request("beacon", 1));
        assertTrue(result.length() < AiTools.MAX_PLAN_CHARS + 120, "length " + result.length());
        assertTrue(result.endsWith("more lines."), result);
    }

    @Test
    void truncateLinesKeepsShortTextAndCutsLongLines() {
        assertEquals("a\nb", AiTools.truncateLines("a\nb", 10));
        assertEquals("a\n… and 1 more lines.", AiTools.truncateLines("a\nbbbbbbbbbbbb", 5));
        assertEquals("aaaaa…", AiTools.truncateLines("aaaaaaaaaa", 5));
    }

    @Test
    void theDenyListCoversBothTools() {
        AiConfig config = new AiConfig();
        assertNull(AiTools.acquireRefusal(config));
        config.deniedCommands.add("Acquire");
        assertTrue(AiTools.acquireRefusal(config).startsWith("Refused"));
    }

    @Test
    void snapshotShowsTheRunningAcquire() {
        AcquireControl.set(null);
        assertNull(WorldSnapshot.acquireStatus(), "feature not loaded");

        AcquireControl.set(this.control);
        assertNull(WorldSnapshot.acquireStatus(), "idle");

        this.control.active = true;
        this.control.status = "acquiring 3 iron_ingot: step 4/9, smelt 3 raw_iron (2/3)";
        assertEquals("acquiring 3 iron_ingot: step 4/9, smelt 3 raw_iron (2/3)", WorldSnapshot.acquireStatus());

        this.control.status = " ";
        assertEquals("acquire running", WorldSnapshot.acquireStatus());
    }
}
