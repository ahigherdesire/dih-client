package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.utils.Helper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Step.Smelt} split across furnaces side by side: the ones already in reach, and extra ones from the inventory
 * placed beside the player. Each is loaded with its share of the input and the fuel for that share, then emptied in
 * the order it was loaded (the first loaded is done first, and the rest cook meanwhile), and the ones placed for it
 * are broken and picked up again. A furnace that can't be placed or loaded only leaves more for the others, and what
 * is left uncooked the process re-plans.
 */
final class SplitSmeltRunner extends RunnerBase {

    private enum Phase { PLACE, LOAD, COLLECT, TAKE_BACK }

    /** How far from the player a furnace already there may be to take a share. */
    private static final int REACH = 5;
    private static final int PLACE_FAILS = 2;

    private final Step.Smelt step;
    private final String station;
    private final List<BlockPos> furnaces = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    private final List<BlockPos> loaded = new ArrayList<>();
    private final List<int[]> loads = new ArrayList<>();
    private Phase phase;
    private StepRunner sub;
    private int index;
    private int placeFails;
    private int loadedTimes;
    /** The share the furnace being loaded gets. */
    private int share;

    SplitSmeltRunner(ExecContext x, Step.Smelt step) {
        super(x);
        this.step = step;
        this.station = step.recipe().station();
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (phase == null) {
            for (BlockPos pos : x.stations.find(station, x.stationRadius())) {
                if (furnaces.size() < step.furnaces() && ctx.player().position().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH) furnaces.add(pos);
            }
            phase = Phase.PLACE;
        }
        if (sub != null) {
            Result result = sub.tick(calcFailed, safeToCancel);
            if (result.kind() == Result.Kind.RUNNING || result.kind() == Result.Kind.FATAL) return result;
            finished(result.kind() == Result.Kind.DONE, result.reason());
            sub = null;
        }
        return next();
    }

    /** What the last sub-runner leaves for the next. */
    private void finished(boolean done, String reason) {
        switch (phase) {
            case PLACE -> {
                BlockPos pos = done && sub instanceof StationRunner runner ? runner.placed() : null;
                if (pos != null) {
                    furnaces.add(pos);
                    placed.add(pos);
                } else {
                    placeFails++;
                    logDebug("split smelt: couldn't place a furnace (" + reason + ")");
                }
            }
            case LOAD -> {
                if (done) {
                    loaded.add(furnaces.get(index));
                    loads.add(new int[]{share, fuelFor(share)});
                    loadedTimes += share;
                } else {
                    logDebug("split smelt: couldn't load the furnace at " + furnaces.get(index).toShortString() + " (" + reason + ")");
                }
                index++;
            }
            case COLLECT -> {
                if (!done) logDebug("split smelt: couldn't empty the furnace at " + loaded.get(index).toShortString() + " (" + reason + ")");
                index++;
            }
            case TAKE_BACK -> {
                if (!done) logDebug("split smelt: left the furnace at " + placed.get(index).toShortString() + " (" + reason + ")");
                index++;
            }
        }
    }

    private Result next() {
        for (int guard = 0; guard < 8; guard++) {
            switch (phase) {
                case PLACE -> {
                    if (furnaces.size() < step.furnaces() && x.have(station) > 0 && placeFails < PLACE_FAILS) {
                        sub = new StationRunner(x, new Step.PlaceStation(station), true);
                        return Result.pause();
                    }
                    if (furnaces.isEmpty()) return Result.failed("no " + Step.shortId(station) + " to smelt in");
                    logDebug("split smelt: " + step.times() + " " + Step.shortId(step.input()) + " across " + furnaces.size() + " furnaces");
                    phase = Phase.LOAD;
                    index = 0;
                }
                case LOAD -> {
                    int left = toLoad();
                    if (index < furnaces.size() && left > 0) {
                        // An even share of what's left: the larger shares first, and more for the rest if one fails.
                        share = (left + furnaces.size() - index - 1) / (furnaces.size() - index);
                        sub = new SmeltRunner(x, step, SmeltRunner.Mode.LOAD, furnaces.get(index), share, fuelFor(share));
                        return Result.pause();
                    }
                    if (loaded.isEmpty()) return Result.failed("couldn't load any furnace with " + Step.shortId(step.input()));
                    phase = Phase.COLLECT;
                    index = 0;
                }
                case COLLECT -> {
                    if (index < loaded.size()) {
                        int[] load = loads.get(index);
                        sub = new SmeltRunner(x, step, SmeltRunner.Mode.COLLECT, loaded.get(index), load[0], load[1]);
                        return Result.pause();
                    }
                    phase = Phase.TAKE_BACK;
                    index = 0;
                }
                case TAKE_BACK -> {
                    if (index < placed.size()) {
                        sub = new RetrieveStationRunner(x, new Step.RetrieveStation(station), placed.get(index));
                        return Result.pause();
                    }
                    return Result.done(); // the process checks the count and re-plans if short
                }
            }
        }
        return Result.pause();
    }

    /** The input still to put in a furnace: what the count lacks, less what is loaded already, as far as it's held. */
    private int toLoad() {
        int outputs = Math.max(0, step.untilCount() - x.have(step.item()));
        int per = Math.max(1, step.recipe().outputCount());
        return Math.min(x.have(step.input()), (outputs + per - 1) / per - loadedTimes);
    }

    /** Fuel for {@code times} cooks in one furnace: what the plan would give it, or a fair part of the planned fuel. */
    private int fuelFor(int times) {
        Integer burn = x.knowledge.fuels() == null ? null : x.knowledge.fuels().get(step.fuel());
        if (burn != null && burn > 0) return Step.Smelt.fuelFor(times, Math.max(1, step.recipe().cookTicks()), burn);
        return (int) Math.ceil((double) step.fuelCount() * times / Math.max(1, step.times()));
    }

    private static void logDebug(String message) {
        Helper.HELPER.logDebug(message);
    }

    @Override
    public void cancel() {
        if (sub != null) sub.cancel();
    }
}
