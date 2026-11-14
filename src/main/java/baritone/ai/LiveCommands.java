package baritone.ai;

import baritone.ai.tool.CommandRunner;
import baritone.api.command.ICommand;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Function;

/** Runs commands for tools in the running game, capturing what they print. */
public final class LiveCommands implements CommandRunner {

    /** How long to let an async command settle before reporting back, so its first message is captured. */
    static final long SETTLE_MILLIS = 1500L;

    /** Runs DIH {@code .} commands; the client installs it (Baritone code doesn't know DIH's dispatcher). */
    private static volatile Function<String, Outcome> clientRunner = command -> Outcome.unknown();

    private final AiBrain brain;

    public LiveCommands(AiBrain brain) {
        this.brain = brain;
    }

    public static void setClientRunner(Function<String, Outcome> runner) {
        clientRunner = runner == null ? command -> Outcome.unknown() : runner;
    }

    @Override
    public Outcome baritone(String command) {
        CommandOutputCapture capture = new CommandOutputCapture();
        Boolean handled = this.brain.onGameThread(() -> {
            capture.install();
            return this.brain.getBaritone().getCommandManager().execute(command);
        }, Boolean.FALSE);
        if (Boolean.TRUE.equals(handled) && !onGameThread()) {
            AiBrain.sleepQuietly(SETTLE_MILLIS);
        }
        this.brain.onGameThread(() -> {
            capture.uninstall();
            return Boolean.TRUE;
        }, Boolean.FALSE);
        if (!Boolean.TRUE.equals(handled)) {
            return Outcome.unknown();
        }
        return new Outcome(true, capture.errored(), capture.summary());
    }

    @Override
    public Outcome client(String command) {
        return clientRunner.apply(command);
    }

    @Override
    public List<String> baritoneNames(String name) {
        return this.brain.onGameThread(() -> {
            ICommand known = this.brain.getBaritone().getCommandManager().getCommand(name);
            return known == null ? List.of(name) : known.getNames();
        }, List.of(name));
    }

    private static boolean onGameThread() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.isSameThread();
    }
}
