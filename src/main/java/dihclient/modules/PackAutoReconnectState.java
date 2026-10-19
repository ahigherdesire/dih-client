package dihclient.modules;

import dihclient.ducks.DihDisconnectedScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class PackAutoReconnectState {
    private static final Minecraft MC = Minecraft.getInstance();
    private static ServerData lastServer;
    private static ServerAddress lastAddress;
    private static Screen countdownScreen;
    private static int ticksLeft;
    /** Set by Flee so a deliberate disconnect isn't undone by AutoReconnect; cleared on the next join. */
    private static boolean held;
    /** Reconnects attempted since the last successful join; reset by {@link #remember}. */
    private static int attempts;
    private static final java.util.Random RANDOM = new java.util.Random();

    private PackAutoReconnectState() {
    }

    public static void remember(ServerData server) {
        if (server == null || server.ip == null || server.ip.isBlank()) return;
        remember(server, ServerAddress.parseString(server.ip));
    }

    public static void remember(ServerData server, ServerAddress address) {
        if (server == null) return;
        if ((server.ip == null || server.ip.isBlank()) && address == null) return;
        ServerData copy = new ServerData(server.name, server.ip, server.type());
        copy.copyFrom(server);
        lastServer = copy;
        lastAddress = address != null ? address : ServerAddress.parseString(server.ip);
        held = false;
        attempts = 0;
        countdownScreen = null;
        ticksLeft = delayTicks();
    }

    /** Skip auto-reconnect until you join a server again yourself. */
    public static void holdUntilNextJoin() {
        held = true;
        countdownScreen = null;
    }

    public static boolean shouldShow() {
        if (held) return false;
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null || !module.isEnabled() || lastServer == null || lastAddress == null || !MC.allowsMultiplayer()) {
            return false;
        }
        int maxAttempts = maxAttempts(module);
        return maxAttempts <= 0 || attempts < maxAttempts;
    }

    private static int maxAttempts(Module module) {
        try {
            return Math.max(0, Integer.parseInt(module.value("max-attempts")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static boolean moduleEnabled() {
        Module module = ModuleRegistry.get("auto-reconnect");
        return module != null && module.isEnabled();
    }

    public static boolean canShowToggle() {
        return lastServer != null && lastAddress != null && MC.allowsMultiplayer();
    }

    public static void toggle() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return;
        module.setConfiguredEnabled(!module.isEnabled());
    }

    public static String toggleLabel() {
        if (!moduleEnabled()) return "Auto Reconnect: OFF";
        double seconds = isCounting() ? Math.max(0.0, ticksLeft / 20.0) : delaySeconds();
        return String.format(java.util.Locale.ROOT, "Auto Reconnect: ON  (%.1fs)", seconds);
    }

    private static double delaySeconds() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return 3.5;
        try {
            return Math.max(0.0, Double.parseDouble(module.value("delay")));
        } catch (NumberFormatException ignored) {
            return 3.5;
        }
    }

    public static void tick(Screen screen, Screen parent) {
        if (!shouldShow()) {
            countdownScreen = null;
            return;
        }
        if (countdownScreen != screen) {
            countdownScreen = screen;
            ticksLeft = delayTicks();
        }
        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }
        reconnect(parent);
    }

    public static boolean isCounting() {
        return shouldShow() && ticksLeft > 0;
    }

    public static void tickCurrentScreen() {
        Screen screen = MC.gui.screen();
        if (screen instanceof DisconnectedScreen) {

            Screen parent = screen instanceof DihDisconnectedScreenAccess access ? access.dih$parent() : screen;
            tick(screen, parent);
        } else {
            countdownScreen = null;
        }
    }

    public static void reconnect(Screen parent) {
        if (!shouldShow()) return;
        attempts++;
        Screen nextParent = parent == null ? MC.gui.screen() : parent;
        ConnectScreen.startConnecting(nextParent, MC, lastAddress, lastServer, false, null);
        countdownScreen = null;
        ticksLeft = delayTicks();
    }

    public static String statusText() {
        if (!shouldShow()) return "";
        double seconds = Math.max(0.0, ticksLeft / 20.0);
        return String.format(java.util.Locale.ROOT, "Auto reconnect in %.1fs", seconds);
    }

    private static int delayTicks() {
        Module module = ModuleRegistry.get("auto-reconnect");
        if (module == null) return 70;
        try {
            double seconds = Math.max(0.0, Double.parseDouble(module.value("delay")));
            if (Boolean.parseBoolean(module.value("randomize"))) {
                double extra = Math.max(0.0, Double.parseDouble(module.value("random-max")));
                if (extra > 0.0) seconds += RANDOM.nextDouble() * extra;
            }
            return Math.max(0, (int) Math.round(seconds * 20.0));
        } catch (NumberFormatException ignored) {
            return 70;
        }
    }
}
