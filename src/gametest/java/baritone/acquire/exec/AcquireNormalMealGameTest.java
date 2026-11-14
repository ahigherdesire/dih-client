package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Items;

/** An acquire eats ordinary food while hurt and hungry, then keeps its item goal active. */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireNormalMealGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            if (config == null) throw new AssertionError("DIH config did not load");
            config.customMainMenu = false;
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/give @p minecraft:cooked_beef 1");
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().forEach(player -> {
                player.setHealth(12.0F);
                player.getFoodData().setFoodLevel(10);
            }));
            context.waitFor(client -> client.player != null && client.player.getHealth() <= 12.0F
                    && client.player.getFoodData().getFoodLevel() <= 10
                    && client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(Items.COOKED_BEEF)));
            try {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire == null) throw new AssertionError("Acquire control did not register");
                    acquire.start("dirt", 64);
                });
                context.waitFor(client -> client.player != null
                        && client.player.getFoodData().getFoodLevel() >= 18
                        && client.player.getInventory().getNonEquipmentItems().stream()
                        .noneMatch(stack -> stack.is(Items.COOKED_BEEF)));
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire == null || !acquire.isActive()) {
                        throw new AssertionError("Acquire did not resume after eating");
                    }
                });
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }
}
