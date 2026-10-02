package dihclient;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.screen.DihTitleScreen;
import dihclient.util.DihConfig;
import dihclient.util.DihUiScale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Clicks Singleplayer on the vanilla and the DIH title screen with real mouse input (cursor move + button press
 * through MouseHandler, as a player would), and expects the world list to open each time. The left button is
 * InputConstants.MOUSE_BUTTON_LEFT: 1 on 26.3, where SDL renumbered the buttons, and 0 before.
 */
@SuppressWarnings("UnstableApiUsage")
public final class TitleScreenClickGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> failures = new ArrayList<>();
        try {
            // the title screen the game started on, as a player gets it after launch
            context.waitTicks(40);
            failures.add("startup: " + context.computeOnClient(TitleScreenClickGameTest::state));
            if (context.computeOnClient(c -> c.gui.screen() instanceof DihTitleScreen)) {
                double[] first = context.computeOnClient(c -> menuButtonCenter(c.gui.screen(), 0));
                List<String> firstDelivered = new ArrayList<>();
                context.runOnClient(c -> ScreenMouseEvents.beforeMouseClick(c.gui.screen()).register((s, e) ->
                    firstDelivered.add(e.x() + "," + e.y())));
                clickAt(context, first);
                failures.add("  aimed at gui " + first[0] + "," + first[1] + "; received "
                    + (firstDelivered.isEmpty() ? "no click" : String.join(" | ", firstDelivered)));
                failures.add("  after click: " + context.computeOnClient(TitleScreenClickGameTest::state));
                check(context, "startup DIH title screen", failures);
            }

            // vanilla title screen, vanilla widget
            setCustomMenu(context, false);
            context.setScreen(TitleScreen::new);
            context.waitTicks(10);
            double[] vanilla = context.computeOnClient(c -> widgetCenter(c.gui.screen(), "menu.singleplayer"));
            List<String> delivered = new ArrayList<>();
            context.runOnClient(c -> ScreenMouseEvents.beforeMouseClick(c.gui.screen()).register((s, e) ->
                delivered.add(e.x() + "," + e.y() + " button " + e.button())));
            clickAt(context, vanilla);
            if (!check(context, "vanilla title screen", failures)) {
                failures.add("  target gui " + vanilla[0] + "," + vanilla[1] + "; " + context.computeOnClient(TitleScreenClickGameTest::state));
                failures.add("  clicks the screen received: " + (delivered.isEmpty() ? "none" : String.join(" | ", delivered)));
                failures.add("  " + context.computeOnClient(c -> widgetState(c.gui.screen(), "menu.singleplayer", vanilla)));
                boolean handled = context.computeOnClient(c -> c.gui.screen().mouseClicked(
                    new MouseButtonEvent(vanilla[0], vanilla[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false));
                failures.add("  direct TitleScreen.mouseClicked returned " + handled + ", screen now "
                    + context.computeOnClient(c -> name(c.gui.screen())));
            }

            // DIH title screen, its own drawn buttons
            setCustomMenu(context, true);
            context.setScreen(DihTitleScreen::new);
            context.waitTicks(30); // past the button intro animation
            double[] dih = context.computeOnClient(c -> menuButtonCenter(c.gui.screen(), 0));
            clickAt(context, dih);
            if (!check(context, "DIH title screen", failures)) {
                // same click straight into the screen, skipping MouseHandler: tells input routing from screen logic
                boolean handled = context.computeOnClient(c -> c.gui.screen().mouseClicked(
                    new MouseButtonEvent(dih[0], dih[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false));
                failures.add("  direct DihTitleScreen.mouseClicked at " + dih[0] + "," + dih[1] + " returned " + handled
                    + ", screen now " + context.computeOnClient(c -> name(c.gui.screen())));
            }
        } finally {
            // Fabric's runner expects the vanilla title screen after each test
            setCustomMenu(context, false);
            context.setScreen(TitleScreen::new);
        }
        boolean failed = failures.stream().anyMatch(line -> line.contains("clicking Singleplayer"));
        String report = "title screen clicks:\n" + String.join("\n", failures);
        if (failed) throw new AssertionError(report);
        System.out.println(report);
    }

    private static String state(Minecraft c) {
        return "screen=" + name(c.gui.screen()) + " overlay=" + (c.gui.overlay() == null ? "none" : c.gui.overlay().getClass().getName())
            + " cursor=" + c.mouseHandler.xpos() + "," + c.mouseHandler.ypos()
            + " window=" + c.getWindow().getScreenWidth() + "x" + c.getWindow().getScreenHeight()
            + " gui=" + c.getWindow().getGuiScaledWidth() + "x" + c.getWindow().getGuiScaledHeight();
    }

    private static void setCustomMenu(ClientGameTestContext context, boolean on) {
        context.runOnClient(c -> {
            DihConfig config = DihConfig.getGlobal();
            if (config != null) config.customMainMenu = on;
        });
    }

    /** Moves the real cursor to a GUI-space point and presses the left button. */
    private static void clickAt(ClientGameTestContext context, double[] gui) {
        double[] window = context.computeOnClient(c -> new double[] {
            gui[0] * c.getWindow().getScreenWidth() / c.getWindow().getGuiScaledWidth(),
            gui[1] * c.getWindow().getScreenHeight() / c.getWindow().getGuiScaledHeight()
        });
        context.getInput().setCursorPos(window[0], window[1]);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }

    private static boolean check(ClientGameTestContext context, String what, List<String> failures) {
        String screen = context.computeOnClient(c -> name(c.gui.screen()));
        // with no saved worlds, the world list goes straight on to world creation
        boolean opened = context.computeOnClient(c -> c.gui.screen() instanceof SelectWorldScreen
            || c.gui.screen() instanceof CreateWorldScreen);
        if (opened) return true;
        failures.add(what + ": clicking Singleplayer left the screen on " + screen);
        return false;
    }

    private static String widgetState(Screen screen, String key, double[] at) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
                && t.getKey().equals(key)) {
                return key + " widget: active=" + w.active + " visible=" + w.visible + " bounds=" + w.getX() + "," + w.getY()
                    + " " + w.getWidth() + "x" + w.getHeight() + " isMouseOver=" + w.isMouseOver(at[0], at[1])
                    + " focused=" + (screen.getFocused() == null ? "none" : screen.getFocused().getClass().getSimpleName());
            }
        }
        return "no " + key + " widget";
    }

    private static double[] widgetCenter(Screen screen, String key) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
                && t.getKey().equals(key)) {
                return new double[] { w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0 };
            }
        }
        throw new AssertionError("no " + key + " widget on " + name(screen));
    }

    /** Centre of DihTitleScreen's n-th menu button, in GUI coordinates (the buttons live in DihUiScale's virtual space). */
    private static double[] menuButtonCenter(Screen screen, int index) {
        if (!(screen instanceof DihTitleScreen)) throw new AssertionError("expected DihTitleScreen, got " + name(screen));
        try {
            Field buttonsField = DihTitleScreen.class.getDeclaredField("buttons");
            buttonsField.setAccessible(true);
            List<?> buttons = (List<?>) buttonsField.get(screen);
            if (buttons.size() <= index) throw new AssertionError("DihTitleScreen has " + buttons.size() + " buttons");
            Object button = buttons.get(index);
            double k = DihUiScale.toVirtual(1.0);
            return new double[] {
                (intField(button, "x") + intField(button, "width") / 2.0) / k,
                (intField(button, "y") + intField(button, "height") / 2.0) / k
            };
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("DihTitleScreen's buttons changed shape", e);
        }
    }

    private static int intField(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(o);
    }

    private static String name(Screen screen) {
        return screen == null ? "no screen" : screen.getClass().getName();
    }
}
