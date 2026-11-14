package baritone.acquire.exec;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A test's pad has to be loaded before it's built: {@code /fill} away from the spawn chunks, or anywhere in the Nether
 * before the player gets there, fails with "That position is not loaded", and the test then runs on natural terrain.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TestPads {
    private static final int LOAD_TICKS = 20 * 30;

    private TestPads() {
    }

    /** Forces the chunks over x1..x2, z1..z2 in {@code dimension} and waits until they're all loaded. */
    public static void load(ClientGameTestContext context, TestSingleplayerContext world, ResourceKey<Level> dimension,
                     int x1, int z1, int x2, int z2) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.getLevel(dimension);
            for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
                for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) level.setChunkForced(cx, cz, true);
            }
        });
        for (int i = 0; i < LOAD_TICKS; i++) {
            if (world.getServer().computeOnServer(server -> server.getLevel(dimension).hasChunksAt(x1, z1, x2, z2))) return;
            context.waitTick();
        }
        throw new AssertionError("the pad's chunks at " + x1 + "," + z1 + " to " + x2 + "," + z2 + " never loaded");
    }

    /** Fails unless {@code pos} in {@code dimension} is {@code block}: the pad really was built. */
    public static void expect(TestSingleplayerContext world, ResourceKey<Level> dimension, BlockPos pos, Block block) {
        boolean built = world.getServer().computeOnServer(server -> server.getLevel(dimension).getBlockState(pos).is(block));
        if (!built) throw new AssertionError("the pad wasn't built: " + pos.toShortString() + " isn't " + block);
    }
}
