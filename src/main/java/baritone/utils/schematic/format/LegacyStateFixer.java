/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.format;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;

/**
 * Completes block states converted from pre-1.13 ids. Before the flattening, some of a block's look lived in its tile
 * entity (bed and banner colours, flower pot contents, skull types, note block notes) or was worked out from its
 * neighbours (door halves, double plants, snowy grass, fence and pane connections); the id and data value alone
 * cannot give it.
 * <p>
 * Ported from Litematica's {@code fi.dy.masa.litematica.schematic.conversion.SchematicConversionFixers} by maruohon
 * (LGPLv3): the same fixes, keyed by the same old tile entity tags. Walls, stairs, redstone and other shapes the game
 * recomputes when the block is placed are left as converted.
 */
public final class LegacyStateFixer {

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final BooleanProperty[] CONNECTIONS = {
        BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST
    };
    /**
     * 1.12 skull types by their "SkullType" byte.
     */
    private static final String[] SKULLS = {"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"};
    private static final String[] WALL_SKULLS = {"skeleton_wall_skull", "wither_skeleton_wall_skull", "zombie_wall_head", "player_wall_head", "creeper_wall_head", "dragon_wall_head"};

    private LegacyStateFixer() {}

    /**
     * Fixes {@code states} (indexed {@code [x][z][y]}) in place.
     *
     * @param tiles the file's tile entities as saved, before any data fixing, by position
     */
    public static void fix(BlockState[][][] states, Map<BlockPos, CompoundTag> tiles) {
        View view = new View(states);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < view.sx; x++) {
            for (int z = 0; z < view.sz; z++) {
                for (int y = 0; y < view.sy; y++) {
                    CompoundTag tile = tiles.get(pos.set(x, y, z));
                    if (tile != null) {
                        states[x][z][y] = fromTile(states[x][z][y], tile, view, pos);
                    }
                }
            }
        }
        // neighbours last, so they see the blocks the tile entities completed
        for (int y = 0; y < view.sy; y++) {
            for (int z = 0; z < view.sz; z++) {
                for (int x = 0; x < view.sx; x++) {
                    states[x][z][y] = fromNeighbours(states[x][z][y], view, pos.set(x, y, z));
                }
            }
        }
    }

    private static BlockState fromTile(BlockState state, CompoundTag tile, View view, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof BedBlock && tile.contains("color")) {
            int color = tile.getIntOr("color", -1);
            return color >= 0 && color < 16 ? swap(state, DyeColor.byId(color).getName() + "_bed") : state;
        }
        if ((block instanceof BannerBlock || block instanceof WallBannerBlock) && tile.contains("Base")) {
            // banner bases counted down from black in 1.12
            String color = DyeColor.byId(15 - (tile.getIntOr("Base", 0) & 15)).getName();
            return swap(state, color + (block instanceof WallBannerBlock ? "_wall_banner" : "_banner"));
        }
        if (block instanceof FlowerPotBlock) {
            String potted = pottedPlant(tile.getStringOr("Item", ""), tile.getIntOr("Data", 0));
            return potted != null ? swap(state, potted) : state;
        }
        if ((block instanceof SkullBlock || block instanceof WallSkullBlock) && tile.contains("SkullType")) {
            int type = Mth.clamp(tile.getIntOr("SkullType", 0), 0, SKULLS.length - 1);
            if (block instanceof WallSkullBlock) {
                return swap(state, WALL_SKULLS[type]);
            }
            BlockState skull = swap(state, SKULLS[type]);
            return skull.setValue(BlockStateProperties.ROTATION_16, Mth.clamp(tile.getIntOr("Rot", 0), 0, 15));
        }
        if (block instanceof NoteBlock) {
            BlockState below = view.getBlockState(pos.below());
            return state
                .setValue(BlockStateProperties.POWERED, tile.getBooleanOr("powered", false))
                .setValue(BlockStateProperties.NOTE, Mth.clamp(tile.getIntOr("note", 0), 0, 24))
                .setValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT, below.instrument());
        }
        return state;
    }

    private static BlockState fromNeighbours(BlockState state, View view, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                BlockState lower = view.getBlockState(pos.below());
                if (lower.is(block)) {
                    return state.setValue(DoorBlock.FACING, lower.getValue(DoorBlock.FACING))
                        .setValue(DoorBlock.OPEN, lower.getValue(DoorBlock.OPEN));
                }
            } else {
                BlockState upper = view.getBlockState(pos.above());
                if (upper.is(block)) {
                    return state.setValue(DoorBlock.HINGE, upper.getValue(DoorBlock.HINGE))
                        .setValue(DoorBlock.POWERED, upper.getValue(DoorBlock.POWERED));
                }
            }
            return state;
        }
        if (block instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = view.getBlockState(pos.below());
            return lower.getBlock() instanceof DoublePlantBlock ? lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER) : state;
        }
        if (block instanceof SnowyBlock) {
            BlockState above = view.getBlockState(pos.above());
            return state.setValue(SnowyBlock.SNOWY, above.is(Blocks.SNOW_BLOCK) || above.is(Blocks.SNOW));
        }
        if (block instanceof FenceBlock fence) {
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction side = HORIZONTAL[i];
                BlockPos adjPos = pos.relative(side);
                BlockState adj = view.getBlockState(adjPos);
                boolean sturdy = adj.isFaceSturdy(view, adjPos, side.getOpposite());
                // the game tells fences apart by tag; nether brick fences join only each other
                boolean sameKind = adj.getBlock() instanceof FenceBlock
                    && adj.is(Blocks.NETHER_BRICK_FENCE) == state.is(Blocks.NETHER_BRICK_FENCE);
                state = state.setValue(CONNECTIONS[i], sameKind || fence.connectsTo(adj, sturdy, side.getOpposite()));
            }
            return state;
        }
        if (block instanceof IronBarsBlock pane) {
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction side = HORIZONTAL[i];
                BlockPos adjPos = pos.relative(side);
                BlockState adj = view.getBlockState(adjPos);
                boolean sturdy = adj.isFaceSturdy(view, adjPos, side.getOpposite());
                state = state.setValue(CONNECTIONS[i], pane.attachsTo(adj, sturdy));
            }
            return state;
        }
        if (block instanceof FenceGateBlock) {
            Direction facing = state.getValue(FenceGateBlock.FACING);
            Direction left = facing.getClockWise();
            boolean inWall = view.getBlockState(pos.relative(left)).getBlock() instanceof WallBlock
                || view.getBlockState(pos.relative(left.getOpposite())).getBlock() instanceof WallBlock;
            return state.setValue(FenceGateBlock.IN_WALL, inWall);
        }
        return state;
    }

    /**
     * @return the 1.13 potted block for a 1.12 flower pot's "Item" and "Data", or null to keep the empty pot
     */
    private static String pottedPlant(String item, int data) {
        return switch (item) {
            case "minecraft:sapling" -> switch (data) {
                case 0 -> "potted_oak_sapling";
                case 1 -> "potted_spruce_sapling";
                case 2 -> "potted_birch_sapling";
                case 3 -> "potted_jungle_sapling";
                case 4 -> "potted_acacia_sapling";
                case 5 -> "potted_dark_oak_sapling";
                default -> null;
            };
            case "minecraft:tallgrass" -> data == 0 ? "potted_dead_bush" : data == 2 ? "potted_fern" : null;
            case "minecraft:red_flower" -> switch (data) {
                case 0 -> "potted_poppy";
                case 1 -> "potted_blue_orchid";
                case 2 -> "potted_allium";
                case 3 -> "potted_azure_bluet";
                case 4 -> "potted_red_tulip";
                case 5 -> "potted_orange_tulip";
                case 6 -> "potted_white_tulip";
                case 7 -> "potted_pink_tulip";
                case 8 -> "potted_oxeye_daisy";
                default -> null;
            };
            case "minecraft:yellow_flower" -> "potted_dandelion";
            case "minecraft:brown_mushroom" -> "potted_brown_mushroom";
            case "minecraft:red_mushroom" -> "potted_red_mushroom";
            case "minecraft:deadbush" -> "potted_dead_bush";
            case "minecraft:cactus" -> "potted_cactus";
            default -> null;
        };
    }

    /**
     * @return the named block, keeping every property of {@code state} it shares
     */
    private static BlockState swap(BlockState state, String name) {
        Identifier id = Identifier.withDefaultNamespace(name);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return state;
        }
        BlockState out = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            out = copy(state, out, property);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /**
     * The schematic as a world, for the game's own shape checks. Outside it is air.
     */
    private static final class View implements BlockGetter {

        private final BlockState[][][] states;
        private final int sx, sy, sz;

        View(BlockState[][][] states) {
            this.states = states;
            this.sx = states.length;
            this.sz = this.sx == 0 ? 0 : states[0].length;
            this.sy = this.sz == 0 ? 0 : states[0][0].length;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= this.sx || y >= this.sy || z >= this.sz) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.states[x][z][y];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return Math.max(this.sy, 1);
        }

        @Override
        public int getMinY() {
            return 0;
        }
    }
}
