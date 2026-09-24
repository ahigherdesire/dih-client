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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the nearest cached bed, navigates to it, and automatically right-clicks
 * to sleep once night falls (or during a thunderstorm).
 *
 * <p>All 16 bed colours are tracked in Baritone's block cache, so any bed the player
 * has previously loaded is found instantly. If no bed is cached, the player should
 * walk near their bed first (or run {@code #repack}).
 */
public class SleepCommand extends Command {

    /** In-game time tick when night begins (out of 24000). */
    private static final long NIGHT_START = 12541L;
    /** In-game time tick when night ends. */
    private static final long NIGHT_END   = 23459L;

    /** All 16 bed block variants — all are tracked in {@link baritone.cache.CachedChunk#BLOCKS_TO_KEEP_TRACK_OF}. */
    // MC 26.2: the 16 per-colour bed blocks are consolidated into the Blocks.BED ColorCollection.
    private static final List<Block> ALL_BEDS = Blocks.BED.asList();

    /** The currently active sleep thread, if any. Interrupted when #sleep is re-run or when cancelled. */
    private Thread sleepThread;

    public SleepCommand(IBaritone baritone) {
        super(baritone, "sleep");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);

        if (ctx.world() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }
        if (ctx.player().isSleeping()) {
            logDirect("You are already sleeping.");
            return;
        }

        // Cancel any previous sleep thread before starting a new one
        if (sleepThread != null && sleepThread.isAlive()) {
            sleepThread.interrupt();
            logDirect("Cancelled previous #sleep. Starting fresh.");
        }

        // ── Find nearest bed across all 16 colours ──────────────────────────
        final BetterBlockPos origin = ctx.playerFeet();
        double nearestDistSq = Double.MAX_VALUE;
        BlockPos nearestBed  = null;

        for (Block bed : ALL_BEDS) {
            final String blockName = BuiltInRegistries.BLOCK.getKey(bed).getPath();
            for (BetterBlockPos pos : ctx.worldData().getCachedWorld()
                    .getLocationsOf(blockName, Integer.MAX_VALUE, origin.x, origin.z, 4)
                    .stream()
                    .map(BetterBlockPos::new)
                    .toArray(BetterBlockPos[]::new)) {
                double d = origin.distSqr(pos);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearestBed = pos;
                }
            }
        }

        if (nearestBed == null) {
            throw new CommandInvalidStateException(
                    "No bed found in the block cache. Walk near your bed first so Baritone learns "
                    + "its location, then try again. Or run #repack to re-cache loaded chunks.");
        }

        final BlockPos bedPos = nearestBed;
        final int dist = (int) Math.sqrt(nearestDistSq);

        logDirect("Found bed " + dist + " blocks away at  X=" + bedPos.getX()
                + "  Y=" + bedPos.getY() + "  Z=" + bedPos.getZ() + ".  Navigating...");

        if (!isNight()) {
            logDirect("It is daytime. The bot will wait at the bed until night ("
                    + formatTicks(ticksUntilNight()) + " away).");
        }

        logDirect("Use  #cancel  at any time to abort.");

        // Navigate to within 1 block of the bed
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(bedPos, 1));

        // ── Background thread: wait for arrival → wait for night → sleep ────
        sleepThread = new Thread(() -> {
            try {
                // Step 1: wait until near the bed (10-minute timeout)
                waitUntil(() -> isNearBed(bedPos), 1200, 500);
                if (!isNearBed(bedPos)) {
                    Minecraft.getInstance().execute(
                            () -> logDirect("Sleep: timed out trying to reach the bed."));
                    return;
                }
                Minecraft.getInstance().execute(
                        () -> logDirect("Arrived at bed. Waiting for nighttime..."));

                // Step 2: wait for night or thunderstorm (20-minute timeout)
                waitUntil(this::isNight, 2400, 500);
                if (!isNight()) {
                    Minecraft.getInstance().execute(
                            () -> logDirect("Sleep: timed out waiting for night."));
                    return;
                }

                // Step 3: look at the bed
                Minecraft.getInstance().execute(() -> {
                    Optional<Rotation> reach = RotationUtils.reachable(
                            ctx, bedPos, ctx.playerController().getBlockReachDistance());
                    if (reach.isPresent()) {
                        baritone.getLookBehavior().updateTarget(reach.get(), true);
                    }
                });

                // Wait one tick for the look to be applied
                Thread.sleep(100);

                // Step 4: right-click
                Minecraft.getInstance().execute(() -> {
                    Optional<Rotation> reach = RotationUtils.reachable(
                            ctx, bedPos, ctx.playerController().getBlockReachDistance());
                    if (reach.isPresent()) {
                        ctx.playerController().processRightClick(
                                ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
                        logDirect("Goodnight! 🛏");  // 🛏
                    } else {
                        logDirect("Bed is out of reach. Move closer and right-click manually.");
                    }
                });

            } catch (InterruptedException ignored) {}
        }, "BaritoneSleep");
        sleepThread.setDaemon(true);
        sleepThread.start();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns true if player is within 2 blocks of the bed. */
    private boolean isNearBed(BlockPos bed) {
        return ctx.player() != null
                && ctx.player().blockPosition().distSqr(bed) <= 4.0;
    }

    /** Returns true if it is currently possible to sleep (night or thunderstorm). */
    private boolean isNight() {
        if (ctx.world() == null) return false;
        if (ctx.world().isThundering()) return true;
        long time = ctx.world().getOverworldClockTime() % 24000L;
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    /** Ticks remaining until the next night begins (0 if already night). */
    private long ticksUntilNight() {
        if (ctx.world() == null) return 0;
        long time = ctx.world().getOverworldClockTime() % 24000L;
        if (time >= NIGHT_START && time <= NIGHT_END) return 0;
        return time < NIGHT_START ? NIGHT_START - time : (24000L - time) + NIGHT_START;
    }

    /** Formats a tick count as a human-readable string, e.g. "3m 20s". */
    private static String formatTicks(long ticks) {
        long s = ticks / 20;
        return s < 60 ? s + "s" : (s / 60) + "m " + (s % 60) + "s";
    }

    /**
     * Blocks the calling thread until {@code condition} returns true or
     * {@code maxChecks} checks have been made (each separated by {@code intervalMs} ms).
     */
    private static void waitUntil(BooleanSupplier condition, int maxChecks, long intervalMs)
            throws InterruptedException {
        for (int i = 0; i < maxChecks && !condition.getAsBoolean(); i++) {
            Thread.sleep(intervalMs);
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    // ── Command metadata ────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Navigate to the nearest bed and sleep";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Finds the nearest bed in Baritone's block cache, navigates to it,",
                "and automatically right-clicks to sleep once night falls.",
                "",
                "All 16 bed colours are supported. Any bed the player has previously been",
                "near will be found instantly from the cache.",
                "",
                "If no bed is found:",
                "  1. Walk near your bed so Baritone loads and caches the chunk.",
                "  2. Or run  #repack  to re-cache all currently loaded chunks.",
                "",
                "Usage:",
                "> sleep     - navigate to nearest bed and sleep automatically",
                "",
                "Notes:",
                "  - If it is daytime, the bot will navigate to the bed and wait there until night.",
                "  - Also triggers during thunderstorms.",
                "  - Use #cancel to abort at any time."
        );
    }
}
