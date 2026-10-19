package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.KeyMapping;

/**
 * Taps S for a moment after each hit: you stop, your target is pushed away, and sprint restarts for the next hit with
 * full knockback. A longer tap than W-Tap also backs you off, which helps keep spacing.
 */
public final class STapModule extends SprintResetModule {
    private boolean releasedForward;

    public STapModule() {
        super("s-tap", "STap", "Taps S after each hit to reset sprint and keep your distance.", 3);
        add(new BoolSetting("release-forward", "Release W", false)
            .group("General")
            .description("Also let go of W during the tap, so you back off instead of stopping in place."));
    }

    @Override
    KeyMapping key() {
        return MC.options.keyDown;
    }

    @Override
    boolean pressDuringTap() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (MC.player == null || MC.options == null) return;
        if (tapping() && bool("release-forward")) {
            MC.options.keyUp.setDown(false);
            releasedForward = true;
        } else {
            restoreForward();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        restoreForward();
    }

    private void restoreForward() {
        if (!releasedForward || MC.options == null) return;
        releasedForward = false;
        KeyMapping forward = MC.options.keyUp;
        forward.setDown(DihKeyMappingBridge.of(forward).dih$isActuallyDown());
    }
}
