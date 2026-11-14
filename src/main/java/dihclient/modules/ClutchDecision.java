package dihclient.modules;

/**
 * Pure choice for the Clutch module's "Clutch With" setting: whether a given tick should place water
 * (an MLG bucket), fall through to the block clutch, or wait for the ground to come into reach. Kept
 * free of Minecraft types so it can be unit tested on its own.
 */
final class ClutchDecision {
    static final String BLOCKS = "Blocks";
    static final String WATER = "Water";
    static final String AUTO = "Auto";

    enum Plan {
        /** Place an MLG water bucket on the ground below. */
        WATER,
        /** Use the block clutch (pillar, or place against a wall). */
        BLOCKS,
        /** Hold off this tick; water is wanted but the ground is not in reach yet. */
        WAIT
    }

    private ClutchDecision() {
    }

    /**
     * @param method          the "Clutch With" value: Blocks, Water or Auto
     * @param nether          true in a dimension where water would vanish (the Nether)
     * @param hasWaterBucket  a water bucket is on the hotbar
     * @param fallBigEnough   the fall so far is large enough to be worth an MLG
     * @param groundReachable solid ground to place the water on is within reach below
     */
    static Plan plan(String method, boolean nether, boolean hasWaterBucket,
                     boolean fallBigEnough, boolean groundReachable) {
        boolean wantsWater = WATER.equals(method) || AUTO.equals(method);
        if (!wantsWater) return Plan.BLOCKS;
        // Water cannot help in the Nether or without a bucket: clutch with blocks instead ("on wall if nether").
        if (nether || !hasWaterBucket) return Plan.BLOCKS;
        if (groundReachable && fallBigEnough) return Plan.WATER;
        // Ground is not in reach yet. Auto keeps the block clutch as a safety net; strict Water waits.
        return AUTO.equals(method) ? Plan.BLOCKS : Plan.WAIT;
    }
}
