package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Location;
import baritone.ai.AiBrain;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The speedrunner's bucket portal: with no obsidian and no diamond pickaxe, only two buckets, cobblestone for the
 * mould and a flint and steel, {@code #acquire} fills a bucket at the water pool, then casts the frame one lava bucket
 * at a time next to the lava pool, lights it and goes through; then it comes home through it. Checks the frame is all
 * obsidian, no obsidian was ever carried, and no water or lava is left loose on the pad.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PortalCastGameTest implements FabricClientGameTest {
    private static final String SEED = "8675309";
    private static final int TICKS = 20 * 420;
    private static final int PAD = 16;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(SEED);
                    state.setDifficulty(Difficulty.PEACEFUL);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            TestPads.load(context, world, Level.OVERWORLD, -PAD, -PAD, PAD, PAD);
            for (String command : List.of(
                    "/gamerule advance_time false",
                    "/time set 3000",
                    "/execute in minecraft:overworld run fill -" + PAD + " 98 -" + PAD + " " + PAD + " 99 " + PAD + " minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill -" + PAD + " 100 -" + PAD + " " + PAD + " 112 " + PAD + " minecraft:air",
                    "/execute in minecraft:overworld run fill 8 99 -1 11 99 1 minecraft:lava",
                    "/execute in minecraft:overworld run fill -10 99 -1 -9 99 0 minecraft:water",
                    "/tp @p 0 100 0 -90 0",
                    "/clear @p",
                    "/item replace entity @p armor.head with minecraft:iron_helmet",
                    "/item replace entity @p armor.chest with minecraft:iron_chestplate",
                    "/item replace entity @p armor.legs with minecraft:iron_leggings",
                    "/item replace entity @p armor.feet with minecraft:iron_boots",
                    "/item replace entity @p weapon.offhand with minecraft:shield",
                    "/give @p minecraft:iron_sword",
                    "/give @p minecraft:iron_pickaxe",
                    "/give @p minecraft:bucket 2",
                    "/give @p minecraft:cobblestone 24",
                    "/give @p minecraft:flint_and_steel",
                    "/give @p minecraft:cooked_beef 16")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(net.minecraft.world.item.Items.COOKED_BEEF)));
            context.waitTicks(40);
            TestPads.expect(world, Level.OVERWORLD, new BlockPos(PAD, 98, PAD), Blocks.SMOOTH_STONE);
            TestPads.expect(world, Level.OVERWORLD, new BlockPos(9, 99, 0), Blocks.LAVA);
            TestPads.expect(world, Level.OVERWORLD, new BlockPos(-10, 99, 0), Blocks.WATER);
            context.runOnClient(client -> PortalMemory.clear());
            // The caster's picks (lava, site, each state) go to chat, and so to the log.
            context.runOnClient(client -> baritone.api.BaritoneAPI.getSettings().chatDebug.value = true);
            List<String> events = new ArrayList<>();
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                String line = event.kind() == AcquireControl.AcquireEvent.Kind.STEP ? event.message() : event.kind() + " " + event.message();
                events.add(line);
                System.out.println("[PortalCastGameTest] " + line);
            }));

            long start = System.currentTimeMillis();
            PortalGameTest.trip(context, "the_nether", Location.NETHER, events, SEED, TICKS);
            double there = (System.currentTimeMillis() - start) / 1000.0;
            String worldKey = context.computeOnClient(client -> AiBrain.currentWorldKey());
            Map<String, BlockPos> portals = context.computeOnClient(client -> PortalMemory.all(worldKey));
            BlockPos cast = portals.get("overworld");
            if (cast == null || portals.get("the_nether") == null) PortalGameTest.fail(context, SEED, "portals remembered: " + portals, events);
            String frame = world.getServer().computeOnServer(server -> frameProblem(server.getLevel(Level.OVERWORLD), cast));
            if (frame != null) PortalGameTest.fail(context, SEED, frame, events);
            int obsidian = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:obsidian"));
            if (obsidian != 0) PortalGameTest.fail(context, SEED, obsidian + " obsidian carried: the frame was to be cast", events);
            context.takeScreenshot("portal-cast-nether");

            start = System.currentTimeMillis();
            PortalGameTest.trip(context, "overworld", Location.OVERWORLD, events, SEED, 20 * 180);
            double home = (System.currentTimeMillis() - start) / 1000.0;
            String loose = world.getServer().computeOnServer(server -> loose(server.getLevel(Level.OVERWORLD)));
            if (loose != null) PortalGameTest.fail(context, SEED, loose, events);
            context.runOnClient(client -> {
                client.player.setYRot(-90);
                client.player.setXRot(10);
            });
            context.waitTicks(10);
            context.takeScreenshot("portal-cast-home");
            System.out.printf("[PortalCastGameTest] cast, lit and entered in %.0f s, home in %.0f s, portal at %s%n",
                    there, home, cast.toShortString());
        }
        context.setScreen(TitleScreen::new);
    }

    /** Null when the lit portal at {@code inside} stands in a frame of obsidian all round; what's wrong otherwise. */
    private static String frameProblem(ServerLevel level, BlockPos inside) {
        if (!level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) return "no lit portal at the remembered " + inside.toShortString();
        BlockPos bottom = inside;
        while (level.getBlockState(bottom.below()).is(Blocks.NETHER_PORTAL)) bottom = bottom.below();
        Direction along = level.getBlockState(bottom.east()).is(Blocks.NETHER_PORTAL) || level.getBlockState(bottom.west()).is(Blocks.NETHER_PORTAL)
                ? Direction.EAST : Direction.SOUTH;
        BlockPos left = bottom;
        while (level.getBlockState(left.relative(along.getOpposite())).is(Blocks.NETHER_PORTAL)) left = left.relative(along.getOpposite());
        int portalBlocks = 0;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 3; j++) {
            if (level.getBlockState(left.relative(along, i).above(j)).is(Blocks.NETHER_PORTAL)) portalBlocks++;
        }
        if (portalBlocks != 6) return "the portal is " + portalBlocks + " blocks, not 2 by 3";
        List<BlockPos> frame = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            frame.add(left.relative(along, i).below());
            frame.add(left.relative(along, i).above(3));
        }
        for (int j = 0; j < 3; j++) {
            frame.add(left.relative(along.getOpposite()).above(j));
            frame.add(left.relative(along, 2).above(j));
        }
        for (BlockPos pos : frame) if (!level.getBlockState(pos).is(Blocks.OBSIDIAN)) return "frame block " + pos.toShortString() + " isn't obsidian";
        return null;
    }

    /** Water or lava anywhere on the pad but in its two pools, or null. */
    private static String loose(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(-PAD, 99, -PAD, PAD, 111, PAD)) {
            boolean pool = pos.getY() == 99 && (pos.getX() >= 8 && pos.getX() <= 11 && pos.getZ() >= -1 && pos.getZ() <= 1
                    || pos.getX() >= -10 && pos.getX() <= -9 && pos.getZ() >= -1 && pos.getZ() <= 0);
            if (!pool && !level.getFluidState(pos).isEmpty()) return "fluid left loose at " + pos.toShortString();
        }
        return null;
    }
}
