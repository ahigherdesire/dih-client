package dihclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.3 {
/*import net.minecraft.world.level.block.BonemealSource;
*///?}

/** Block queries that 26.3 removed or reshaped. */
public final class DihBlocks {

    private DihBlocks() {
    }

    /** Whether {@code state} stops movement: 26.2's {@code blocksMotion()} (solid, except cobweb and bamboo sapling). */
    public static boolean blocksMotion(BlockState state) {
        return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && state.isSolid();
    }

    /** Whether a player's bone meal would take on {@code state}. */
    public static boolean isValidBonemealTarget(BonemealableBlock block, LevelReader level, BlockPos pos, BlockState state) {
        //? if >=26.3 {
        /*return block.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION);
        *///?} else {
        return block.isValidBonemealTarget(level, pos, state);
        //?}
    }

    public static boolean isBonemealSuccess(BonemealableBlock block, Level level, RandomSource random, BlockPos pos, BlockState state) {
        //? if >=26.3 {
        /*return block.isBonemealSuccess(level, random, pos, state, BonemealSource.INTERACTION);
        *///?} else {
        return block.isBonemealSuccess(level, random, pos, state);
        //?}
    }
}
