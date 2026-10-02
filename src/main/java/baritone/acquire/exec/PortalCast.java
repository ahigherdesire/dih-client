package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A nether portal frame cast in place from buckets, the speedrunner way: no obsidian is mined, so no diamond pickaxe is
 * needed. The frame stands in one vertical plane with a wall of any blocks right behind it. Every frame cell is poured
 * through the face of the wall block behind it (the bottom two through the ground under them). For each cell, water
 * goes into a neighbouring cell first (in the opening, or a top corner), then lava into the cell, which turns to
 * obsidian at once because water touches it; then the water is scooped back up. Water only counts beside or above
 * lava, never below, so the top row takes its water from the top corners.
 *
 * <p>Positions are {@code (i, j, k)} from {@code origin}, the frame's bottom-left cell: {@code i} along {@code right},
 * {@code j} up, {@code k} toward {@code front}, where the player stands (the wall is at {@code k = -1}). The top row is
 * cast first and each side column top-down, so the look ray to a cell, which climbs from the eye, never passes
 * through obsidian cast below it.
 */
record PortalCast(BlockPos origin, Direction right, Direction front) {

    /** Mould blocks the wall takes when none of it is there already. */
    static final int WALL_BLOCKS = 20;

    /**
     * One frame cell: the lava goes into {@code lava} through {@code lavaFace} of {@code lavaVia}, after water went
     * into {@code water} through {@code waterFace} of {@code waterVia}; both are poured from {@code stand}.
     */
    record Cell(BlockPos lava, BlockPos lavaVia, Direction lavaFace,
                BlockPos water, BlockPos waterVia, Direction waterFace, BlockPos stand) {
    }

    /** What the finder needs to know about the world. */
    interface Terrain {
        /** A full solid block that stays put: ground to stand on, or a wall block. */
        boolean floor(BlockPos pos);

        /** Air with no fluid. */
        boolean air(BlockPos pos);

        /** Something in the way that can be dug out (not bedrock, obsidian or a fluid). */
        boolean diggable(BlockPos pos);

        /** Empty enough to put a mould block in (air, grass, snow), with no fluid. */
        boolean placeable(BlockPos pos);

        /** Sand or gravel: it falls into anything dug out below it. */
        boolean falls(BlockPos pos);

        /** Any lava, source or flowing. */
        boolean lava(BlockPos pos);
    }

    /** A site the finder picked and what it takes to get it ready. */
    record Found(PortalCast cast, int digs, int places) {
    }

    /**
     * How close lava may come to anything the cast uses, in front and to the sides. Water poured into the frame
     * spills out through the row in front and runs 7 blocks on flat ground before it's taken back, and lava it
     * reaches turns to obsidian: a pool any closer is spent before the frame is.
     */
    static final int LAVA_CLEARANCE = 8;

    BlockPos at(int i, int j, int k) {
        return origin.relative(right, i).above(j).relative(front, k);
    }

    /** In casting order: the top row, the left column top-down, the right column top-down, the bottom row. */
    List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        out.add(cell(1, 4, 0, 4));
        out.add(cell(2, 4, 3, 4));
        for (int j = 3; j >= 1; j--) out.add(cell(0, j, 1, j));
        for (int j = 3; j >= 1; j--) out.add(cell(3, j, 2, j));
        out.add(cell(1, 0, 1, 1));
        out.add(cell(2, 0, 2, 1));
        return out;
    }

    private Cell cell(int i, int j, int wi, int wj) {
        BlockPos lavaVia = j == 0 ? at(i, -1, 0) : at(i, j, -1);
        Direction lavaFace = j == 0 ? Direction.UP : front;
        return new Cell(at(i, j, 0), lavaVia, lavaFace, at(wi, wj, 0), at(wi, wj, -1), front, stand(i >= 2));
    }

    /** The ten frame cells. */
    List<BlockPos> frame() {
        return cells().stream().map(Cell::lava).toList();
    }

    /** The 2 by 3 opening, where the portal forms. */
    List<BlockPos> interior() {
        List<BlockPos> out = new ArrayList<>();
        for (int j = 1; j <= 3; j++) for (int i = 1; i <= 2; i++) out.add(at(i, j, 0));
        return out;
    }

    /** The bottom cell whose top the flint and steel strikes. */
    BlockPos ignite() {
        return at(1, 0, 0);
    }

    /** Where the player stands for the left half of the frame, or the right half. */
    BlockPos stand(boolean rightHalf) {
        return at(rightHalf ? 2 : 1, 0, 2);
    }

    /** The wall behind the frame, bottom-up, so each block rests on the ground or the one placed before it. */
    List<BlockPos> wall() {
        List<BlockPos> out = new ArrayList<>();
        for (int j = 0; j <= 4; j++) for (int i = 0; i <= 3; i++) out.add(at(i, j, -1));
        return out;
    }

    /** Solid ground under the wall, the frame, and the two rows in front where the player stands. */
    List<BlockPos> ground() {
        List<BlockPos> out = new ArrayList<>();
        for (int k = -1; k <= 2; k++) for (int i = 0; i <= 3; i++) out.add(at(i, -1, k));
        return out;
    }

    /**
     * What must be open: the frame plane but its bottom corners, the full height of the row in front (the rays to
     * the top go through it), and head room over the row the player stands on.
     */
    List<BlockPos> open() {
        List<BlockPos> out = new ArrayList<>();
        for (int j = 0; j <= 4; j++) {
            for (int i = 0; i <= 3; i++) {
                if (j != 0 || i == 1 || i == 2) out.add(at(i, j, 0));
                out.add(at(i, j, 1));
                if (j <= 2) out.add(at(i, j, 2));
            }
        }
        return out;
    }

    /**
     * The cheapest site near {@code near}: its left stand within {@code radius} blocks and 3 up or down, facing any
     * of the four ways, with at most {@code maxDigs} blocks to dig out, and its origin not in {@code bad}. Each block
     * to dig counts three, each mould block one, each block of distance two, since the lava is fetched from near
     * {@code near} for all ten cells.
     */
    static Found find(BlockPos near, int radius, int maxDigs, Set<BlockPos> bad, Terrain t) {
        List<BlockPos> lava = new ArrayList<>();
        int margin = LAVA_CLEARANCE + 8;
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-radius - margin, -3 - margin, -radius - margin),
                near.offset(radius + margin, 3 + margin, radius + margin))) {
            if (t.lava(p)) lava.add(p.immutable());
        }
        Found best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos stand = near.offset(dx, dy, dz);
                    double distance = Math.sqrt(stand.distSqr(near));
                    if (2 * distance >= bestScore) continue;
                    for (Direction front : Direction.Plane.HORIZONTAL) {
                        Direction right = front.getCounterClockWise();
                        // The left stand is (1, 0, 2) from the origin.
                        PortalCast cast = new PortalCast(stand.relative(right, -1).relative(front, -2), right, front);
                        if (bad.contains(cast.origin())) continue;
                        Found found = cast.evaluate(t, maxDigs, bestScore - 2 * distance, lava);
                        if (found == null) continue;
                        double score = 3 * found.digs() + found.places() + 2 * distance;
                        if (score < bestScore) {
                            best = found;
                            bestScore = score;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** What this site takes, or null if it can't be used (or can't beat {@code budget}). */
    Found evaluate(Terrain t, int maxDigs, double budget, List<BlockPos> lava) {
        for (BlockPos pos : ground()) if (!t.floor(pos)) return null;
        int digs = 0;
        for (BlockPos pos : open()) {
            if (t.air(pos)) continue;
            if (!t.diggable(pos) || t.falls(pos) || ++digs > maxDigs || 3 * digs >= budget) return null;
        }
        // Sand or gravel resting on what gets dug out would fall into the frame.
        if (digs > 0) {
            for (int i = 0; i <= 3; i++) {
                if (t.falls(at(i, 5, 0)) || t.falls(at(i, 5, 1)) || t.falls(at(i, 3, 2))) return null;
            }
        }
        int places = 0;
        for (BlockPos pos : wall()) {
            if (t.floor(pos)) continue;
            if (!t.placeable(pos) || 3 * digs + ++places >= budget) return null;
        }
        for (BlockPos p : lava) if (tooClose(p)) return null;
        return new Found(this, digs, places);
    }

    /** Whether lava at {@code p} is within {@link #LAVA_CLEARANCE} of the site, in any direction. */
    boolean tooClose(BlockPos p) {
        int dx = p.getX() - origin.getX(), dz = p.getZ() - origin.getZ();
        int i = dx * right.getStepX() + dz * right.getStepZ();
        int j = p.getY() - origin.getY();
        int k = dx * front.getStepX() + dz * front.getStepZ();
        int c = LAVA_CLEARANCE;
        // The site's box: i 0 to 3, j -1 (the ground) to 4, k -1 (the wall) to 2 (the stand row).
        return i >= -c && i <= 3 + c && j >= -1 - c && j <= 4 + c && k >= -1 - c && k <= 2 + c;
    }
}
