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

/**
 * Part 3 of the parkour work: every single jump the solver unit tests cover, built in a real world and made with
 * {@code #parkour goto} from a standstill, {@link #RUNS} runs each. Every run must land on the target; the largest
 * drift between the simulator's prediction and the real client is logged per jump.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourJumpGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourJumpGameTest] ";
    private static final int RUNS = Integer.getInteger("dih.parkourRuns", 10);
    private static final int BASE_Y = 120;

    /** A jump: commands relative to its origin (the start block is at ~ ~-1 ~), and the target block relative to it. */
    record Jump(String name, List<String> build, int tx, int ty, int tz) {
    }

    static List<Jump> jumps() {
        String start = "fill ~-2 ~-1 ~-8 ~2 ~-1 ~0 minecraft:stone";
        List<Jump> out = new ArrayList<>();
        for (int gap = 1; gap <= 4; gap++) {
            out.add(new Jump("flat-" + gap, List.of(start, "fill ~-2 ~-1 ~" + (1 + gap) + " ~2 ~-1 ~" + (3 + gap) + " minecraft:stone"), 0, -1, 1 + gap));
        }
        out.add(new Jump("up-1-gap-2", List.of(start, "fill ~-2 ~ ~3 ~2 ~ ~5 minecraft:stone"), 0, 0, 3));
        out.add(new Jump("down-2-gap-4", List.of(start, "fill ~-2 ~-3 ~5 ~2 ~-3 ~7 minecraft:stone"), 0, -3, 5));
        out.add(new Jump("head-hitter-1", List.of(start, "fill ~-2 ~2 ~-8 ~2 ~2 ~6 minecraft:stone", "fill ~-2 ~-1 ~2 ~2 ~-1 ~4 minecraft:stone"), 0, -1, 2));
        out.add(new Jump("fence-post", List.of(start, "setblock ~ ~-1 ~2 minecraft:oak_fence"), 0, -1, 2));
        return out;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/gamerule doDaylightCycle false");
            world.getServer().runCommand("/effect give @a minecraft:saturation infinite 1 true");
            world.getServer().runCommand("/effect give @a minecraft:resistance infinite 4 true");
            List<Jump> jumps = jumps();
            for (int i = 0; i < jumps.size(); i++) {
                Jump jump = jumps.get(i);
                BlockPos origin = new BlockPos(4000 + i * 48, BASE_Y, 0);
                world.getServer().runCommand("/forceload add " + (origin.getX() - 24) + " -24 " + (origin.getX() + 24) + " 24");
                for (String command : jump.build()) {
                    world.getServer().runCommand("/execute positioned " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " run " + command);
                }
                BlockPos target = origin.offset(jump.tx(), jump.ty(), jump.tz());
                int landed = 0;
                double drift = 0.0;
                List<String> misses = new ArrayList<>();
                for (int run = 0; run < RUNS; run++) {
                    String result = once(context, world, origin, target);
                    if (result.startsWith("landed")) {
                        landed++;
                        drift = Math.max(drift, Double.parseDouble(result.substring(result.indexOf(' ') + 1)));
                    } else {
                        misses.add(result);
                    }
                }
                String line = String.format(java.util.Locale.ROOT, "%s: %d/%d landed, max drift %.2e%s", jump.name(), landed, RUNS, drift,
                    misses.isEmpty() ? "" : ", misses " + misses);
                System.out.println(TAG + line);
                if (landed < RUNS) failures.add(line);
            }
        }
        context.setScreen(TitleScreen::new);
        if (!failures.isEmpty()) throw new AssertionError("jumps missed:\n  " + String.join("\n  ", failures));
    }

    /** One run: stand on the start, #parkour goto the target, wait; "landed <drift>" or why not. */
    private static String once(ClientGameTestContext context, TestSingleplayerContext world, BlockPos origin, BlockPos target) {
        world.getServer().runCommand("/tp @a " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() - 3.5) + " 0 0");
        context.waitTicks(5);
        context.waitFor(client -> client.player.onGround(), 60);
        context.waitTicks(5);
        context.runOnClient(client -> process().goTo(target));
        try {
            context.waitFor(client -> !process().running(), 20 * 20);
        } catch (RuntimeException | AssertionError e) {
            context.runOnClient(client -> process().stop());
            return "timed out";
        }
        return context.computeOnClient(client -> {
            ParkourProcess p = process();
            boolean onTarget = client.player.onGround() && client.player.getOnPos().equals(target)
                || client.player.onGround() && Math.abs(client.player.getY() - (target.getY() + top(client, target))) < 1e-6
                && Math.abs(client.player.getX() - (target.getX() + 0.5)) < 0.8 && Math.abs(client.player.getZ() - (target.getZ() + 0.5)) < 0.8;
            if (onTarget && p.falls() == 0) return "landed " + p.maxDrift();
            return "missed (" + p.status() + ", at " + client.player.blockPosition().toShortString() + ")";
        });
    }

    private static double top(net.minecraft.client.Minecraft client, BlockPos pos) {
        var shape = client.level.getBlockState(pos).getCollisionShape(client.level, pos);
        return shape.isEmpty() ? 0.0 : shape.max(net.minecraft.core.Direction.Axis.Y);
    }

    private static ParkourProcess process() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getParkourProcess();
    }
}
