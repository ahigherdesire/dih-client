package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RegistryListSetting;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * Shared timing for W-Tap and S-Tap. A sprinting hit deals extra knockback and then stops the attacker's sprint, so
 * the next hit is a plain one unless sprint is restarted in between. After each hit these modules briefly let go of
 * W (or tap S), which ends the sprint, then hand the keys back so sprint starts again before the next hit.
 */
abstract class SprintResetModule extends Module {
    /** A hit charged past this ends the attacker's sprint; a weaker one leaves it running. */
    static final float SPRINT_HIT_CHARGE = 0.9F;

    private final Random random = new Random();
    private final CombatFilters.IdSet heldItemIds = new CombatFilters.IdSet();
    private int waitTicks = -1;
    private int holdTicks;
    private int lastTapTick = Integer.MIN_VALUE;

    SprintResetModule(String id, String name, String description, int defaultHold) {
        super(id, name, ModuleCategory.COMBAT, description);
        add(new IntSetting("delay", "Delay", 0, 0, 5, 1).unit("ticks")
            .group("General")
            .description("Wait after the hit before tapping.")
            .visibleWhen(this::usesKeys));
        add(new IntSetting("hold", "Hold", defaultHold, 1, 10, 1).unit("ticks")
            .group("General")
            .description("How long the tap lasts.")
            .visibleWhen(this::usesKeys));
        add(new IntSetting("chance", "Chance", 100, 1, 100, 1).unit("%")
            .group("General")
            .description("Share of hits that get a tap."));
        add(new IntSetting("tap-cooldown", "Tap Cooldown", 0, 0, 20, 1).unit("ticks")
            .group("General")
            .description("Ticks after a tap before another can start; 0 allows one per hit."));

        add(new BoolSetting("only-sprinting", "Only Sprinting", true)
            .group("Conditions")
            .description("Only after hits made while sprinting."));
        add(new BoolSetting("only-full-hits", "Only Full Hits", false)
            .group("Conditions")
            .description("Only after hits charged past 90%: weaker hits keep your sprint anyway."));
        add(new BoolSetting("only-players", "Only Players", false)
            .group("Conditions")
            .description("Only after hitting a player."));
        add(new BoolSetting("only-on-ground", "Only On Ground", false)
            .group("Conditions")
            .description("Skip hits made in the air."));
        add(new BoolSetting("only-moving", "Only Moving Forward", false)
            .group("Conditions")
            .description("Only while you hold the forward key."));
        add(new BoolSetting("skip-liquid", "Skip In Liquid", true)
            .group("Conditions")
            .description("Skip hits made in water or lava, where sprint works differently."));
        add(new BoolSetting("held-items-only", "Held Items Only", false)
            .group("Conditions")
            .description("Only while holding a listed item."));
        add(RegistryListSetting.items("held-items", "Held Items", CombatFilters.DEFAULT_WEAPONS)
            .group("Conditions")
            .description("Main-hand items that get taps.")
            .visibleWhen(() -> bool("held-items-only")));
    }

    /** The key pressed (true) or released (false) during the tap. */
    abstract KeyMapping key();

    abstract boolean pressDuringTap();

    /** Whether a tap is holding its key this tick. */
    final boolean tapping() {
        return holdTicks > 0;
    }

    /** Whether taps press or release a key (the delay and hold settings only apply then). */
    boolean usesKeys() {
        return true;
    }

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
        lastTapTick = Integer.MIN_VALUE;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundAttackPacket attack)) return false;
        LocalPlayer player = MC.player;
        if (player == null) return false;
        if (!wantsTap(player, attack)) return false;
        if (random.nextInt(100) >= integer("chance")) return false;
        lastTapTick = player.tickCount;
        beforeAttack(player);
        if (holdTicks <= 0 && waitTicks < 0) waitTicks = integer("delay");
        return false;
    }

    private boolean wantsTap(LocalPlayer player, ServerboundAttackPacket attack) {
        if (bool("only-sprinting") && !player.isSprinting()) return false;
        if (bool("only-full-hits") && player.getAttackStrengthScale(0.5F) <= SPRINT_HIT_CHARGE) return false;
        if (bool("only-on-ground") && !player.onGround()) return false;
        if (bool("skip-liquid") && (player.isInWater() || player.isInLava())) return false;
        if (bool("only-moving") && !DihKeyMappingBridge.of(MC.options.keyUp).dih$isActuallyDown()) return false;
        if (bool("held-items-only")
            && !CombatFilters.holds(player.getMainHandItem(), heldItemIds, list("held-items"))) {
            return false;
        }
        if (bool("only-players") && (MC.level == null || !(MC.level.getEntity(attack.entityId()) instanceof Player))) {
            return false;
        }
        return cooledDown(lastTapTick, player.tickCount, integer("tap-cooldown"));
    }

    /** Whether {@code cooldown} ticks have passed since the tap at {@code lastTap}. */
    static boolean cooledDown(int lastTap, int now, int cooldown) {
        return cooldown <= 0 || lastTap == Integer.MIN_VALUE || now - lastTap >= cooldown;
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
