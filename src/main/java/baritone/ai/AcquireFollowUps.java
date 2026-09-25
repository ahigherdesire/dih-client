/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.ai;

import baritone.acquire.AcquireControl;
import baritone.acquire.AcquireControl.AcquireEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Decides which {@code #acquire} events the AI hears about, and which of them earn it another
 * turn without anyone typing. Pure bookkeeping with no game access, so it is unit-testable.
 *
 * <ul>
 *   <li>Only acquires the AI started count. The first STARTED event within a short grace window
 *       of the AI's own {@code start()} belongs to it; any other STARTED means somebody else's
 *       acquire replaced it.</li>
 *   <li>DONE and FAILED of an AI-owned acquire become a note for the model, plus a request for a
 *       follow-up turn when follow-ups are on and the chain cap is not reached.</li>
 *   <li>STOPPED becomes a note only. A stop never causes a turn.</li>
 *   <li>The chain counter resets only when the user gives a new instruction.</li>
 * </ul>
 */
public final class AcquireFollowUps {

    public enum Action {
        /** Not ours, or not interesting. */
        IGNORE,
        /** Queued for the model to read at its next turn; no turn is requested. */
        NOTE,
        /** Queued, and the AI should get a turn now. */
        FOLLOW_UP,
        /** Queued, but the chain cap is reached, so the AI waits for the user. */
        CAPPED
    }

    /** How long after the AI's start() a STARTED event is still taken to be the AI's own. */
    static final long START_GRACE_MILLIS = 5_000L;
    static final int MAX_NOTES = 5;
    private static final int MAX_MESSAGE_CHARS = 160;

    private final Deque<String> notes = new ArrayDeque<>();
    private AcquireControl attachedTo;

    private boolean aiOwned;
    private long awaitStartedUntil;
    private boolean startedSeen;
    private boolean stoppedDuringStart;
    private int chain;
    private boolean owed;

    /** Registers {@code listener} with {@code control} unless it already is. Returns true if it registered. */
    public synchronized boolean attach(AcquireControl control, Consumer<AcquireEvent> listener) {
        if (control == null || control == this.attachedTo) {
            return false;
        }
        control.addListener(listener);
        this.attachedTo = control;
        return true;
    }

    /** Call right before the AI calls {@code AcquireControl.start}. */
    public synchronized void aiStarting(long now) {
        this.awaitStartedUntil = now + START_GRACE_MILLIS;
        this.startedSeen = false;
        this.stoppedDuringStart = false;
    }

    /** Call right after the AI's {@code start} returned ({@code true}) or threw ({@code false}). */
    public synchronized void aiStartFinished(boolean started) {
        if (!started) {
            this.awaitStartedUntil = 0L;
            if (this.stoppedDuringStart) {
                this.aiOwned = false;
            }
            return;
        }
        // If STARTED already arrived (synchronously), it set ownership and maybe even saw the end.
        // Otherwise it is still to come, or the executor does not send one; either way it's ours.
        if (!this.startedSeen) {
            this.aiOwned = true;
        }
    }

    /** The user gave a new instruction: the automatic chain starts over. */
    public synchronized void userSpoke() {
        this.chain = 0;
    }

    /** Forget everything about the current acquire (history cleared, world left). */
    public synchronized void reset() {
        this.aiOwned = false;
        this.awaitStartedUntil = 0L;
        this.startedSeen = false;
        this.stoppedDuringStart = false;
        this.chain = 0;
        this.owed = false;
        this.notes.clear();
    }

    public synchronized Action onEvent(AcquireEvent event, long now, boolean followUpsOn, int maxChain) {
        if (event == null || event.kind() == null) {
            return Action.IGNORE;
        }
        boolean inStartWindow = now <= this.awaitStartedUntil;
        switch (event.kind()) {
            case STARTED:
                if (inStartWindow) {
                    this.aiOwned = true;
                    this.startedSeen = true;
                    this.awaitStartedUntil = 0L;
                } else {
                    this.aiOwned = false;
                }
                return Action.IGNORE;
            case STOPPED:
                if (inStartWindow && !this.startedSeen) {
                    // The AI's own start() is replacing the previous acquire.
                    this.stoppedDuringStart = true;
                    return Action.IGNORE;
                }
                if (!this.aiOwned) {
                    return Action.IGNORE;
                }
                this.aiOwned = false;
                this.owed = false;
                addNote(event);
                return Action.NOTE;
            case DONE:
            case FAILED:
                if (!this.aiOwned) {
                    return Action.IGNORE;
                }
                this.aiOwned = false;
                addNote(event);
                if (!followUpsOn || maxChain <= 0) {
                    return Action.NOTE;
                }
                if (this.chain >= maxChain) {
                    return Action.CAPPED;
                }
                this.chain++;
                this.owed = true;
                return Action.FOLLOW_UP;
            default:
                return Action.IGNORE;
        }
    }

    /** Takes every queued note, one per line, or "" if none. Also settles any owed follow-up. */
    public synchronized String drainNotes() {
        this.owed = false;
        if (this.notes.isEmpty()) {
            return "";
        }
        String joined = String.join("\n", this.notes);
        this.notes.clear();
        return joined;
    }

    public synchronized boolean isFollowUpOwed() {
        return this.owed;
    }

    /** Give up on an owed follow-up (rate limit, AI switched off). Its notes stay queued. */
    public synchronized void dropOwed() {
        this.owed = false;
    }

    public synchronized int chainLength() {
        return this.chain;
    }

    public synchronized boolean ownsCurrent() {
        return this.aiOwned;
    }

    public synchronized boolean hasNotes() {
        return !this.notes.isEmpty();
    }

    private void addNote(AcquireEvent event) {
        this.notes.addLast("[event] " + summary(event));
        while (this.notes.size() > MAX_NOTES) {
            this.notes.removeFirst();
        }
    }

    /** "acquire of 3 iron_ingot is DONE", "acquire of 3 iron_ingot FAILED: no iron nearby". */
    static String summary(AcquireEvent event) {
        String item = event.item() == null || event.item().isBlank() ? "item" : event.item().trim();
        String what = "acquire of " + (event.count() > 0 ? event.count() + " " : "") + item;
        String message = clean(event.message());
        switch (event.kind()) {
            case DONE:
                return what + " is DONE" + (message.isEmpty() ? "" : " (" + message + ")");
            case FAILED:
                return what + " FAILED: " + (message.isEmpty() ? "no reason given" : message);
            case STOPPED:
                return what + " was STOPPED. Don't restart it unless asked.";
            default:
                return what + ": " + event.kind() + (message.isEmpty() ? "" : " (" + message + ")");
        }
    }

    /** The prompt for an automatic turn; the event notes are put in front of it. */
    static String followUpPrompt(int number, int max) {
        return "(Automatic follow-up " + number + "/" + max + ": nobody typed anything. If the request "
                + "you were working on needs more steps, start the next one. If it is finished or cannot "
                + "be done, say so in one short line and stop. Do not start anything nobody asked for.)";
    }

    private static String clean(String message) {
        if (message == null) {
            return "";
        }
        String text = message.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_MESSAGE_CHARS ? text.substring(0, MAX_MESSAGE_CHARS) + "…" : text;
    }
}
