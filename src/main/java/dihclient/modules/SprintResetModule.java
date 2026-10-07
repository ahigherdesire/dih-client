package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

import java.util.Random;

/**
 * Shared timing for W-Tap and S-Tap. A sprinting hit deals extra knockback and then stops the attacker's sprint, so
 * the next hit is a plain one unless sprint is restarted in between. After each hit these modules briefly let go of
 * W (or tap S), which ends the sprint, then hand the keys back so sprint starts again before the next hit.
 */
abstract class SprintResetModule extends Module {
    private final Random random = new Random();
    private int waitTicks = -1;
    private int holdTicks;

    SprintResetModule(String id, String name, String description, int defaultHold) {
        super(id, name, ModuleCategory.COMBAT, description);
        add(new IntSetting("delay", "Delay", 0, 0, 5, 1).unit("ticks").description("Wait after the hit before tapping."));
        add(new IntSetting("hold", "Hold", defaultHold, 1, 10, 1).unit("ticks").description("How long the tap lasts."));
        add(new IntSetting("chance", "Chance", 100, 1, 100, 1).unit("%").description("Share of hits that get a tap."));
        add(new BoolSetting("only-sprinting", "Only Sprinting", true).description("Only after hits made while sprinting."));
    }

    /** The key pressed (true) or released (false) during the tap. */
    abstract KeyMapping key();

    abstract boolean pressDuringTap();

    /** Runs just before a hit that gets a tap goes out. */
    void beforeAttack(LocalPlayer player) {
    }

    @Override
    public void onDisable() {
        end();
    }

    @Override
    public void onGameLeft() {
        waitTicks = -1;
        holdTicks = 0;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundAttackPacket)) return false;
        LocalPlayer player = MC.player;
        if (player == null) return false;
        if (bool("only-sprinting") && !player.isSprinting()) return false;
        if (random.nextInt(100) >= integer("chance")) return false;
        beforeAttack(player);
        if (holdTicks <= 0 && waitTicks < 0) waitTicks = integer("delay");
        return false;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.options == null) return;
        if (waitTicks >= 0 && waitTicks-- == 0) holdTicks = integer("hold");
        if (holdTicks > 0) {
            key().setDown(pressDuringTap());
            if (--holdTicks == 0) end();
        }
    }

    private void end() {
        waitTicks = -1;
        holdTicks = 0;
        if (MC.options == null) return;
        KeyMapping key = key();
        key.setDown(DihKeyMappingBridge.of(key).dih$isActuallyDown());
    }
}
