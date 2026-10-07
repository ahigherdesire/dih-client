package dihclient.util;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.GpuDevice;
*///?} else {
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDevice;
//?}
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;
import java.util.Locale;

/**
 * Light UI: DIH screens skip the full-screen blur pass behind them, the one GUI cost that grows with resolution and
 * hurts integrated graphics most. Everything drawn on top stays the same. Auto turns it on for integrated, software
 * and virtual GPUs and for two-core machines.
 */
public final class DihLightUi {
    public static final List<String> MODES = List.of("AUTO", "ON", "OFF");

    private static volatile Boolean weakDevice;

    private DihLightUi() {
    }

    public static String mode() {
        DihConfig config = DihConfig.getGlobal();
        return normalize(config == null ? null : config.lightUi);
    }

    static String normalize(String mode) {
        String upper = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return MODES.contains(upper) ? upper : "AUTO";
    }

    public static boolean active() {
        return switch (mode()) {
            case "ON" -> true;
            case "OFF" -> false;
            default -> weakDevice();
        };
    }

    /** Steps through Auto, On, Off and saves. */
    public static void cycle(boolean forward) {
        DihConfig config = DihConfig.getGlobal();
        if (config == null) return;
        config.lightUi = next(mode(), forward);
        config.save();
    }

    static String next(String mode, boolean forward) {
        int i = MODES.indexOf(normalize(mode));
        return MODES.get(Math.floorMod(i + (forward ? 1 : -1), MODES.size()));
    }

    public static String label() {
        return switch (mode()) {
            case "ON" -> "On";
            case "OFF" -> "Off";
            default -> "Auto";
        };
    }

    private static boolean weakDevice() {
        Boolean known = weakDevice;
        if (known != null) return known;
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) return false;
        boolean weak;
        try {
            weak = isWeak(device.getDeviceInfo().type(), Runtime.getRuntime().availableProcessors());
        } catch (Throwable t) {
            weak = false;
        }
        weakDevice = weak;
        return weak;
    }

    static boolean isWeak(DeviceType type, int cores) {
        return type == DeviceType.INTEGRATED || type == DeviceType.CPU || type == DeviceType.VIRTUAL || cores <= 2;
    }
}
