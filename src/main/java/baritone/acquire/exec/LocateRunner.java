package baritone.acquire.exec;

import baritone.acquire.model.Location;
import baritone.acquire.model.Step;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.BaritoneAPI;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.command.defaults.ClientStructureFinder;
import baritone.command.defaults.SeedStructureScanner;
import baritone.command.defaults.StructureCommand;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link Step.Locate} for a nether fortress: find where one is, walk there, and finish once nether bricks are all
 * around ({@link BaritoneWorldView#here}). Where it is comes from, in order: the integrated server in singleplayer
 * (the same search as {@code /locate}), the world seed entered with {@code #seedinput} on a server, or, with neither,
 * exploring straight along the axis the player faces until a fortress comes into view.
 */
final class LocateRunner extends RunnerBase {

    private enum State { FIND, GO, CLOSE_IN }

    /** How far to explore without a known position before giving up. */
    static final int EXPLORE_BLOCKS = 2000;
    /** Near the found position, head for the nearest nether brick in view. */
    private static final int CLOSE_BLOCKS = 48;
    private static final int CHECK_EVERY = 20;

    private final Step.Locate step;
    private State state = State.FIND;
    private CompletableFuture<BlockPos> search;
    private BlockPos target;
    private boolean exploring;
    private BlockPos brick;
    private int ticks;

    LocateRunner(ExecContext x, Step.Locate step) {
        super(x);
        this.step = step;
    }

    /** Whether this runner can find {@code site} (the stronghold comes with the End update). */
    static boolean supports(Location site) {
        return site == Location.FORTRESS;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (!supports(step.site())) return Result.failed("finding " + step.site().label() + " isn't built yet");
        if (++ticks % CHECK_EVERY == 1 && BaritoneWorldView.here(ctx) == step.site()) return Result.done();
        switch (state) {
            case FIND -> {
                if (search == null) search = find();
                if (!search.isDone()) return Result.pause();
                target = search.getNow(null);
                if (target == null) {
                    // Nothing known: explore straight ahead along the nearer axis.
                    exploring = true;
                    BlockPos feet = ctx.playerFeet();
                    float yaw = ctx.player().getYRot();
                    int dx = (int) Math.round(-Math.sin(Math.toRadians(yaw)));
                    int dz = (int) Math.round(Math.cos(Math.toRadians(yaw)));
                    if (Math.abs(dx) + Math.abs(dz) != 1) dx = 1;
                    if (dx != 0) dz = 0;
                    target = feet.offset(dx * EXPLORE_BLOCKS, 0, dz * EXPLORE_BLOCKS);
                    x.logDirect("No fortress position known: exploring toward " + target.getX() + " " + target.getZ() + ".");
                } else {
                    x.logDirect("Nether fortress at " + target.getX() + " " + target.getZ() + ".");
                }
                state = State.GO;
                return Result.pause();
            }
            case GO -> {
                if (calcFailed) return Result.failed("no path toward the fortress at " + target.getX() + " " + target.getZ());
                BlockPos feet = ctx.playerFeet();
                double dx = feet.getX() - target.getX();
                double dz = feet.getZ() - target.getZ();
                boolean near = dx * dx + dz * dz <= CLOSE_BLOCKS * CLOSE_BLOCKS;
                if (near || ticks % CHECK_EVERY == 1) {
                    // Nether bricks in view: the fortress is found, head for it.
                    brick = nearestBrick();
                    if (brick != null) {
                        state = State.CLOSE_IN;
                        return Result.pause();
                    }
                    if (near) {
                        if (exploring) return Result.failed("explored " + EXPLORE_BLOCKS + " blocks and found no fortress");
                        return Result.failed("no nether bricks in sight near " + target.getX() + " " + target.getZ());
                    }
                }
                return walk(new GoalXZ(target.getX(), target.getZ()));
            }
            case CLOSE_IN -> {
                if (calcFailed) return Result.failed("no path to the fortress's nether bricks at " + brick.toShortString());
                return walk(new GoalGetToBlock(brick));
            }
        }
        return Result.pause();
    }

    @Override
    public void cancel() {
        if (search != null) search.cancel(false);
    }

    /** The nearest nether brick block in the loaded chunks, or null. */
    private BlockPos nearestBrick() {
        List<BlockPos> found = BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(Blocks.NETHER_BRICKS), 64, -1, 6);
        BlockPos feet = ctx.playerFeet();
        return found.stream().min(Comparator.comparingDouble(p -> p.distSqr(feet))).orElse(null);
    }

    /** The fortress position from the integrated server or the seed, off the game thread; null when neither knows. */
    private CompletableFuture<BlockPos> find() {
        BlockPos origin = ctx.playerFeet();
        MinecraftServer server = ctx.minecraft().getSingleplayerServer();
        if (server != null) {
            ServerLevel level = server.getLevel(ctx.world().dimension());
            if (level != null) {
                return CompletableFuture.supplyAsync(() -> {
                    HolderSet<Structure> fortress = StructureCommand.resolveStructures("id:fortress", level);
                    if (fortress == null) return null;
                    Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                            .findNearestMapStructure(level, fortress, origin, 100, false);
                    return found == null ? null : found.getFirst();
                }, server).exceptionally(e -> null);
            }
        }
        if (ClientStructureFinder.hasSeed()) {
            long seed = ClientStructureFinder.getSeed();
            var dimension = ctx.world().dimension();
            return CompletableFuture.supplyAsync(() -> {
                SeedStructureScanner.Found found = StructureCommand.findNearestSeeded("id:fortress", seed, dimension,
                        origin.getX(), origin.getZ());
                return found == null ? null : new BlockPos(found.x(), found.y(), found.z());
            }).exceptionally(e -> null);
        }
        return CompletableFuture.completedFuture(null);
    }
}
