package baritone.construct;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * What a schematic costs to build, in items: one item per block, except where a block takes more or less (a double
 * slab is two slabs, a door's top half and a bed's head come with the other half, a cluster of candles or sea pickles
 * is one item each). Blocks that no item places (water, fire, a portal) are listed apart.
 */
public final class BillOfMaterials {

    /** Items a block state takes: {@link #NONE} for air and the halves placed with their partner, null for no item. */
    public record Cost(Item item, int count) {
    }

    public static final Cost NONE = new Cost(Items.AIR, 0);

    /** The items still to place, by item id, and the block ids nothing can place, with how many of each. */
    public record Tally(Map<String, Integer> items, Map<String, Integer> unplaceable) {
        public boolean isEmpty() {
            return items.isEmpty();
        }

        public int total() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private BillOfMaterials() {
    }

    /** What placing {@code state} takes, {@link #NONE} if nothing, or null if no item can place it. */
    public static Cost costOf(BlockState state) {
        if (state == null || state.isAir()) return NONE;
        Block block = state.getBlock();
        if (block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON) return NONE;
        if (block instanceof LiquidBlock) return null;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return NONE;
        if (state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return NONE;
        Item item = block.asItem();
        if (item == Items.AIR) return null;
        int count = 1;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) count = 2;
        else if (state.hasProperty(BlockStateProperties.CANDLES)) count = state.getValue(BlockStateProperties.CANDLES);
        else if (state.hasProperty(BlockStateProperties.PICKLES)) count = state.getValue(BlockStateProperties.PICKLES);
        else if (state.hasProperty(BlockStateProperties.EGGS)) count = state.getValue(BlockStateProperties.EGGS);
        else if (block == Blocks.SNOW && state.hasProperty(BlockStateProperties.LAYERS)) count = state.getValue(BlockStateProperties.LAYERS);
        return new Cost(item, count);
    }

    /** Adds up the cost of every state; ids are namespaced item ids for items and block ids for the unplaceable. */
    public static Tally tally(Iterable<BlockState> states) {
        Map<String, Integer> items = new TreeMap<>();
        Map<String, Integer> unplaceable = new TreeMap<>();
        for (BlockState state : states) {
            Cost cost = costOf(state);
            if (cost == null) {
                unplaceable.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
            } else if (cost.count() > 0) {
                items.merge(BuiltInRegistries.ITEM.getKey(cost.item()).toString(), cost.count(), Integer::sum);
            }
        }
        return new Tally(items, unplaceable);
    }

    /** What is still missing: {@code needed} less what is {@code held} of each, without the items at or below zero. */
    public static Map<String, Integer> deficit(Map<String, Integer> needed, ToIntFunction<String> held) {
        Map<String, Integer> out = new LinkedHashMap<>();
        needed.forEach((id, count) -> {
            int missing = count - Math.max(0, held.applyAsInt(id));
            if (missing > 0) out.put(id, missing);
        });
        return out;
    }

    /** One item to gather in a batch: get {@code count} more of {@code item}. */
    public record Gather(String item, int count) {
    }

    /**
     * The next batch to gather: the biggest shortfalls first, each taking whole stacks of inventory room, until
     * {@code freeSlots} are used. The last item that doesn't fit gets the stacks that are left; a batch never comes
     * back empty while something is missing and there is a slot free.
     */
    public static List<Gather> batch(Map<String, Integer> deficit, int freeSlots, ToIntFunction<String> stackSize) {
        List<Map.Entry<String, Integer>> order = new ArrayList<>(deficit.entrySet());
        order.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
        List<Gather> batch = new ArrayList<>();
        int slots = freeSlots;
        for (Map.Entry<String, Integer> entry : order) {
            if (slots <= 0) break;
            int stack = Math.max(1, stackSize.applyAsInt(entry.getKey()));
            int count = entry.getValue();
            int needSlots = (count + stack - 1) / stack;
            if (needSlots <= slots) {
                batch.add(new Gather(entry.getKey(), count));
                slots -= needSlots;
            } else {
                batch.add(new Gather(entry.getKey(), slots * stack));
                slots = 0;
            }
        }
        return batch;
    }

    /** "12 oak_planks, 30 cobblestone" for chat, shortest ids. */
    public static String describe(Map<String, Integer> items, int max) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (shown == max) {
                out.append(", and ").append(items.size() - max).append(" more");
                break;
            }
            if (shown > 0) out.append(", ");
            String id = entry.getKey();
            out.append(entry.getValue()).append(' ').append(id.startsWith("minecraft:") ? id.substring(10) : id);
            shown++;
        }
        return out.toString();
    }
}
