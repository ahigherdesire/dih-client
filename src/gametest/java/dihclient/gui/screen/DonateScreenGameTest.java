package dihclient.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.util.DihConfig;
import dihclient.util.DihUiScale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Field;

/**
 * Automated client launches reach the title screen without a donate card. With the automation marker lifted, the
 * card opens over the first title screen of a launch, closes to it with Esc, Enter or "Not now", and stays away for
 * the rest of the launch.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DonateScreenGameTest implements FabricClientGameTest {
    private static final String MARKER = "fabric.client.gametest";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig.getGlobal().customMainMenu = false;
            if (System.getProperty(MARKER) == null)
                throw new AssertionError("the game-test marker is absent");
            resetLaunch();
            TitleScreen title = new TitleScreen();
            if (DihDonateScreen.overTitle(title) != title)
                throw new AssertionError("the donate card intercepted an automated launch");
        });
        context.setScreen(TitleScreen::new);
        context.waitFor(client -> client.gui.screen() instanceof TitleScreen);

        // Esc closes it to the vanilla title screen, and a screenshot records how it looks.
        openCard(context);
        context.takeScreenshot("donate-card");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() instanceof TitleScreen);

        // Once per launch: the next title screen opens directly.
        String marker = System.clearProperty(MARKER);
        try {
            context.setScreen(TitleScreen::new);
            context.waitTicks(5);
            if (!context.computeOnClient(client -> client.gui.screen() instanceof TitleScreen))
                throw new AssertionError("the donate card showed twice in one launch");
        } finally {
            System.setProperty(MARKER, marker);
        }

        // Enter closes it too.
        openCard(context);
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(client -> client.gui.screen() instanceof TitleScreen);

        // "Not now", over the DIH title screen.
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = true);
        openCard(context);
        context.waitTicks(3);
        int[] center = context.computeOnClient(client -> buttonCenter((DihDonateScreen) client.gui.screen(), "later"));
        context.getInput().setCursorPos(DihUiScale.virtualToFramebufferX(center[0]), DihUiScale.virtualToFramebufferY(center[1]));
        context.getInput().pressMouse(0);
        context.waitFor(client -> client.gui.screen() instanceof DihTitleScreen);

        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        context.setScreen(TitleScreen::new);
        context.waitFor(client -> client.gui.screen() instanceof TitleScreen);
    }

    /** Shows the title screen as a fresh, non-automated launch would, and waits for the card over it. */
    private static void openCard(ClientGameTestContext context) {
        context.runOnClient(client -> resetLaunch());
        String marker = System.clearProperty(MARKER);
        try {
            context.setScreen(TitleScreen::new);
        } finally {
            System.setProperty(MARKER, marker);
        }
        context.waitFor(client -> client.gui.screen() instanceof DihDonateScreen);
    }

    private static void resetLaunch() {
        try {
            Field shown = DihDonateScreen.class.getDeclaredField("shownThisLaunch");
            shown.setAccessible(true);
            shown.setBoolean(null, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not reset launch state for the test", e);
        }
    }

    /** Centre of a card button in the card's virtual UI coordinates. */
    private static int[] buttonCenter(Screen card, String name) {
        try {
            Field button = DihDonateScreen.class.getDeclaredField(name);
            button.setAccessible(true);
            Object btn = button.get(card);
            int[] box = new int[4];
            String[] parts = {"x", "y", "w", "h"};
            for (int i = 0; i < parts.length; i++) {
                Field f = btn.getClass().getDeclaredField(parts[i]);
                f.setAccessible(true);
                box[i] = f.getInt(btn);
            }
            if (box[2] <= 0 || box[3] <= 0) throw new AssertionError("the card has not laid out its buttons");
            return new int[]{box[0] + box[2] / 2, box[1] + box[3] / 2};
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read the " + name + " button", e);
        }
    }
}
