package baritone.acquire.exec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the food a player carries as {@link FoodChoice.Food}s, from the item components. */
final class Foods {

    private Foods() {
    }

    /** Each kind of edible food in the main inventory and offhand, once. */
    static List<FoodChoice.Food> held(Player player) {
        Map<String, FoodChoice.Food> foods = new LinkedHashMap<>();
        for (ItemStack stack : InventoryReader.stacks(player)) {
            FoodChoice.Food food = of(stack);
            if (food != null) foods.putIfAbsent(food.id(), food);
        }
        return new ArrayList<>(foods.values());
    }

    /** The stack as food, or null when it is not something you eat. */
    static FoodChoice.Food of(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.CONSUMABLE)) return null;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return null;
        return new FoodChoice.Food(InventoryReader.idOf(stack), food.nutrition(), food.saturation(), food.canAlwaysEat());
    }
}
