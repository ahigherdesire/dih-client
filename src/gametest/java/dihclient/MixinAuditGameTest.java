package dihclient;

import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Loads every mixin target and applies every mixin. Mixins otherwise apply only when their target class first
 * loads, so a broken injection in a class the other tests never touch would only surface in a player's game.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MixinAuditGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            if (config != null) config.customMainMenu = false; // Fabric's runner requires the vanilla title screen.
            MixinEnvironment.getCurrentEnvironment().audit();
        });
        // DIH replaces the title screen; Fabric's runner checks for the vanilla screen after each test.
        context.setScreen(TitleScreen::new);
    }
}
