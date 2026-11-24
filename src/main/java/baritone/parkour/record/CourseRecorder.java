package baritone.parkour.record;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code #parkour record}: copies a parkour course out of the world the player is in (a server lobby, say) while the
 * player walks or runs it. Every half second the blocks round the player are added, so the copy covers the whole
 * course however far it goes; the blocks the player stands on are kept in order as the course's route. Saved as a
 * {@link CourseRecording} under {@code dih/parkour-courses}.
 */
public final class CourseRecorder {
    /** How far round the player blocks are copied, sideways and up/down. */
    static final int RADIUS = 24;
    static final int VERTICAL = 16;
    private static final int EVERY_TICKS = 10;

    private static boolean recording;
    private static final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private static final List<BlockPos> route = new ArrayList<>();
    private static BlockPos start;
    private static float startYaw;
    private static int ticks;

    private CourseRecorder() {
    }

    public static boolean recording() {
        return recording;
    }

    public static String start(LocalPlayer player, Level level) {
        blocks.clear();
        route.clear();
        start = player.blockPosition();
        startYaw = player.getYRot();
        ticks = 0;
        recording = true;
        capture(player, level);
        return "Recording the course from " + start.toShortString() + ". Run it to the end (the blocks round you are copied as you go), then #parkour record save <name>.";
    }

    /** Called every client tick. */
    public static void tick(LocalPlayer player, Level level) {
        if (!recording || player == null || level == null) return;
        if (player.onGround()) {
            BlockPos on = player.getOnPos();
            if (route.isEmpty() || !route.getLast().equals(on)) route.add(on.immutable());
        }
        if (++ticks % EVERY_TICKS == 0) capture(player, level);
    }

    private static void capture(LocalPlayer player, Level level) {
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
            for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = center.getY() - VERTICAL; y <= center.getY() + VERTICAL; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) blocks.put(pos.immutable(), state);
                }
            }
        }
    }

    public static String status() {
        if (!recording) return "Not recording a course.";
        return String.format(Locale.ROOT, "Recording: %d blocks copied, %d steps of route.", blocks.size(), route.size());
    }

    public static String stop() {
        recording = false;
        blocks.clear();
        route.clear();
        return "Stopped recording; nothing saved.";
    }

    /** The recording so far (absolute positions). */
    public static CourseRecording snapshot() {
        return new CourseRecording(new HashMap<>(blocks), start, startYaw, List.copyOf(route));
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("dih").resolve("parkour-courses");
    }

    public static String save(String name) throws IOException {
        if (!recording) throw new IllegalStateException("Not recording; start with #parkour record start.");
        String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        Path file = folder().resolve(safe + ".nbt");
        CourseRecording recordingNow = snapshot();
        recordingNow.save(file);
        recording = false;
        String line = String.format(Locale.ROOT, "Saved %s: %d blocks, %d steps of route, %d pressure plates.", file,
            recordingNow.blocks.size(), recordingNow.route.size(), recordingNow.plates().size());
        blocks.clear();
        route.clear();
        return line;
    }
}
