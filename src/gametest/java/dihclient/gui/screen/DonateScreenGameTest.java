package dihclient.gui.screen;

import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/** Automated client launches must reach the title screen without a donate card. */
@SuppressWarnings("UnstableApiUsage")
public final class DonateScreenGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig.getGlobal().customMainMenu = false;
            if (System.getProperty("fabric.client.gametest") == null)
                throw new AssertionError("the game-test marker is absent");
            try {
                var shown = DihDonateScreen.class.getDeclaredField("shownThisLaunch");
                shown.setAccessible(true);
                shown.setBoolean(null, false);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("could not reset launch state for the test", e);
            }
            TitleScreen title = new TitleScreen();
            if (DihDonateScreen.overTitle(title) != title)
                throw new AssertionError("the donate card intercepted an automated launch");
        });
        context.setScreen(TitleScreen::new);
        context.waitFor(client -> client.gui.screen() instanceof TitleScreen);
    }
}