package baritone.ai;

import baritone.acquire.AcquireControl.AcquireEvent;
import baritone.acquire.AcquireControl.AcquireEvent.Kind;
import baritone.ai.AcquireFollowUps.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which acquire events earn the AI another turn: only its own, only a bounded number in a row. */
final class AcquireFollowUpsTest {

    private static final long T0 = 1_000_000L;
    private static final int CAP = 3;

    private AcquireFollowUps followUps;
    private long now;

    @BeforeEach
    void setUp() {
        this.followUps = new AcquireFollowUps();
        this.now = T0;
    }

    private static AcquireEvent event(Kind kind) {
        return new AcquireEvent(kind, "iron_ingot", 3, kind == Kind.FAILED ? "no iron ore nearby" : "");
    }

    private Action fire(Kind kind) {
        return this.followUps.onEvent(event(kind), this.now, true, CAP);
    }

    /** The AI starts an acquire and the executor reports STARTED from inside start(). */
    private void aiStartsSynchronously() {
        this.followUps.aiStarting(this.now);
        fire(Kind.STARTED);
        this.followUps.aiStartFinished(true);
    }

    @Test
    void anAcquireTheAiStartedEarnsAFollowUp() {
        aiStartsSynchronously();
        assertTrue(this.followUps.ownsCurrent());
        assertEquals(Action.IGNORE, fire(Kind.STEP));

        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
        assertTrue(this.followUps.isFollowUpOwed());
        assertEquals(1, this.followUps.chainLength());
        assertEquals("[event] acquire of 3 iron_ingot is DONE", this.followUps.drainNotes());
        assertFalse(this.followUps.isFollowUpOwed(), "reading the note settles the follow-up");
    }

    @Test
    void failuresFollowUpWithTheReason() {
        aiStartsSynchronously();
        assertEquals(Action.FOLLOW_UP, fire(Kind.FAILED));
        assertEquals("[event] acquire of 3 iron_ingot FAILED: no iron ore nearby", this.followUps.drainNotes());
    }

    @Test
    void anAcquireTheUserStartedIsIgnored() {
        this.now += 60_000L;
        assertEquals(Action.IGNORE, fire(Kind.STARTED));
        assertEquals(Action.IGNORE, fire(Kind.STEP));
        assertEquals(Action.IGNORE, fire(Kind.DONE));
        assertFalse(this.followUps.isFollowUpOwed());
        assertFalse(this.followUps.hasNotes());
    }

    @Test
    void aUserAcquireThatReplacesTheAisOneIsIgnored() {
        aiStartsSynchronously();
        this.now += 30_000L;
        fire(Kind.STOPPED);          // the executor drops the AI's acquire...
        this.followUps.drainNotes();
        fire(Kind.STARTED);          // ...for the one the user typed
        assertFalse(this.followUps.ownsCurrent());
        assertEquals(Action.IGNORE, fire(Kind.DONE));
    }

    @Test
    void aUserAcquireStartedWithoutAStopStillTakesOwnershipAway() {
        aiStartsSynchronously();
        this.now += 30_000L;
        fire(Kind.STARTED);
        assertEquals(Action.IGNORE, fire(Kind.DONE));
    }

    @Test
    void aStartedEventThatArrivesOnALaterTickIsStillTheAis() {
        this.followUps.aiStarting(this.now);
        this.followUps.aiStartFinished(true);
        this.now += 50L;
        fire(Kind.STARTED);
        assertTrue(this.followUps.ownsCurrent());
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
    }

    @Test
    void anExecutorThatNeverSendsStartedStillWorks() {
        this.followUps.aiStarting(this.now);
        this.followUps.aiStartFinished(true);
        this.now += 120_000L;
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
    }

    @Test
    void aStartedEventAfterTheGraceWindowIsSomebodyElses() {
        this.followUps.aiStarting(this.now);
        this.followUps.aiStartFinished(true);
        this.now += AcquireFollowUps.START_GRACE_MILLIS + 1;
        fire(Kind.STARTED);
        assertFalse(this.followUps.ownsCurrent());
        assertEquals(Action.IGNORE, fire(Kind.DONE));
    }

    @Test
    void aFailedStartClosesTheWindow() {
        this.followUps.aiStarting(this.now);
        this.followUps.aiStartFinished(false); // start() threw
        this.now += 10L;
        fire(Kind.STARTED);                   // so this one is somebody else's
        assertEquals(Action.IGNORE, fire(Kind.DONE));
    }

    @Test
    void aFailedStartThatStoppedTheOldAcquireOwnsNothing() {
        aiStartsSynchronously();
        this.now += 20_000L;
        this.followUps.aiStarting(this.now);
        fire(Kind.STOPPED);
        this.followUps.aiStartFinished(false);
        assertFalse(this.followUps.ownsCurrent());
    }

    @Test
    void aFailedStartKeepsTheAcquireThatWasAlreadyRunning() {
        aiStartsSynchronously();
        this.now += 5_000L;
        this.followUps.aiStarting(this.now);
        this.followUps.aiStartFinished(false);
        assertTrue(this.followUps.ownsCurrent());
        this.now += 60_000L;
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
    }

    @Test
    void theAiReplacingItsOwnAcquireDoesNotReportAStop() {
        aiStartsSynchronously();
        this.now += 20_000L;
        this.followUps.aiStarting(this.now);
        assertEquals(Action.IGNORE, fire(Kind.STOPPED));
        fire(Kind.STARTED);
        this.followUps.aiStartFinished(true);
        assertFalse(this.followUps.hasNotes());
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
    }

    @Test
    void aStopIsANoteButNeverATurn() {
        aiStartsSynchronously();
        this.now += 20_000L;
        assertEquals(Action.NOTE, fire(Kind.STOPPED));
        assertFalse(this.followUps.isFollowUpOwed());
        assertTrue(this.followUps.drainNotes().contains("STOPPED"));
        assertEquals(0, this.followUps.chainLength());
    }

    @Test
    void aStopCancelsAFollowUpThatWasStillOwed() {
        aiStartsSynchronously();
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE)); // owed while the brain is busy
        aiStartsSynchronously();                          // the brain starts the next piece
        this.now += 20_000L;
        fire(Kind.STOPPED);                               // and the user hits #stop
        assertFalse(this.followUps.isFollowUpOwed());
    }

    @Test
    void theChainIsCapped() {
        for (int i = 1; i <= CAP; i++) {
            aiStartsSynchronously();
            assertEquals(Action.FOLLOW_UP, fire(Kind.DONE), "follow-up " + i);
            this.followUps.drainNotes();
            this.now += 60_000L;
        }
        aiStartsSynchronously();
        assertEquals(Action.CAPPED, fire(Kind.DONE));
        assertFalse(this.followUps.isFollowUpOwed());
        assertTrue(this.followUps.hasNotes(), "the model still reads what happened next time");
    }

    @Test
    void theUserSpeakingResetsTheChain() {
        for (int i = 0; i < CAP; i++) {
            aiStartsSynchronously();
            fire(Kind.DONE);
            this.now += 60_000L;
        }
        this.followUps.userSpoke();
        assertEquals(0, this.followUps.chainLength());
        aiStartsSynchronously();
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
    }

    @Test
    void offMeansANoteOnly() {
        aiStartsSynchronously();
        assertEquals(Action.NOTE, this.followUps.onEvent(event(Kind.DONE), this.now, false, CAP));
        assertFalse(this.followUps.isFollowUpOwed());
        assertEquals(0, this.followUps.chainLength());
        assertTrue(this.followUps.hasNotes());
    }

    @Test
    void aCapOfZeroMeansOff() {
        aiStartsSynchronously();
        assertEquals(Action.NOTE, this.followUps.onEvent(event(Kind.DONE), this.now, true, 0));
        assertFalse(this.followUps.isFollowUpOwed());
    }

    @Test
    void eachAcquireEndsOnlyOnce() {
        aiStartsSynchronously();
        assertEquals(Action.FOLLOW_UP, fire(Kind.DONE));
        assertEquals(Action.IGNORE, fire(Kind.DONE));
        assertEquals(Action.IGNORE, fire(Kind.FAILED));
        assertEquals(1, this.followUps.chainLength());
    }

    @Test
    void resetForgetsEverything() {
        aiStartsSynchronously();
        fire(Kind.DONE);
        aiStartsSynchronously();
        this.followUps.reset();
        assertFalse(this.followUps.ownsCurrent());
        assertFalse(this.followUps.isFollowUpOwed());
        assertFalse(this.followUps.hasNotes());
        assertEquals(0, this.followUps.chainLength());
        assertEquals(Action.IGNORE, fire(Kind.DONE));
    }

    @Test
    void notesAreBounded() {
        for (int i = 0; i < AcquireFollowUps.MAX_NOTES + 4; i++) {
            aiStartsSynchronously();
            this.followUps.onEvent(event(Kind.DONE), this.now, false, CAP);
            this.now += 60_000L;
        }
        assertEquals(AcquireFollowUps.MAX_NOTES, this.followUps.drainNotes().split("\n").length);
    }

    @Test
    void nullAndOddEventsAreIgnored() {
        assertEquals(Action.IGNORE, this.followUps.onEvent(null, this.now, true, CAP));
        assertEquals(Action.IGNORE, this.followUps.onEvent(new AcquireEvent(null, "x", 1, ""), this.now, true, CAP));
    }

    @Test
    void attachesToEachControlOnce() {
        List<Consumer<AcquireEvent>> registered = new ArrayList<>();
        FakeAcquireControl first = new FakeAcquireControl(registered);
        Consumer<AcquireEvent> listener = e -> { };

        assertFalse(this.followUps.attach(null, listener));
        assertTrue(this.followUps.attach(first, listener));
        assertFalse(this.followUps.attach(first, listener));
        assertEquals(1, registered.size());

        // A new implementation registered later gets the listener too.
        assertTrue(this.followUps.attach(new FakeAcquireControl(registered), listener));
        assertEquals(2, registered.size());
    }

    @Test
    void summariesAreShortAndReadable() {
        assertEquals("acquire of 3 iron_ingot is DONE (got 3)",
                AcquireFollowUps.summary(new AcquireEvent(Kind.DONE, "iron_ingot", 3, "  got\n3 ")));
        assertEquals("acquire of item FAILED: no reason given",
                AcquireFollowUps.summary(new AcquireEvent(Kind.FAILED, null, 0, null)));
        String longReason = AcquireFollowUps.summary(new AcquireEvent(Kind.FAILED, "torch", 1, "x".repeat(500)));
        assertTrue(longReason.length() < 220, longReason);
        assertTrue(AcquireFollowUps.followUpPrompt(2, 6).contains("2/6"));
    }
}
