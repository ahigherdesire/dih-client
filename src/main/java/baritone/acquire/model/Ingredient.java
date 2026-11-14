package baritone.acquire.model;

import java.util.List;

/**
 * {@code count} items, each of which may be any of {@code anyOf} (item ids, tags already expanded).
 * A shaped recipe with four oak-or-birch-or-... plank slots is one Ingredient(planks..., 4).
 */
public record Ingredient(List<String> anyOf, int count) {
    public Ingredient {
        anyOf = List.copyOf(anyOf);
    }

    public boolean accepts(String item) {
        return anyOf.contains(item);
    }
}
