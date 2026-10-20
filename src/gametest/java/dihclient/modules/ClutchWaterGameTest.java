package dihclient.modules;

import dihclient.util.DihConfig;
import dihclient.util.DihInventoryHelper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The Clutch water-bucket MLG against a real integrated server. Clutch set to Auto catches a 14-block fall
 * onto stone with a water bucket so the player takes no fall damage, then scoops the water back up.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClutchWaterGameTest implements FabricClientGameTest {
    private static final String TAG = "[ClutchWaterGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/time set day");
            context.waitTicks(40);
            try {
                waterClutch(context, world);
                System.out.println(TAG + "water-clutch ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> ModuleRegistry.get("clutch").setEnabled(false));
            }
        }
        context.setScreen(TitleScreen::new);
    }

    /** A 14-block fall onto stone: Clutch (Auto) must MLG water so no fall damage lands, then pick it back up. */
    private static void waterClutch(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode survival @a");
        world.getServer().runCommand("/clear @a");
        world.getServer().runCommand("/effect clear @a");
        world.getServer().runCommand("/fill 18 -60 -2 22 -60 2 minecraft:stone");
        world.getServer().runCommand("/fill 18 -59 -2 22 -45 2 minecraft:air");
        world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:water_bucket");
        world.getServer().runCommand("/tp @a 20.5 -59 0.5 -90 30");
        world.getServer().runCommand("/heal @a");
        context.waitTicks(10);
        context.runOnClient(client -> {
            DihInventoryHelper.selectHotbarSlot(client, 0);
            Module clutch = ModuleRegistry.get("clutch");
            clutch.setValue("mode", "Big Falls");
            clutch.setValue("fall-height", "6");
            clutch.setValue("clutch-with", "Auto");
            clutch.setValue("water-pickup", "true");
            clutch.setValue("switch-back", "true");
            clutch.setEnabled(true);
        });
        context.waitTicks(2);

        world.getServer().runCommand("/tp @a 20.5 -45 0.5 -90 30");
        boolean sawWater = false;
        boolean landed = false;
        for (int i = 0; i < 80 && !landed; i++) {
            context.waitTicks(1);
            sawWater |= waterInColumn(world);
            landed = context.computeOnClient(client -> client.player.onGround() && client.player.getY() > -60.0D);
        }
        context.waitTicks(30);

        float health = context.computeOnClient(client -> client.player.getHealth());
        double y = context.computeOnClient(client -> client.player.getY());
        boolean waterLeft = waterInColumn(world);
        int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        System.out.println(TAG + "water sawWater=" + sawWater + " health=" + health + " y=" + y
            + " waterLeft=" + waterLeft + " selected=" + selected);

        if (!sawWater) throw new AssertionError("Clutch never placed water on the way down");
        if (health < 20.0F) throw new AssertionError("the water MLG should spare all fall damage, health " + health);
        if (y < -60.0D) throw new AssertionError("Clutch let the player fall past the floor, y " + y);
        if (waterLeft) throw new AssertionError("Pick Up should have scooped the water back, but water remains");
        if (selected != 0) throw new AssertionError("Switch Back should return to slot 0, still on " + selected);
    }

    private static boolean waterInColumn(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            for (int y = -59; y <= -46; y++) {
                for (int x = 19; x <= 21; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (server.getLevel(Level.OVERWORLD).getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }
}
