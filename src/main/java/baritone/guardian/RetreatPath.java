package baritone.guardian;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Where a retreat falls back to along the crumbs of the path already walked. A spot once chosen is kept until reached,
 * then the next one further back is taken: choosing afresh every tick flips between two spots as each comes near, and
 * the player stands still under fire.
 */
final class RetreatPath {

    /** Reached at this distance (a retreat walks to within a block of a crumb). */
    static final int REACHED = 2;
    /** A crumb this close is no retreat at all. */
    static final int TOO_NEAR = 3;

    private RetreatPath() {}

    /**
     * The crumb to walk to, or null when none is far enough from the danger.
     *
     * @param crumbs  the path walked, oldest first
     * @param current the crumb being walked to, or null
     * @param feet    the player's position
     * @param danger  the threat's position
     * @param away    how far from the danger a crumb must be
     */
    static BlockPos next(List<BlockPos> crumbs, BlockPos current, BlockPos feet, BlockPos danger, double away) {
        double awaySq = away * away;
        int from = crumbs.size() - 1;
        int at = current == null ? -1 : crumbs.indexOf(current);
        if (at >= 0) {
            if (!current.closerThan(feet, REACHED) && current.distSqr(danger) >= awaySq) return current;
            // Reached, or the threat came over to it: on further back.
            from = at - 1;
        }
        for (int i = from; i >= 0; i--) {
            BlockPos crumb = crumbs.get(i);
            if (crumb.distSqr(danger) >= awaySq && !crumb.closerThan(feet, TOO_NEAR)) return crumb;
        }
        return null;
    }
}
