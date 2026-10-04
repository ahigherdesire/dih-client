package baritone.parkour;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Part 4 of the parkour work: whole courses run with {@code #parkour} (follow mode, no goal) from the start platform
 * to a gold finish block. Each course is run {@link #RUNS} times; a run passes when it ends standing on the finish.
 * Times and falls are logged per run. {@code -Ddih.parkourTiers=1,2,3} picks the tiers.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourCourseGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourCourseGameTest] ";
    private static final int RUNS = Integer.getInteger("dih.parkourRuns", 10);
    private static final int BASE_Y = 140;

    /**
     * One jump of a course: where the next landing block is relative to the last one, and what it is, plus any other
     * blocks the jump needs ("dx dy dz block", relative to the landing block, placed after it in order): a run-up
     * strip, a wall to go round, a ladder to climb.
     */
    record Hop(int dx, int dy, int dz, String block, List<String> extras) {
        Hop(int dx, int dy, int dz, String block) {
            this(dx, dy, dz, block, List.of());
        }

        static Hop of(int dx, int dy, int dz) {
            return new Hop(dx, dy, dz, "minecraft:stone");
        }

        /** A five-block run-up strip starting at the landing, going on along +z. */
        static Hop strip(int dx, int dy, int dz) {
            return new Hop(dx, dy, dz, "minecraft:stone", List.of("0 0 1 minecraft:stone", "0 0 2 minecraft:stone",
                "0 0 3 minecraft:stone", "0 0 4 minecraft:stone"));
        }

        /** A classic neo off the end of a {@link #strip}: a one-wide wall four high in the way, the landing just behind it. */
        static Hop neo() {
            return new Hop(0, 0, 6, "minecraft:stone", List.of("0 0 -1 minecraft:stone", "0 1 -1 minecraft:stone",
                "0 2 -1 minecraft:stone", "0 3 -1 minecraft:stone"));
        }

        /** A ladder jump: across a one-block gap onto a ladder up a wall's near face, landing on the wall's top. */
        static Hop ladder() {
            return new Hop(0, 3, 3, "minecraft:stone", List.of("0 -1 0 minecraft:stone", "0 -2 0 minecraft:stone",
                "0 -3 0 minecraft:stone", "0 -2 -1 minecraft:ladder[facing=north]", "0 -1 -1 minecraft:ladder[facing=north]",
                "0 0 -1 minecraft:ladder[facing=north]"));
        }
    }

    record Course(String name, int tier, List<Hop> hops, int required) {
    }

    static List<Course> courses() {
        List<Course> out = new ArrayList<>();
        out.add(new Course("tier-1 basics", 1, List.of(
            Hop.of(0, 0, 2), Hop.of(0, 0, 3), Hop.of(0, 1, 2), Hop.of(0, 0, 3), Hop.of(0, -1, 3),
            new Hop(0, 0, 2, "minecraft:stone_slab"), Hop.of(2, 0, 1), Hop.of(0, 0, 3), Hop.of(0, 1, 3), Hop.of(0, -2, 3),
            Hop.of(2, 0, 2), new Hop(0, 0, 2, "minecraft:stone_slab"), Hop.of(0, 0, 2), Hop.of(-2, 0, 2),
            new Hop(0, 0, 3, "minecraft:gold_block")), 10));
        out.add(new Course("tier-2 medium", 2, List.of(
            Hop.of(0, 0, 3), new Hop(0, 0, 2, "minecraft:oak_fence"), Hop.of(0, 0, 3), new Hop(0, 0, 2, "minecraft:glass_pane"),
            Hop.of(0, 0, 3), new Hop(0, 0, 3, "minecraft:packed_ice"), Hop.of(0, 0, 4), Hop.of(1, 1, 2), Hop.of(0, 0, 3),
            Hop.of(0, -1, 4), new Hop(0, 0, 2, "minecraft:oak_fence"), Hop.of(-1, 0, 3), Hop.of(0, 1, 2), Hop.of(0, 0, 3),
            Hop.strip(0, 0, 2), Hop.neo(), Hop.of(0, 0, 2), Hop.ladder(), Hop.of(0, 0, 3),
            new Hop(0, 0, 3, "minecraft:gold_block")), 10));
        out.add(new Course("tier-3 hard", 3, List.of(
            Hop.of(0, 0, 4), Hop.of(0, 1, 3), Hop.of(0, 0, 4), new Hop(0, 0, 3, "minecraft:oak_fence"), Hop.of(0, -2, 4),
            Hop.of(3, 0, 3), Hop.of(0, 1, 3), new Hop(0, 0, 3, "minecraft:glass_pane"), Hop.of(0, 0, 4), Hop.of(-3, 0, 3),
            Hop.of(0, 1, 3), new Hop(0, 0, 4, "minecraft:gold_block")), 9));
        return out;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String tiers = System.getProperty("dih.parkourTiers", "1,2,3");
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/gamerule advance_time false");
            world.getServer().runCommand("/gamerule fall_damage false");
            world.getServer().runCommand("/effect give @a minecraft:saturation infinite 1 true");
            List<Course> courses = courses();
            for (int i = 0; i < courses.size(); i++) {
                Course course = courses.get(i);
                if (!tiers.contains(Integer.toString(course.tier()))) continue;
                BlockPos origin = new BlockPos(6000 + i * 64, BASE_Y, 0);
                BlockPos finish = build(world, course, origin);
                int passed = 0;
                List<String> runs = new ArrayList<>();
                for (int run = 0; run < RUNS; run++) {
                    String result = once(context, world, origin, finish);
                    runs.add(result);
                    if (result.startsWith("finished")) passed++;
                }
                String line = String.format(Locale.ROOT, "%s: %d/%d finished; %s", course.name(), passed, RUNS, String.join("; ", runs));
                System.out.println(TAG + line);
                if (passed < course.required()) failures.add(line);
            }
        }
        context.setScreen(TitleScreen::new);
        if (!failures.isEmpty()) throw new AssertionError("courses failed:\n  " + String.join("\n  ", failures));
    }

    /** Builds the course in the air from {@code origin} and returns the finish block. */
    private static BlockPos build(TestSingleplayerContext world, Course course, BlockPos origin) {
        world.getServer().runCommand("/forceload add " + (origin.getX() - 32) + " -16 " + (origin.getX() + 32) + " 96");
        world.getServer().runCommand("/fill " + (origin.getX() - 1) + " " + (origin.getY() - 1) + " " + (origin.getZ() - 4) + " "
            + (origin.getX() + 1) + " " + (origin.getY() - 1) + " " + origin.getZ() + " minecraft:stone");
        BlockPos at = origin.below();
        for (Hop hop : course.hops()) {
            at = at.offset(hop.dx(), hop.dy(), hop.dz());
            world.getServer().runCommand("/setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " " + hop.block());
            for (String extra : hop.extras()) {
                String[] part = extra.split(" ");
                BlockPos pos = at.offset(Integer.parseInt(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]));
                world.getServer().runCommand("/setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + part[3]);
            }
        }
        return at;
    }

    /** One run from the start platform: "finished <s> <falls>" or why not. */
    private static String once(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin, BlockPos finish) {
        world.getServer().runCommand("/tp @a " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() - 1.5) + " 0 0");
        context.waitTicks(5);
        context.waitFor(client -> client.player.onGround(), 60);
        context.waitTicks(5);
        long t0 = System.currentTimeMillis();
        context.runOnClient(client -> process().follow());
        try {
            context.waitFor(client -> !process().running() || client.player.getY() < origin.getY() - 12, 20 * 180);
        } catch (RuntimeException | AssertionError e) {
            context.runOnClient(client -> process().stop());
            return "timed out";
        }
        double seconds = (System.currentTimeMillis() - t0) / 1000.0;
        return context.computeOnClient(client -> {
            ParkourProcess p = process();
            if (p.running()) p.stop();
            BlockPos on = client.player.getOnPos();
            if (client.player.onGround() && on.equals(finish)) {
                return String.format(Locale.ROOT, "finished %.1fs %d falls", seconds, p.falls());
            }
            return "stopped at " + on.toShortString() + " after " + p.jumps() + " jumps (" + p.status() + ")";
        });
    }

    private static ParkourProcess process() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getParkourProcess();
    }
}
