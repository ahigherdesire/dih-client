package baritone.parkour;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.parkour.record.CourseRecorder;
import baritone.parkour.record.CourseRecording;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Courses copied out of real worlds with {@code #parkour record} (a server lobby's parkour, say), rebuilt in a
 * singleplayer world and run with {@code #parkour}. Each run is scored against the route the recording player took:
 * whether it got to the route's end, how long it took, how often it fell, and how much of the route it went over.
 *
 * <p>First a self-check of the whole pipeline: the tier-2 course is run while recording, saved, loaded back, rebuilt
 * somewhere else and run again; that has to finish. Then every recording in the repository's {@code run/parkour-courses}
 * (and in {@code DIH_PARKOUR_COURSES}) is run and reported.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourRecordedCourseGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourRecordedCourseGameTest] ";
    private static final int RUNS = Integer.getInteger("dih.parkourRecordedRuns", 3);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/gamerule advance_time false");
            world.getServer().runCommand("/gamerule fall_damage false");
            world.getServer().runCommand("/effect give @a minecraft:saturation infinite 1 true");

            // ---- self-check: record a run, save, load, rebuild elsewhere, run again
            ParkourCourseGameTest.Course tier2 = ParkourCourseGameTest.courses().get(1);
            BlockPos origin = new BlockPos(12000, 140, 0);
            world.getServer().runCommand("/forceload add " + (origin.getX() - 32) + " -16 " + (origin.getX() + 32) + " 96");
            ParkourCourseGameTest.build(world, tier2, origin);
            world.getServer().runCommand("/tp @a " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() - 1.5) + " 0 0");
            context.waitTicks(5);
            context.waitFor(client -> client.player.onGround(), 60);
            context.runOnClient(client -> CourseRecorder.start(client.player, client.level));
            context.runOnClient(client -> process().follow());
            context.waitFor(client -> !process().running(), 20 * 180);
            Path saved = context.computeOnClient(client -> {
                try {
                    CourseRecorder.save("selfcheck-tier2");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return CourseRecorder.folder().resolve("selfcheck-tier2.nbt");
            });
            CourseRecording loaded;
            try {
                loaded = CourseRecording.load(saved);
            } catch (IOException e) {
                throw new AssertionError("couldn't load the self-check recording back: " + e);
            }
            String self = runAll(context, world, "selfcheck-tier2", loaded, new BlockPos(14000, 120, 0));
            System.out.println(TAG + self);
            if (!self.contains(" " + RUNS + "/" + RUNS + " reached the end")) failures.add(self);

            // ---- every recording found
            for (Path file : recordings(context)) {
                if (file.getFileName().toString().startsWith("selfcheck-")) continue;
                try {
                    CourseRecording course = CourseRecording.load(file);
                    System.out.println(TAG + runAll(context, world, file.getFileName().toString(), course, new BlockPos(16000, 100, 0)));
                } catch (IOException | RuntimeException e) {
                    System.out.println(TAG + file.getFileName() + ": couldn't run: " + e);
                }
            }
        }
        context.setScreen(TitleScreen::new);
        if (!failures.isEmpty()) throw new AssertionError("recorded courses failed:\n  " + String.join("\n  ", failures));
    }

    private static List<Path> recordings(ClientGameTestContext context) {
        Set<Path> folders = new LinkedHashSet<>();
        folders.add(context.computeOnClient(client -> CourseRecorder.folder()));
        String extra = System.getenv("DIH_PARKOUR_COURSES");
        if (extra != null && !extra.isBlank()) folders.add(Path.of(extra));
        // The game test wipes its run folder on start, so recordings to keep go in the repository's run/parkour-courses.
        Path repo = context.computeOnClient(client -> ParkourMapGameTest.repoRoot(client.gameDirectory.toPath()));
        if (repo != null) folders.add(repo.resolve("run").resolve("parkour-courses"));
        List<Path> out = new ArrayList<>();
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(f -> f.toString().endsWith(".nbt")).sorted().forEach(out::add);
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    /** Builds {@code course} at {@code origin} and runs it {@link #RUNS} times; one line of results. */
    private static String runAll(ClientGameTestContext context, TestSingleplayerContext world, String name, CourseRecording course, BlockPos origin) {
        place(world, course, origin);
        List<BlockPos> route = new ArrayList<>(new LinkedHashSet<>(course.route));
        BlockPos end = route.isEmpty() ? null : route.getLast().offset(origin);
        int reached = 0;
        List<String> runs = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            String result = once(context, world, course, origin, end, route);
            if (result.startsWith("reached")) reached++;
            runs.add(result);
        }
        return String.format(Locale.ROOT, "%s (%d blocks, %d route steps, %d plates): %d/%d reached the end; %s",
            name, course.blocks.size(), route.size(), course.plates().size(), reached, RUNS, String.join("; ", runs));
    }

    private static void place(TestSingleplayerContext world, CourseRecording course, BlockPos origin) {
        int maxX = 0, maxZ = 0;
        for (BlockPos p : course.blocks.keySet()) {
            maxX = Math.max(maxX, p.getX());
            maxZ = Math.max(maxZ, p.getZ());
        }
        // Keep the area loaded, in squares /forceload accepts.
        for (int x = 0; x <= maxX + 16; x += 192) {
            for (int z = 0; z <= maxZ + 16; z += 192) {
                world.getServer().runCommand("/forceload add " + (origin.getX() + x - 16) + " " + (origin.getZ() + z - 16) + " "
                    + (origin.getX() + Math.min(x + 192, maxX + 16)) + " " + (origin.getZ() + Math.min(z + 192, maxZ + 16)));
            }
        }
        world.getServer().runOnServer(server -> {
            var level = server.getLevel(Level.OVERWORLD);
            // UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE: as recorded, with no neighbour updates reshaping fences or panes.
            for (Map.Entry<BlockPos, BlockState> e : course.blocks.entrySet()) level.setBlock(e.getKey().offset(origin), e.getValue(), 2 | 16);
        });
    }

    /** One run from the recording's start: "reached ..." or why not. */
    private static String once(ClientGameTestContext context, TestSingleplayerContext world, CourseRecording course, BlockPos origin,
                               BlockPos end, List<BlockPos> route) {
        BlockPos start = course.start.offset(origin);
        world.getServer().runCommand(String.format(Locale.ROOT, "/tp @a %.1f %d %.1f %.1f 0", start.getX() + 0.5, start.getY(), start.getZ() + 0.5, course.startYaw));
        context.waitTicks(5);
        context.waitFor(client -> client.player.onGround(), 100);
        context.waitTicks(5);
        int lowest = route.stream().mapToInt(BlockPos::getY).min().orElse(0) + origin.getY();
        long t0 = System.currentTimeMillis();
        context.runOnClient(client -> CourseRecorder.start(client.player, client.level));
        context.runOnClient(client -> process().follow());
        try {
            context.waitFor(client -> !process().running() || client.player.getY() < lowest - 8, 20 * 300);
        } catch (RuntimeException | AssertionError e) {
            context.runOnClient(client -> process().stop());
        }
        double seconds = (System.currentTimeMillis() - t0) / 1000.0;
        return context.computeOnClient(client -> {
            ParkourProcess p = process();
            if (p.running()) p.stop();
            List<BlockPos> went = CourseRecorder.snapshot().route;
            CourseRecorder.stop();
            Set<BlockPos> stood = new HashSet<>(went);
            long covered = route.stream().filter(r -> stood.contains(r.offset(origin))).count();
            String along = String.format(Locale.ROOT, "%.0f%% of the route", 100.0 * covered / Math.max(1, route.size()));
            if (end != null && stood.contains(end)) {
                return String.format(Locale.ROOT, "reached %.1fs %d falls %s", seconds, p.falls(), along);
            }
            BlockPos on = client.player.getOnPos().subtract(origin);
            return String.format(Locale.ROOT, "stopped at %s after %.1fs, %d jumps, %d falls, %s (%s)", on.toShortString(), seconds,
                p.jumps(), p.falls(), along, p.status());
        });
    }

    private static ParkourProcess process() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getParkourProcess();
    }
}
