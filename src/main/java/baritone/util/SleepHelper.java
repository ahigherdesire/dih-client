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

package baritone.util;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Shared sleep / bed logic used by {@code SleepCommand} (explicit) and
 * {@code AutopilotBehavior} (when {@code autoSleep} is enabled).
 *
 * <p>Encapsulates bed-finding (all 16 colours, X-Z centred — passing
 * {@code origin.y} as the Z coordinate was a bug fixed earlier) and the
 * standard "is it time to sleep" check.
 */
public final class SleepHelper {

    /** In-game time tick when night begins (out of 24000). */
    public static final long NIGHT_START = 12541L;
    /** In-game time tick when night ends. */
    public static final long NIGHT_END   = 23459L;

    /** All 16 bed colours — all tracked in {@code CachedChunk.BLOCKS_TO_KEEP_TRACK_OF}. */
    // MC 26.2: the 16 per-colour bed blocks are consolidated into the Blocks.BED ColorCollection.
    public static final List<Block> ALL_BEDS = Blocks.BED.asList();

    private SleepHelper() {}

    /**
     * Finds the nearest cached bed of any colour to the player.
     *
     * @return the bed location, or empty if no bed is in the cache
     */
    public static Optional<BlockPos> findNearestBed(IPlayerContext ctx) {
        if (ctx.world() == null || ctx.player() == null) {
            return Optional.empty();
        }
        final BetterBlockPos origin = ctx.playerFeet();
        double nearestDistSq = Double.MAX_VALUE;
        BlockPos nearestBed  = null;

        for (Block bed : ALL_BEDS) {
            final String blockName = BuiltInRegistries.BLOCK.getKey(bed).getPath();
            // X-Z centred search (origin.x, origin.z) — origin.y would be a bug.
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
        return Optional.ofNullable(nearestBed);
    }

    /**
     * Returns true if it is currently possible to sleep
     * (night-time or thundering in the overworld).
     */
    public static boolean isNightOrStorm(Level world) {
        if (world == null) return false;
        if (world.isThundering()) return true;
        long time = world.getOverworldClockTime() % 24000L;
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    /** Ticks remaining until the next night begins (0 if already night). */
    public static long ticksUntilNight(Level world) {
        if (world == null) return 0;
        long time = world.getOverworldClockTime() % 24000L;
        if (time >= NIGHT_START && time <= NIGHT_END) return 0;
        return time < NIGHT_START ? NIGHT_START - time : (24000L - time) + NIGHT_START;
    }
}
