package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;
import baritone.parkour.ParkourProcess;
import baritone.parkour.exec.ParkourExecutor;
import baritone.parkour.record.CourseRecorder;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.solve.JumpSolver;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** {@code #parkour}: run a parkour course, or jump to a block, with exactly solved jumps. */
public class ParkourCommand extends Command {

    public ParkourCommand(IBaritone baritone) {
        super(baritone, "parkour");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ParkourProcess parkour = ((Baritone) baritone).getParkourProcess();
        String sub = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "";
        try {
            switch (sub) {
                case "" -> parkour.follow();
                case "goto" -> {
                    args.requireMin(3);
                    BetterBlockPos target = args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet());
                    parkour.goTo(target);
                }
                case "stop" -> logDirect(parkour.stop());
                case "status" -> logDirect(parkour.status());
                case "solve" -> {
                    args.requireMin(3);
                    BlockPos target = args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet());
                    solve(target);
                }
                case "record" -> record(args);
                default -> throw new CommandInvalidStateException("Unknown: #parkour " + sub + ". Try #parkour, #parkour goto x y z, #parkour stop, #parkour record.");
            }
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
    }

    /** {@code #parkour record start|save <name>|status|stop}: copy a course out of this world to a file. */
    private void record(IArgConsumer args) throws CommandException {
        String what = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "status";
        switch (what) {
            case "start" -> {
                if (ctx.player() == null) throw new IllegalArgumentException("Join a world first.");
                logDirect(CourseRecorder.start(ctx.player(), ctx.world()));
            }
            case "save" -> {
                args.requireMin(1);
                String name = args.getString();
                try {
                    logDirect(CourseRecorder.save(name));
                } catch (java.io.IOException e) {
                    throw new CommandInvalidStateException("Couldn't save the course: " + e.getMessage());
                } catch (IllegalStateException e) {
                    throw new CommandInvalidStateException(e.getMessage());
                }
            }
            case "stop" -> logDirect(CourseRecorder.stop());
            case "status" -> logDirect(CourseRecorder.status());
            default -> throw new CommandInvalidStateException("Try #parkour record start, #parkour record save <name>, #parkour record stop.");
        }
    }

    /** Solves one jump from here onto {@code target} and prints the plan, without moving. */
    private void solve(BlockPos target) {
        if (ctx.player() == null) throw new IllegalArgumentException("Join a world first.");
        PlayerState from = ParkourExecutor.stateOf(ctx.player());
        var world = LevelSnapshot.around(ctx.world(), target, 12, 8, 8);
        JumpSolver.Result result = JumpSolver.solve(world, from, target, JumpSolver.Limits.THOROUGH);
        if (result instanceof JumpSolver.Found found) {
            logDirect(String.format(Locale.ROOT, "Jump to %s: %d ticks, margin %.0f%%, %d nodes, %.0f ms.", target.toShortString(),
                found.ticks(), found.margin() * 100, found.nodes(), found.nanos() / 1e6));
        } else if (result instanceof JumpSolver.Impossible impossible) {
            logDirect(String.format(Locale.ROOT, "No way onto %s: %s (%d nodes, %.0f ms).", target.toShortString(), impossible.reason(),
                impossible.nodes(), impossible.nanos() / 1e6));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString();
            return Stream.of("goto", "solve", "stop", "status", "record").filter(s -> s.startsWith(prefix));
        }
        if (args.has(2) && args.peekString().equalsIgnoreCase("record")) {
            args.get();
            String prefix = args.peekString();
            return Stream.of("start", "save", "status", "stop").filter(s -> s.startsWith(prefix));
        }
        if (args.hasAny() && (args.peekString().equalsIgnoreCase("goto") || args.peekString().equalsIgnoreCase("solve"))) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run a parkour course with exactly solved jumps";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Runs parkour by solving each jump exactly: the client's own movement rules are simulated tick by tick,",
            "and a search finds the keys and turns that land, including neos, strafes, head-hitters, ladder and",
            "momentum jumps. Only normal key presses and turning; nothing a player couldn't do.",
            "",
            "Usage:",
            "> parkour - Follow the course you are standing on until there are no more jumps ahead.",
            "> parkour goto <x> <y> <z> - Jump your way to that block.",
            "> parkour solve <x> <y> <z> - Solve one jump onto that block and say how, without moving.",
            "> parkour status - What it is doing.",
            "> parkour record start - Start copying the course you're on to a file; run it to the end.",
            "> parkour record save <name> - Save it to dih/parkour-courses/<name>.nbt (a structure file).",
            "> parkour stop - Stop (so do #stop and #cancel)."
        );
    }
}
