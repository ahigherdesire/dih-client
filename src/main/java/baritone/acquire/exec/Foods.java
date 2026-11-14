package baritone.acquire.exec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads edible items: anything with a food component and a consumable, main inventory and offhand. */
public final class Foods {

    /** Eating time of most food, in ticks. */
    static final int DEFAULT_EAT_TICKS = 32;

    private Foods() {
    }

    /** The stack as a {@link FoodChoice.Food}, or null if it can't be eaten. */
    public static FoodChoice.Food of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return of(InventoryReader.idOf(stack), stack.get(DataComponents.FOOD), stack.get(DataComponents.CONSUMABLE));
    }

    /** An item's food from its components; null unless it has both. */
    static FoodChoice.Food of(String id, FoodProperties food, Consumable consumable) {
        if (food == null || consumable == null) return null;
        boolean harmful = FoodChoice.HARMFUL.contains(id) || harmfulEffect(consumable);
        return new FoodChoice.Food(id, food.nutrition(), food.saturation(), harmful);
    }

    /** Whether eating applies a harmful effect (hunger, poison, nausea, ...), whatever its chance. */
    static boolean harmfulEffect(Consumable consumable) {
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply) || apply.probability() <= 0) continue;
            for (MobEffectInstance instance : apply.effects()) {
                if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
            }
        }
        return false;
    }

    /** Every distinct food in the main inventory and offhand, in slot order. */
    public static List<FoodChoice.Food> held(Player player) {
        Map<String, FoodChoice.Food> foods = new LinkedHashMap<>();
        if (player == null) return List.of();
        for (ItemStack stack : InventoryReader.stacks(player)) {
            FoodChoice.Food food = of(stack);
            if (food != null) foods.putIfAbsent(food.id(), food);
        }
        return new ArrayList<>(foods.values());
    }

    /** Ticks it takes to eat {@code stack}. */
    static int eatTicks(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        return consumable == null ? DEFAULT_EAT_TICKS : Math.max(1, consumable.consumeTicks());
    }
}
