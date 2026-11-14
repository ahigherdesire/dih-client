package baritone.construct;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.schematic.ISchematic;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code #construct} end to end: from an empty inventory in a superflat world with a stone pad and a few trees put
 * down by the test, it gathers and builds a 3x3x3 hut (a cobblestone floor and two rings of oak plank walls): logs to
 * planks and a crafting table, a wooden pickaxe, cobblestone, then the build. Every block must end up in place.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ConstructGameTest implements FabricClientGameTest {
    private static final String TAG = "[ConstructGameTest] ";
    private static final int TIMEOUT_TICKS = 8 * 60 * 20;

    /** The hut: cobblestone floor, oak plank walls two high, air inside. */
    static final ISchematic HUT = new ISchematic() {
        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            if (y == 0) return Blocks.COBBLESTONE.defaultBlockState();
            if (x == 1 && z == 1) return Blocks.AIR.defaultBlockState();
            return Blocks.OAK_PLANKS.defaultBlockState();
        }

        @Override
        public int widthX() {
            return 3;
        }

        @Override
        public int heightY() {
            return 3;
        }

        @Override
        public int lengthZ() {
            return 3;
        }
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> log = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/time set day");
            world.getServer().runCommand("/gamerule doDaylightCycle false");
            world.getServer().runCommand("/clear @p");
            world.getServer().runCommand("/execute at @p run fill ~5 ~ ~-3 ~9 ~2 ~1 minecraft:stone");
            world.getServer().runCommand("/execute at @p run fill ~-5 ~ ~-1 ~-5 ~3 ~-1 minecraft:oak_log");
            world.getServer().runCommand("/execute at @p run fill ~-5 ~ ~-4 ~-5 ~3 ~-4 minecraft:oak_log");
            context.waitTicks(20);
            BlockPos origin = context.computeOnClient(client -> client.player.blockPosition().offset(-1, 0, 5));
            boolean allowInventoryBefore = Baritone.settings().allowInventory.value;
            try {
                context.runOnClient(client -> {
                    ConstructJob job = ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getConstructJob();
                    log.add("plan: " + job.plan("hut", HUT, origin));
                    log.add("start: " + job.start("hut", HUT, origin));
                });
                context.waitFor(client -> !job().isActive(), TIMEOUT_TICKS);
            } catch (RuntimeException | AssertionError e) {
                log.add("wait ended with: " + e);
            } finally {
                context.runOnClient(client -> {
                    log.add("status: " + job().status());
                    if (job().isActive()) job().stop();
                });
                context.takeScreenshot("construct-hut");
                System.out.println(TAG + "\n  " + String.join("\n  ", log));
            }
            String wrong = world.getServer().computeOnServer(server -> {
                Level level = server.getLevel(Level.OVERWORLD);
                List<String> off = new ArrayList<>();
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        for (int z = 0; z < 3; z++) {
                            BlockState want = HUT.desiredState(x, y, z, null, List.of());
                            BlockState have = level.getBlockState(origin.offset(x, y, z));
                            if (have.getBlock() != want.getBlock()) off.add(x + "," + y + "," + z + "=" + have.getBlock());
                        }
                    }
                }
                return String.join(" ", off);
            });
            if (!wrong.isEmpty()) throw new AssertionError("the hut isn't built: " + wrong + "\n  " + String.join("\n  ", log));
            if (Baritone.settings().allowInventory.value != allowInventoryBefore) {
                throw new AssertionError("#construct must put allowInventory back when it ends");
            }
            String status = context.computeOnClient(client -> job().status());
            if (!status.startsWith("Done")) throw new AssertionError("the job should end with Done, not: " + status);
        }
        context.setScreen(TitleScreen::new);
    }

    private static ConstructJob job() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getConstructJob();
    }
}
