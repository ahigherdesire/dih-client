package baritone.parkour.record;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A recorded course survives saving and loading: blocks (with their states), start, facing and route, moved to 0. */
final class CourseRecordingTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void roundTrips() throws Exception {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(new BlockPos(100, 64, -50), Blocks.STONE.defaultBlockState());
        blocks.put(new BlockPos(103, 64, -50), Blocks.OAK_FENCE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceBlock.EAST, true));
        blocks.put(new BlockPos(103, 65, -50), Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        blocks.put(new BlockPos(106, 66, -47), Blocks.STONE_SLAB.defaultBlockState());
        List<BlockPos> route = List.of(new BlockPos(100, 64, -50), new BlockPos(103, 64, -50), new BlockPos(106, 66, -47));
        CourseRecording original = new CourseRecording(blocks, new BlockPos(100, 65, -50), -90.0F, route);

        Path file = Files.createTempDirectory("dih-course").resolve("course.nbt");
        original.save(file);
        CourseRecording loaded = CourseRecording.load(file);

        BlockPos min = new BlockPos(100, 64, -50);
        Map<BlockPos, BlockState> expected = new HashMap<>();
        blocks.forEach((p, s) -> expected.put(p.subtract(min), s));
        assertEquals(expected, loaded.blocks);
        assertEquals(new BlockPos(0, 1, 0), loaded.start);
        assertEquals(-90.0F, loaded.startYaw);
        assertEquals(route.stream().map(p -> p.subtract(min)).toList(), loaded.route);
        assertEquals(List.of(new BlockPos(3, 1, 0)), loaded.plates());
    }
}
