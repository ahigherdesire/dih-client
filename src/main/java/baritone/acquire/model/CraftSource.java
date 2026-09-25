package baritone.acquire.model;

import java.util.List;

/** A crafting recipe. {@code needsTable} is true when it does not fit the 2x2 inventory grid. */
public record CraftSource(String recipeId, String output, int outputCount, List<Ingredient> ingredients, boolean needsTable)
        implements Source {
    public CraftSource {
        ingredients = List.copyOf(ingredients);
    }

    @Override
    public double outputPerAction() {
        return outputCount;
    }
}
