package baritone.acquire.model;

/** One way to obtain an item. Item, block and entity ids are namespaced registry ids ("minecraft:oak_log"). */
public sealed interface Source permits CraftSource, SmeltSource, MineSource, KillSource {
    /** The item this source produces. */
    String output();

    /** Expected items produced per action (per craft, per smelt, per block broken, per kill). */
    double outputPerAction();
}
