package dihclient.modules;

import dihclient.util.DihConfig;
import dihclient.util.DihInventoryHelper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;

/**
 * PearlCharge against a real integrated server: armed with an ender pearl and a wind charge on the hotbar, one
 * activation lifts the player, the burst boosts the pearl past its normal throw speed of 1.5, and the module turns
 * itself back off. In the air it must refuse and keep both items.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PearlChargeGameTest implements FabricClientGameTest {
    private static final String TAG = "[PearlChargeGameTest] ";
    private static final int LAUNCHES = 6;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/time set day");
            context.waitTicks(40);
            try {
                pearlCharge(context, world);
                System.out.println(TAG + "pearl-charge ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> ModuleRegistry.get("pearl-charge").setEnabled(false));
            }
        }
        context.setScreen(TitleScreen::new);
    }

    /** Every launch hops the player, speeds the pearl up, and the module disables itself; in the air it does nothing. */
    private static void pearlCharge(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode survival @a");
        world.getServer().runCommand("/clear @a");
        world.getServer().runCommand("/setblock 0 -61 0 minecraft:stone");
        world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:ender_pearl 16");
        world.getServer().runCommand("/item replace entity @a hotbar.1 with minecraft:wind_charge 16");

        for (int launch = 0; launch < LAUNCHES; launch++) {
            world.getServer().runCommand("/kill @e[type=minecraft:ender_pearl]");
            world.getServer().runCommand("/kill @e[type=minecraft:wind_charge]");
            world.getServer().runCommand("/tp @a 0.5 -60 0.5 -90 -90");
            context.waitFor(client -> client.player.onGround(), 60);
            context.waitTicks(30);
            context.runOnClient(client -> {
                DihInventoryHelper.selectHotbarSlot(client, 0);
                ModuleRegistry.get("pearl-charge").setEnabled(true);
            });

            double startY = context.computeOnClient(client -> client.player.getY());
            double peakY = startY;
            double pearlSpeed = 0.0D;
            for (int i = 0; i < 30; i++) {
                context.waitTicks(1);
                peakY = Math.max(peakY, context.computeOnClient(client -> client.player.getY()));
                pearlSpeed = Math.max(pearlSpeed, pearlSpeed(world));
            }
            System.out.println(TAG + "launch " + launch + " rise=" + (peakY - startY) + " pearl speed=" + pearlSpeed);
            if (peakY - startY < 5.0D) throw new AssertionError("PearlCharge should lift the player, rose " + (peakY - startY));
            if (pearlSpeed < 1.8D) throw new AssertionError("the burst should boost the pearl, speed " + pearlSpeed);
            boolean stillOn = context.computeOnClient(client -> ModuleRegistry.get("pearl-charge").isEnabled());
            if (stillOn) throw new AssertionError("PearlCharge should turn itself off after launching");
        }
        world.getServer().runCommand("/kill @e[type=minecraft:ender_pearl]");
        world.getServer().runCommand("/kill @e[type=minecraft:wind_charge]");

        context.waitFor(client -> client.player.onGround(), 60);
        context.waitTicks(30);
        world.getServer().runCommand("/tp @a 0.5 -40 0.5 -90 -90");
        context.waitTicks(2);
        int before = context.computeOnClient(client -> client.player.getInventory().getItem(1).getCount());
        context.runOnClient(client -> ModuleRegistry.get("pearl-charge").setEnabled(true));
        context.waitTicks(3);
        int after = context.computeOnClient(client -> client.player.getInventory().getItem(1).getCount());
        if (after != before) throw new AssertionError("PearlCharge must not launch in the air");
        System.out.println(TAG + "refused in the air");
    }

    private static double pearlSpeed(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var pearls = server.getLevel(Level.OVERWORLD).getEntities(EntityTypes.ENDER_PEARL, entity -> true);
            return pearls.isEmpty() ? 0.0D : pearls.get(0).getDeltaMovement().length();
        });
    }
}
