package baritone.acquire.model;

/**
 * A cooking recipe.
 *
 * @param station {@code minecraft:furnace}, {@code minecraft:blast_furnace} or {@code minecraft:smoker}
 */
public record SmeltSource(String recipeId, String output, int outputCount, Ingredient input, String station, int cookTicks)
        implements Source {
    @Override
    public double outputPerAction() {
        return outputCount;
    }
}
