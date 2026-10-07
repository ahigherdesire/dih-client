package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RangeSetting;
import dihclient.api.module.ValueRange;
import dihclient.util.DihEntities;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Keeps you from being kicked for idling: now and then it turns, swings, jumps, steps or changes slot. Ported from
 * LiquidBounce's AntiAFK (GPL-3.0, CCBlueX) "RandomInteraction" mode. Here it waits until you have been idle a while
 * and stops the moment you touch a key or the mouse, so it never fights you.
 */
public final class AntiAfkModule extends Module {
    private static final float MAX_TURN = 45.0F;

    private final Random random = new Random();
    private final List<KeyMapping> held = new ArrayList<>();
    private int idleTicks;
    private int cooldown;
    private int holdTicks;
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;

    public AntiAfkModule() {
        super("anti-afk", "AntiAFK", ModuleCategory.PLAYER, "Moves now and then so you are not kicked for idling.");
        add(new IntSetting("idle-time", "Idle Time", 30, 0, 600, 5).unit("s")
            .description("Start only after this long without input; 0 acts all the time."));
        add(new RangeSetting("interval", "Interval", new ValueRange(40, 100), 5, 600, 5).unit("ticks")
            .description("Time between actions."));
        add(new BoolSetting("rotate", "Rotate", true).group("Actions"));
        add(new BoolSetting("swing", "Swing", true).group("Actions"));
        add(new BoolSetting("jump", "Jump", false).group("Actions"));
        add(new BoolSetting("walk", "Walk", false).group("Actions").description("Step a little in a random direction."));
        add(new BoolSetting("sneak", "Sneak", false).group("Actions"));
        add(new BoolSetting("hotbar", "Hotbar", false).group("Actions").description("Switch to a random hotbar slot."));
    }

    @Override
    public void onEnable() {
        idleTicks = 0;
        cooldown = 0;
        lastYaw = Float.NaN;
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void onGameLeft() {
        release();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.options == null) return;
        boolean turnedByUser = !Float.isNaN(lastYaw)
            && (Math.abs(player.getYRot() - lastYaw) > 1.0E-3F || Math.abs(player.getXRot() - lastPitch) > 1.0E-3F);
        if (turnedByUser || userKeyDown() || MC.gui.screen() != null) {
            idleTicks = 0;
            release();
            remember(player);
            return;
        }
        idleTicks++;
        if (holdTicks > 0) {
            if (--holdTicks == 0) release();
            else for (KeyMapping key : held) key.setDown(true);
        }
        if (idleTicks < integer("idle-time") * 20 || --cooldown > 0) {
            remember(player);
            return;
        }
        ValueRange interval = ValueRange.parse(value("interval"), new ValueRange(40, 100));
        cooldown = (int) Math.round(interval.random(random));
        perform(player);
        remember(player);
    }

    private void perform(LocalPlayer player) {
        List<Runnable> actions = new ArrayList<>();
        if (bool("rotate")) actions.add(() -> {
            player.setYRot(player.getYRot() + (random.nextFloat() * 2.0F - 1.0F) * MAX_TURN);
            player.setXRot(Mth.clamp(player.getXRot() + (random.nextFloat() * 2.0F - 1.0F) * 5.0F, -90.0F, 90.0F));
        });
        if (bool("swing")) actions.add(() -> DihEntities.swing(player, InteractionHand.MAIN_HAND));
        if (bool("jump")) actions.add(() -> hold(2, MC.options.keyJump));
        if (bool("walk")) actions.add(() -> {
            KeyMapping[] keys = {MC.options.keyUp, MC.options.keyDown, MC.options.keyLeft, MC.options.keyRight};
            hold(4 + random.nextInt(8), keys[random.nextInt(keys.length)]);
        });
        if (bool("sneak")) actions.add(() -> hold(5 + random.nextInt(10), MC.options.keyShift));
        if (bool("hotbar")) actions.add(() -> player.getInventory().setSelectedSlot(random.nextInt(9)));
        if (!actions.isEmpty()) actions.get(random.nextInt(actions.size())).run();
    }

    private void hold(int ticks, KeyMapping key) {
        release();
        key.setDown(true);
        held.add(key);
        holdTicks = ticks;
    }

    private void release() {
        for (KeyMapping key : held) key.setDown(DihKeyMappingBridge.of(key).dih$isActuallyDown());
        held.clear();
        holdTicks = 0;
    }

    private void remember(LocalPlayer player) {
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
    }

    private boolean userKeyDown() {
        KeyMapping[] keys = {MC.options.keyUp, MC.options.keyDown, MC.options.keyLeft, MC.options.keyRight,
            MC.options.keyJump, MC.options.keyShift, MC.options.keyAttack, MC.options.keyUse};
        for (KeyMapping key : keys) {
            if (DihKeyMappingBridge.of(key).dih$isActuallyDown()) return true;
        }
        return false;
    }
}
