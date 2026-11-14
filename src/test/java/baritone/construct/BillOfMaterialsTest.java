package baritone.construct;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class BillOfMaterialsTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void aBlockIsOneOfItsItem() {
        assertEquals(new BillOfMaterials.Cost(Items.STONE_BRICKS, 1), BillOfMaterials.costOf(Blocks.STONE_BRICKS.defaultBlockState()));
        assertSame(BillOfMaterials.NONE, BillOfMaterials.costOf(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void wallVariantsCostTheirStandingItem() {
        assertEquals(new BillOfMaterials.Cost(Items.TORCH, 1), BillOfMaterials.costOf(Blocks.WALL_TORCH.defaultBlockState()));
    }

    @Test
    void twoPartBlocksAreCountedOnce() {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        assertEquals(new BillOfMaterials.Cost(Items.OAK_DOOR, 1), BillOfMaterials.costOf(lower));
        assertSame(BillOfMaterials.NONE, BillOfMaterials.costOf(upper));
        BlockState foot = Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
        BlockState head = Blocks.BED.red().defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        assertEquals(new BillOfMaterials.Cost(Items.BED.red(), 1), BillOfMaterials.costOf(foot));
        assertSame(BillOfMaterials.NONE, BillOfMaterials.costOf(head));
    }

    @Test
    void stackedBlocksCostOneItemEach() {
        assertEquals(new BillOfMaterials.Cost(Items.OAK_SLAB, 2),
            BillOfMaterials.costOf(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE)));
        assertEquals(new BillOfMaterials.Cost(Items.CANDLE, 3),
            BillOfMaterials.costOf(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)));
        assertEquals(new BillOfMaterials.Cost(Items.SNOW, 4),
            BillOfMaterials.costOf(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 4)));
    }

    @Test
    void liquidsAndFireHaveNoItem() {
        assertNull(BillOfMaterials.costOf(Blocks.WATER.defaultBlockState()));
        assertNull(BillOfMaterials.costOf(Blocks.FIRE.defaultBlockState()));
    }

    @Test
    void tallyAddsUpItemsAndListsTheUnplaceable() {
        BillOfMaterials.Tally tally = BillOfMaterials.tally(List.of(
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE),
            Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState()));
        assertEquals(Map.of("minecraft:cobblestone", 2, "minecraft:oak_slab", 2), tally.items());
        assertEquals(Map.of("minecraft:water", 1), tally.unplaceable());
        assertEquals(4, tally.total());
    }

    @Test
    void deficitIsWhatTheInventoryLacks() {
        Map<String, Integer> needed = Map.of("minecraft:cobblestone", 100, "minecraft:oak_planks", 10);
        Map<String, Integer> missing = BillOfMaterials.deficit(needed,
            id -> id.equals("minecraft:oak_planks") ? 12 : id.equals("minecraft:cobblestone") ? 40 : 0);
        assertEquals(Map.of("minecraft:cobblestone", 60), missing);
    }

    @Test
    void aBatchFillsTheFreeSlotsBiggestFirst() {
        Map<String, Integer> deficit = new LinkedHashMap<>();
        deficit.put("minecraft:oak_door", 3);
        deficit.put("minecraft:cobblestone", 300);
        deficit.put("minecraft:glass", 20);
        // 300 cobblestone is 5 stacks, 20 glass 1, 3 doors (stack 64 here for simplicity) 1: 7 slots in all.
        assertEquals(List.of(new BillOfMaterials.Gather("minecraft:cobblestone", 300),
                new BillOfMaterials.Gather("minecraft:glass", 20),
                new BillOfMaterials.Gather("minecraft:oak_door", 3)),
            BillOfMaterials.batch(deficit, 10, id -> 64));
        // With 3 slots the cobblestone gets all three.
        assertEquals(List.of(new BillOfMaterials.Gather("minecraft:cobblestone", 192)),
            BillOfMaterials.batch(deficit, 3, id -> 64));
        assertEquals(List.of(), BillOfMaterials.batch(deficit, 0, id -> 64));
    }
}
