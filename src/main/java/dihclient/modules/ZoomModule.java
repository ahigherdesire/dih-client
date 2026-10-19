package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
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
    private boolean zoomingIn;
    private boolean hidHud;

    public ZoomModule() {
        super("zoom", "Zoom", ModuleCategory.RENDER, "Hold to zoom in.");
        add(new IntSetting("fov", "FOV", 30, 1, 110, 1).unit("deg").group("General").description("Field of view while zoomed."));
        add(new IntSetting("duration", "Duration", 150, 0, 1000, 10).unit("ms").group("General").description("Zoom-in transition time."));
        add(new IntSetting("out-duration", "Out Duration", 150, 0, 1000, 10).unit("ms").group("General")
            .description("Zoom-out transition time."));
        add(new ChoiceSetting("easing", "Easing", "Ease Out", "Ease Out", "Linear", "Smooth").group("General")
            .description("Shape of the transition: fast then slow, steady, or slow at both ends."));
        add(new BoolSetting("hold", "Hold Key", true).group("General")
            .description("Only while the key is held; off makes the key toggle it."));
        add(new BoolSetting("hide-hud", "Hide HUD", false).group("General")
            .description("Hides the HUD and hand while zoomed."));

        add(new BoolSetting("scroll", "Scroll", true).group("Scroll").description("Scroll to zoom further in or out while held."));
        add(new IntSetting("scroll-step", "Scroll Step", 3, 1, 20, 1).unit("deg").group("Scroll")
            .description("Degrees per scroll notch.").visibleWhen(() -> bool("scroll")));
        add(new IntSetting("min-fov", "Min FOV", 1, 1, 60, 1).unit("deg").group("Scroll")
            .description("Narrowest view scrolling can reach.").visibleWhen(() -> bool("scroll")));
        add(new BoolSetting("remember-scroll", "Remember Scroll", false).group("Scroll")
            .description("Starts the next zoom at the level you scrolled to.").visibleWhen(() -> bool("scroll")));

        add(new BoolSetting("scale-sensitivity", "Scale Sensitivity", true).group("Mouse")
            .description("Slow the mouse down with the zoom."));
        add(new BoolSetting("cinematic", "Cinematic", false).group("Mouse").description("Smooth camera while zoomed."));
        instance = this;
    }

    @Override
    public boolean holdToActivate() {
        return bool("hold");
    }

    @Override
    public boolean emitsToggleMessage() {
        return false;
    }

    @Override
    public void onEnable() {
        if (!bool("remember-scroll")) scrolledFov = -1.0F;
        zoomingIn = true;
        retarget(scrolledFov > 0.0F ? scrolledFov : integer("fov"));
        if (bool("hide-hud") && MC.gui != null && !MC.gui.hud.isHidden()) {
            MC.gui.hud.toggle();
            hidHud = true;
        }
    }

    @Override
    public void onDisable() {
        zoomingIn = false;
        retarget(baseFov());
        if (hidHud && MC.gui != null && MC.gui.hud.isHidden()) MC.gui.hud.toggle();
        hidHud = false;
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
        float elapsed = (System.nanoTime() - startNanos) / 1.0E6F;
        float duration = integer(zoomingIn ? "duration" : "out-duration");
        float t = linear(elapsed, duration);
        return switch (choice("easing")) {
            case "Linear" -> t;
            case "Smooth" -> t * t * (3.0F - 2.0F * t);
            default -> eased(elapsed, duration);
        };
    }

    private static float linear(float elapsedMs, float durationMs) {
        if (durationMs <= 0.0F) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, elapsedMs / durationMs));
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
        float next = Math.max(Math.max(1.0F, zoom.integer("min-fov")),
            Math.min(zoom.baseFov(), current - (float) Math.signum(amount) * zoom.integer("scroll-step")));
        zoom.scrolledFov = next;
        zoom.retarget(next);
        return true;
    }
}
