package dihclient.modules;

import dihclient.api.module.ChoiceSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Lets go of W for a moment after each hit so every hit lands with sprint knockback. Packet mode instead tells the
 * server sprint stopped and started again just before the hit, with no visible tap (the same trick as LiquidBounce's
 * SuperKnockback); anticheats can notice it.
 */
public final class WTapModule extends SprintResetModule {
    public WTapModule() {
        super("w-tap", "WTap", "Lets go of W after each hit so every hit gets sprint knockback.", 2);
        add(new ChoiceSetting("mode", "Mode", "Legit", "Legit", "Packet")
            .group("General")
            .description("Legit: really release W. Packet: resend sprint before the hit (anticheats can notice)."));
    }

    @Override
    boolean usesKeys() {
        return !"Packet".equals(value("mode"));
    }

    @Override
    KeyMapping key() {
        return MC.options.keyUp;
    }

    @Override
    boolean pressDuringTap() {
        return false;
    }

    @Override
    void beforeAttack(LocalPlayer player) {
        if (!"Packet".equals(choice("mode")) || MC.getConnection() == null) return;
        // Criticals' packet mode stops sprint around the hit on purpose; leave its hits alone.
        Module criticals = ModuleRegistry.get("criticals");
        if (criticals != null && criticals.isEnabled() && "Packet".equals(criticals.value("mode"))) return;
        MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
    }

    @Override
    public void tick() {
        if ("Packet".equals(choice("mode"))) return;
        super.tick();
    }
}
