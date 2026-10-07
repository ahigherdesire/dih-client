package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;

/**
 * Hold to zoom. Ported from LiquidBounce's Zoom (GPL-3.0, CCBlueX): an eased transition in and out, and the scroll
 * wheel changes the zoom while held. Added here: the mouse slows down with the zoom so aiming stays as fine as at
 * normal view, and an optional cinematic camera while zoomed. The zoom scales the finished field of view, so sprint
 * and speed effects still show.
 */
public final class ZoomModule extends Module {
    private static ZoomModule instance;

    private float fromFov;
    private float toFov;
    private long startNanos;
    private float scrolledFov = -1.0F;

    public ZoomModule() {
        super("zoom", "Zoom", ModuleCategory.RENDER, "Hold to zoom in.");
        add(new IntSetting("fov", "FOV", 30, 1, 110, 1).unit("deg").description("Field of view while zoomed."));
        add(new IntSetting("duration", "Duration", 150, 0, 1000, 10).unit("ms").description("Zoom transition time."));
        add(new BoolSetting("scroll", "Scroll", true).description("Scroll to zoom further in or out while held."));
        add(new IntSetting("scroll-step", "Scroll Step", 3, 1, 20, 1).unit("deg").visibleWhen(() -> bool("scroll")));
        add(new BoolSetting("scale-sensitivity", "Scale Sensitivity", true)
            .description("Slow the mouse down with the zoom."));
        add(new BoolSetting("cinematic", "Cinematic", false).description("Smooth camera while zoomed."));
        instance = this;
    }

    @Override
    public boolean holdToActivate() {
        return true;
    }

    @Override
    public boolean emitsToggleMessage() {
        return false;
    }

    @Override
    public void onEnable() {
        scrolledFov = -1.0F;
        retarget(integer("fov"));
    }

    @Override
    public void onDisable() {
        retarget(baseFov());
    }

    @Override
    public void onGameLeft() {
        if (isEnabled()) setEnabled(false);
    }

    private void retarget(float target) {
        fromFov = displayedFov();
        toFov = target;
        startNanos = System.nanoTime();
    }

    private float baseFov() {
        return MC.options == null ? 70.0F : MC.options.fov().get();
    }

    /** The zoom the screen shows now, in degrees of the base field of view. */
    private float displayedFov() {
        if (startNanos == 0L) return baseFov();
        return Math.max(1.0F, lerp(fromFov, toFov, progress()));
    }

    private float progress() {
        return eased((System.nanoTime() - startNanos) / 1.0E6F, integer("duration"));
    }

    /** Ease-out quad of {@code elapsedMs} through a {@code durationMs} transition, 0 to 1. */
    static float eased(float elapsedMs, float durationMs) {
        if (durationMs <= 0.0F) return 1.0F;
        float t = Math.max(0.0F, Math.min(1.0F, elapsedMs / durationMs));
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private boolean idle() {
        return !isEnabled() && (startNanos == 0L || progress() >= 1.0F);
    }

    /** Camera hook: the field of view after zoom, from vanilla's. */
    public static float applyFov(float vanillaFov) {
        ZoomModule zoom = instance;
        if (zoom == null || zoom.idle() || PackHideState.isActive()) return vanillaFov;
        return vanillaFov * zoom.displayedFov() / Math.max(1.0F, zoom.baseFov());
    }

    /** Mouse hook: the sensitivity option scaled so turning slows down with the zoom. */
    public static double scaleSensitivity(double sensitivity) {
        ZoomModule zoom = instance;
        if (zoom == null || zoom.idle() || !zoom.bool("scale-sensitivity") || PackHideState.isActive()) return sensitivity;
        return sensitivityFor(sensitivity, zoom.displayedFov() / Math.max(1.0F, zoom.baseFov()));
    }

    /**
     * The sensitivity option that turns {@code ratio} times as far: vanilla turns by {@code (0.6s + 0.2)^3} per mouse
     * count, so the cube root of the ratio goes inside.
     */
    static double sensitivityFor(double sensitivity, double ratio) {
        double factor = (sensitivity * 0.6D + 0.2D) * Math.cbrt(Math.max(0.0D, Math.min(1.0D, ratio)));
        return (factor - 0.2D) / 0.6D;
    }

    /** Mouse hook: whether the camera should smooth while zoomed. */
    public static boolean cinematic() {
        ZoomModule zoom = instance;
        return zoom != null && zoom.isEnabled() && zoom.bool("cinematic") && !PackHideState.isActive();
    }

    /** Scroll hook: true when the wheel changed the zoom and should not also move the hotbar. */
    public static boolean onScroll(double amount) {
        ZoomModule zoom = instance;
        if (zoom == null || !zoom.isEnabled() || !zoom.bool("scroll") || amount == 0.0D) return false;
        if (MC.gui.screen() != null || PackHideState.isActive()) return false;
        float current = zoom.scrolledFov > 0.0F ? zoom.scrolledFov : zoom.integer("fov");
        float next = Math.max(1.0F, Math.min(zoom.baseFov(), current - (float) Math.signum(amount) * zoom.integer("scroll-step")));
        zoom.scrolledFov = next;
        zoom.retarget(next);
        return true;
    }
}
