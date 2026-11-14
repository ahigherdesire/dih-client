package baritone.acquire.model;

/** Vanilla tool-use budgets; unknown tools remain usable without a guessed cap. */
public final class ToolDurability {
    private ToolDurability() {}

    public static int maxUses(String item) {
        if (item == null) return 0;
        if (item.equals("minecraft:shears")) return 238;
        if (item.equals("minecraft:flint_and_steel")) return 64;
        if (!(item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel")
                || item.endsWith("_hoe") || item.endsWith("_sword"))) return 0;
        if (item.startsWith("minecraft:wooden_")) return 59;
        if (item.startsWith("minecraft:stone_")) return 131;
        if (item.startsWith("minecraft:iron_")) return 250;
        if (item.startsWith("minecraft:golden_")) return 32;
        if (item.startsWith("minecraft:diamond_")) return 1561;
        if (item.startsWith("minecraft:netherite_")) return 2031;
        return 0;
    }

    /** Expected mining and incidental path digging, rounded up. */
    public static int budget(int blocks) {
        return Math.max(0, (int) Math.ceil(blocks * 1.25));
    }
}
