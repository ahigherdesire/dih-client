package dihclient.modules;

import dihclient.util.DihConfig;
import dihclient.util.DihInventoryHelper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;

/**
 * PearlCharge against a real integrated server: armed with an ender pearl and a wind charge on the hotbar,
 * one activation throws both projectiles and the module turns itself back off.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PearlChargeGameTest implements FabricClientGameTest {
    private static final String TAG = "[PearlChargeGameTest] ";

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

    /** With an ender pearl and a wind charge in hand, one arming throws both and the module disables itself. */
    private static void pearlCharge(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode survival @a");
        world.getServer().runCommand("/clear @a");
        world.getServer().runCommand("/setblock 0 -61 0 minecraft:stone");
        world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:ender_pearl 16");
        world.getServer().runCommand("/item replace entity @a hotbar.1 with minecraft:wind_charge 16");
        world.getServer().runCommand("/kill @e[type=minecraft:ender_pearl]");
        world.getServer().runCommand("/kill @e[type=minecraft:wind_charge]");
        world.getServer().runCommand("/tp @a 0.5 -60 0.5 -90 -90");
        context.waitTicks(20);
        context.runOnClient(client -> {
            DihInventoryHelper.selectHotbarSlot(client, 0);
            Module pearl = ModuleRegistry.get("pearl-charge");
            pearl.setValue("order", "Pearl First");
            pearl.setValue("delay", "1");
            pearl.setEnabled(true);
        });

        boolean pearlThrown = false;
        boolean chargeThrown = false;
        for (int i = 0; i < 40 && !(pearlThrown && chargeThrown); i++) {
            context.waitTicks(1);
            pearlThrown |= hasEntity(world, EntityTypes.ENDER_PEARL);
            chargeThrown |= hasEntity(world, EntityTypes.WIND_CHARGE);
        }
        System.out.println(TAG + "pearl thrown=" + pearlThrown + " charge thrown=" + chargeThrown);
        if (!pearlThrown) throw new AssertionError("PearlCharge never threw the ender pearl");
        if (!chargeThrown) throw new AssertionError("PearlCharge never popped the wind charge");

        context.waitTicks(5);
        boolean stillOn = context.computeOnClient(client -> ModuleRegistry.get("pearl-charge").isEnabled());
        if (stillOn) throw new AssertionError("PearlCharge should turn itself off after launching");
        world.getServer().runCommand("/kill @e[type=minecraft:ender_pearl]");
        world.getServer().runCommand("/kill @e[type=minecraft:wind_charge]");
    }

    private static boolean hasEntity(TestSingleplayerContext world, EntityType<?> type) {
        return world.getServer().computeOnServer(server ->
            !server.getLevel(Level.OVERWORLD).getEntities(type, entity -> true).isEmpty());
    }
}
