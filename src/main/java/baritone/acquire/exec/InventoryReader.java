package baritone.acquire.exec;

import baritone.acquire.model.InventorySnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads the player's main inventory (36 slots: hotbar 0-8, then 9-35) and offhand as namespaced item
 * ids. Armour and the crafting grid are not counted. This is the same view the planner plans from and
 * the executor checks {@code untilCount} against.
 */
public final class InventoryReader {

    /** Size of the main inventory, hotbar included. */
    static final int MAIN_SIZE = 36;

    private InventoryReader() {
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The item for a namespaced id ("minecraft:torch"; a bare "torch" works too), or null. */
    public static Item itemOf(String id) {
        if (id == null) return null;
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    public static InventorySnapshot snapshot(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks(player)) {
            if (!stack.isEmpty()) counts.merge(idOf(stack), stack.getCount(), Integer::sum);
        }
        return new InventorySnapshot(counts);
    }

    /** Count of {@code id} in the main inventory and offhand. */
    public static int count(Player player, String id) {
        Item item = itemOf(id);
        if (item == null || player == null) return 0;
        int total = 0;
        for (ItemStack stack : stacks(player)) {
            if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** The 36 main slots followed by the offhand. */
    static List<ItemStack> stacks(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        all.add(player.getOffhandItem());
        return all;
    }

    /** Main-inventory index (0-35) of the first stack matching {@code want}, hotbar first; -1 if none. */
    static int find(Player player, Predicate<ItemStack> want) {
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(MAIN_SIZE, main.size()); i++) {
            ItemStack stack = main.get(i);
            if (!stack.isEmpty() && want.test(stack)) return i;
        }
        return -1;
    }

    /** Whether one more {@code item} fits: an empty main slot or a stack of it that is not full. */
    static boolean hasRoomFor(Player player, Item item) {
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(MAIN_SIZE, main.size()); i++) {
            ItemStack stack = main.get(i);
            if (stack.isEmpty()) return true;
            if (item != null && stack.is(item) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }
}
