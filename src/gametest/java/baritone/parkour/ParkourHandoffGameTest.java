package baritone.parkour;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;

/**
 * Part 5 of the parkour work: Baritone hands jumps it can't make to the solver. A gap whose straight crossing is
 * blocked by a wall, so the only way over is a diagonal jump round the wall's end: with {@code allowHardParkour} on,
 * {@code #goto} gets across; with it off, Baritone finds no path and the player stays put.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourHandoffGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourHandoffGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        boolean parkourBefore = Baritone.settings().allowParkour.value;
        boolean hardBefore = Baritone.settings().allowHardParkour.value;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/gamerule fall_damage false");
            BlockPos o = new BlockPos(8000, 150, 0);
            world.getServer().runCommand("/forceload add 7970 -30 8030 30");
            // start platform, the wall along its far edge, the landing across the gap
            fill(world, o.offset(-1, -1, -6), o.offset(2, -1, 0), "minecraft:stone");
            fill(world, o.offset(-4, -1, 1), o.offset(1, 2, 1), "minecraft:stone");
            fill(world, o.offset(-1, -1, 3), o.offset(1, -1, 5), "minecraft:stone");
            // a floor far below to catch a miss
            fill(world, o.offset(-10, -12, -10), o.offset(10, -12, 10), "minecraft:stone");
            BlockPos goal = o.offset(0, 0, 4);

            boolean withHard = attempt(context, world, o, goal, true);
            System.out.println(TAG + "with allowHardParkour: " + (withHard ? "crossed" : "did not cross"));
            boolean withoutHard = attempt(context, world, o, goal, false);
            System.out.println(TAG + "without allowHardParkour: " + (withoutHard ? "crossed" : "did not cross"));
            if (!withHard) throw new AssertionError("with allowHardParkour on, #goto should cross by a solved jump");
            if (withoutHard) throw new AssertionError("with allowHardParkour off, there should be no way across");
        } finally {
            Baritone.settings().allowParkour.value = parkourBefore;
            Baritone.settings().allowHardParkour.value = hardBefore;
        }
        context.setScreen(TitleScreen::new);
    }

    private static boolean attempt(ClientGameTestContext context, TestSingleplayerContext world, BlockPos o, BlockPos goal, boolean hard) {
        world.getServer().runCommand("/tp @a " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() - 4.5) + " 0 0");
        context.waitTicks(10);
        context.waitFor(client -> client.player.onGround(), 60);
        context.runOnClient(client -> {
            Baritone.settings().allowParkour.value = true;
            Baritone.settings().allowHardParkour.value = hard;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
        });
        try {
            context.waitFor(client -> client.player.onGround() && client.player.blockPosition().equals(goal)
                || client.player.getY() < o.getY() - 5, 20 * 25);
        } catch (RuntimeException | AssertionError e) {
            // timed out: did not get there
        }
        context.runOnClient(client -> System.out.println(TAG + (hard ? "hard" : "plain") + " attempt ended at " + client.player.position()));
        boolean there = context.computeOnClient(client -> client.player.getY() >= o.getY() - 0.5
            && client.player.getZ() > o.getZ() + 2.5 && client.player.onGround());
        context.runOnClient(client -> BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything());
        context.waitTicks(5);
        return there;
    }

    private static void fill(TestSingleplayerContext world, BlockPos a, BlockPos b, String block) {
        world.getServer().runCommand("/fill " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + block);
    }
}
