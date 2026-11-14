package baritone.acquire.model;

/**
 * What a block needs to be mined for its drops.
 *
 * @param type     {@code pickaxe}, {@code axe}, {@code shovel}, {@code hoe}, {@code shears}, {@code sword}, or null for hand
 * @param minTier  0 = anything (hand works), 1 = wood/gold, 2 = stone, 3 = iron, 4 = diamond, 5 = netherite
 * @param required true if the block drops nothing without a matching tool (stone, ores); false if the tool only speeds it up (logs, dirt)
 */
public record ToolReq(String type, int minTier, boolean required) {
    public static final ToolReq NONE = new ToolReq(null, 0, false);
}
