package baritone.ai.tool;

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.AiConfig;
import baritone.ai.AiMemory;
import baritone.ai.WorldSnapshot;
import baritone.guardian.GuardianProcess;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a tool can reach: Baritone, the game thread, the world snapshot, the Guardian, and who is calling. One is
 * made per call; {@code with…} methods return a copy.
 */
public final class ToolContext {

    /** Who asked. Only the AI is limited to the tools it loaded; a player or a macro can call anything. */
    public enum Source { AI, CHAT, MACRO }

    private final AiBrain brain;
    private final Source source;
    private final ToolSession session;
    private final boolean confirmed;
    private final Consumer<String> events;

    private ToolContext(AiBrain brain, Source source, ToolSession session, boolean confirmed, Consumer<String> events) {
        this.brain = brain;
        this.source = source;
        this.session = session;
        this.confirmed = confirmed;
        this.events = events;
    }

    /** {@code brain} may be null in tests; game-thread work then runs inline. */
    public static ToolContext of(AiBrain brain, Source source) {
        return new ToolContext(brain, source, null, false, event -> { });
    }

    /** The run's visible tools, for an AI call. */
    public ToolContext withSession(ToolSession session) {
        return new ToolContext(this.brain, this.source, session, this.confirmed, this.events);
    }

    /** A dangerous tool may run: the player clicked [confirm], or a macro step allows it. */
    public ToolContext confirmed(boolean confirmed) {
        return new ToolContext(this.brain, this.source, this.session, confirmed, this.events);
    }

    /** Where tools report things that happen later, such as a job ending. */
    public ToolContext withEvents(Consumer<String> events) {
        return new ToolContext(this.brain, this.source, this.session, this.confirmed, events == null ? event -> { } : events);
    }

    public Source source() {
        return this.source;
    }

    /** Null outside an AI run. */
    public ToolSession session() {
        return this.session;
    }

    public boolean isConfirmed() {
        return this.confirmed;
    }

    public AiBrain brain() {
        return this.brain;
    }

    public Baritone baritone() {
        return this.brain == null ? null : this.brain.getBaritone();
    }

    public AiConfig config() {
        return this.brain == null ? null : this.brain.getConfig();
    }

    public AiMemory memory() {
        return this.brain == null ? null : this.brain.getMemory();
    }

    public GuardianProcess guardian() {
        Baritone baritone = baritone();
        return baritone == null ? null : baritone.getGuardianProcess();
    }

    /** Runs {@code work} on the game thread and waits (10 s), or returns {@code fallback}. Safe on the game thread. */
    public <T> T onGameThread(Supplier<T> work, T fallback) {
        return this.brain == null ? work.get() : this.brain.onGameThread(work, fallback);
    }

    /** The full situation report: position, health, inventory, mobs, the running job. */
    public String snapshot() {
        return onGameThread(() -> WorldSnapshot.describe(baritone()), "Not in a world.");
    }

    /** One line of where things stand, for the end of a result. */
    public String brief() {
        return onGameThread(() -> WorldSnapshot.brief(baritone()), "somewhere");
    }

    public void event(String text) {
        this.events.accept(text);
    }
}
