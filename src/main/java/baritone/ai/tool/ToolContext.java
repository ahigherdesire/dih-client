package baritone.ai.tool;

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.AiConfig;
import baritone.ai.AiMemory;
import baritone.ai.LiveCommands;
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
    private final CommandRunner commands;
    private final AiConfig config;

    /** Outside a game every command is unknown. */
    private static final CommandRunner NO_GAME = new CommandRunner() {
        @Override
        public Outcome baritone(String command) {
            return Outcome.unknown();
        }

        @Override
        public Outcome client(String command) {
            return Outcome.unknown();
        }
    };

    private ToolContext(AiBrain brain, Source source, ToolSession session, boolean confirmed, Consumer<String> events,
                        CommandRunner commands, AiConfig config) {
        this.brain = brain;
        this.source = source;
        this.session = session;
        this.confirmed = confirmed;
        this.events = events;
        this.commands = commands;
        this.config = config;
    }

    /** {@code brain} may be null in tests; game-thread work then runs inline. */
    public static ToolContext of(AiBrain brain, Source source) {
        return new ToolContext(brain, source, null, false, event -> { }, null, null);
    }

    /** The run's visible tools, for an AI call. */
    public ToolContext withSession(ToolSession session) {
        return new ToolContext(this.brain, this.source, session, this.confirmed, this.events, this.commands, this.config);
    }

    /** A dangerous tool may run: the player clicked [confirm], or a macro step allows it. */
    public ToolContext confirmed(boolean confirmed) {
        return new ToolContext(this.brain, this.source, this.session, confirmed, this.events, this.commands, this.config);
    }

    /** Where tools report things that happen later, such as a job ending. */
    public ToolContext withEvents(Consumer<String> events) {
        return new ToolContext(this.brain, this.source, this.session, this.confirmed, events == null ? event -> { } : events,
                this.commands, this.config);
    }

    /** Runs commands through {@code commands} instead of the game: for tests. */
    public ToolContext withCommands(CommandRunner commands) {
        return new ToolContext(this.brain, this.source, this.session, this.confirmed, this.events, commands, this.config);
    }

    /** Uses {@code config} instead of the brain's: for tests. */
    public ToolContext withConfig(AiConfig config) {
        return new ToolContext(this.brain, this.source, this.session, this.confirmed, this.events, this.commands, config);
    }

    /** Where tools run {@code #} and {@code .} commands. */
    public CommandRunner commands() {
        if (this.commands != null) return this.commands;
        return this.brain == null ? NO_GAME : new LiveCommands(this.brain);
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
        if (this.config != null) return this.config;
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
        return baritone() == null ? "Not in a world." : onGameThread(() -> WorldSnapshot.describe(baritone()), "Not in a world.");
    }

    /** One line of where things stand, for the end of a result. */
    public String brief() {
        return baritone() == null ? "not in a world" : onGameThread(() -> WorldSnapshot.brief(baritone()), "somewhere");
    }

    public void event(String text) {
        this.events.accept(text);
    }
}
