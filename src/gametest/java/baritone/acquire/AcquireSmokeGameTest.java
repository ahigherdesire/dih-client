package baritone.acquire;

import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

/** Checks that the real client can plan with inventory granted by an integrated server. */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireSmokeGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            if (config == null) throw new AssertionError("DIH config did not load");
            config.customMainMenu = false; // Fabric's runner requires the vanilla title screen after the test.
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/give @p minecraft:stone_pickaxe 1");
            context.waitFor(client -> client.player != null
                    && client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(net.minecraft.world.item.Items.STONE_PICKAXE)));
            context.runOnClient(client -> {
                AcquireControl acquire = AcquireControl.get();
                if (acquire == null) throw new AssertionError("Acquire control did not register");
                String plan = acquire.plan("stone_pickaxe", 1);
                if (!plan.toLowerCase(java.util.Locale.ROOT).contains("already have 1 stone_pickaxe")) {
                    throw new AssertionError("Expected server-granted inventory to count; got: " + plan);
                }
            });
        }
        // DIH replaces the title screen; Fabric's runner checks for the vanilla screen after each test.
        context.setScreen(TitleScreen::new);
    }
}
