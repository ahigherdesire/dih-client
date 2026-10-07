package dihclient.modules;

import net.minecraft.client.KeyMapping;

/**
 * Taps S for a moment after each hit: you stop, your target is pushed away, and sprint restarts for the next hit with
 * full knockback. A longer tap than W-Tap also backs you off, which helps keep spacing.
 */
public final class STapModule extends SprintResetModule {
    public STapModule() {
        super("s-tap", "STap", "Taps S after each hit to reset sprint and keep your distance.", 3);
    }

    @Override
    KeyMapping key() {
        return MC.options.keyDown;
    }

    @Override
    boolean pressDuringTap() {
        return true;
    }
}
