package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RangeSetting;
import dihclient.api.module.StringSetting;
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
    private static final float MAX_PITCH_CHANGE = 5.0F;
    private static final ValueRange WALK_TICKS = new ValueRange(4, 11);
    private static final ValueRange SNEAK_TICKS = new ValueRange(5, 14);

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
            .group("General")
            .description("Start only after this long without input; 0 acts all the time."));
        add(new RangeSetting("interval", "Interval", new ValueRange(40, 100), 5, 600, 5).unit("ticks")
            .group("General")
            .description("Time between actions."));
        add(new ChoiceSetting("mode", "Mode", "Random", "Random", "All")
            .group("General")
            .description("Do one random action each time, or every action you turned on"));
        add(new BoolSetting("pause-in-screens", "Pause In Screens", true)
            .group("General")
            .description("Stops while a screen such as chat or your inventory is open"));

        add(new BoolSetting("rotate", "Rotate", true).group("Actions")
            .description("Turns your view a little"));
        add(new BoolSetting("swing", "Swing", true).group("Actions")
            .description("Swings your arm"));
        add(new BoolSetting("jump", "Jump", false).group("Actions")
            .description("Jumps once"));
        add(new BoolSetting("walk", "Walk", false).group("Actions").description("Step a little in a random direction."));
        add(new BoolSetting("sneak", "Sneak", false).group("Actions")
            .description("Crouches for a moment"));
        add(new BoolSetting("hotbar", "Hotbar", false).group("Actions").description("Switch to a random hotbar slot."));
        add(new BoolSetting("chat", "Chat", false).group("Actions")
            .description("Sends the message below; a leading / sends it as a command"));
        add(new BoolSetting("spin", "Spin", false).group("Actions")
            .description("Keeps turning in a circle the whole time you are idle"));

        add(new IntSetting("turn-angle", "Turn Angle", (int) MAX_TURN, 5, 180, 5).unit("°")
            .group("Tuning")
            .description("Largest left or right turn")
            .visibleWhen(() -> bool("rotate")));
        add(new IntSetting("pitch-change", "Pitch Change", (int) MAX_PITCH_CHANGE, 0, 45, 1).unit("°")
            .group("Tuning")
            .description("Largest up or down look change")
            .visibleWhen(() -> bool("rotate")));
        add(new RangeSetting("walk-time", "Walk Time", WALK_TICKS, 1, 40, 1).unit("ticks")
            .group("Tuning")
            .description("How long each step lasts")
            .visibleWhen(() -> bool("walk")));
        add(new RangeSetting("sneak-time", "Sneak Time", SNEAK_TICKS, 1, 60, 1).unit("ticks")
            .group("Tuning")
            .description("How long each crouch lasts")
            .visibleWhen(() -> bool("sneak")));
        add(new StringSetting("message", "Message", "")
            .group("Tuning")
            .description("Chat line or /command to send")
            .visibleWhen(() -> bool("chat")));
        add(new IntSetting("spin-speed", "Spin Speed", 5, 1, 45, 1).unit("°/tick")
            .group("Tuning")
            .description("How fast Spin turns")
            .visibleWhen(() -> bool("spin")));
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
        boolean screenOpen = MC.gui.screen() != null && bool("pause-in-screens");
        if (turnedByUser || userKeyDown() || screenOpen) {
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
        boolean idle = idleTicks >= integer("idle-time") * 20;
        if (idle && bool("spin")) player.setYRot(player.getYRot() + integer("spin-speed"));
        if (!idle || --cooldown > 0) {
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
            float turn = integer("turn-angle");
            float pitch = integer("pitch-change");
            player.setYRot(player.getYRot() + (random.nextFloat() * 2.0F - 1.0F) * turn);
            player.setXRot(Mth.clamp(player.getXRot() + (random.nextFloat() * 2.0F - 1.0F) * pitch, -90.0F, 90.0F));
        });
        if (bool("swing")) actions.add(() -> DihEntities.swing(player, InteractionHand.MAIN_HAND));
        if (bool("jump")) actions.add(() -> hold(2, MC.options.keyJump));
        if (bool("walk")) actions.add(() -> {
            KeyMapping[] keys = {MC.options.keyUp, MC.options.keyDown, MC.options.keyLeft, MC.options.keyRight};
            hold(ticks("walk-time", WALK_TICKS), keys[random.nextInt(keys.length)]);
        });
        if (bool("sneak")) actions.add(() -> hold(ticks("sneak-time", SNEAK_TICKS), MC.options.keyShift));
        if (bool("hotbar")) actions.add(() -> player.getInventory().setSelectedSlot(random.nextInt(9)));
        if (bool("chat") && !text("message").isBlank()) actions.add(this::sendMessage);
        if (actions.isEmpty()) return;
        release();
        if ("All".equals(choice("mode"))) {
            for (Runnable action : actions) action.run();
        } else {
            actions.get(random.nextInt(actions.size())).run();
        }
    }

    private int ticks(String settingId, ValueRange fallback) {
        return Math.max(1, (int) Math.round(ValueRange.parse(value(settingId), fallback).random(random)));
    }

    private void sendMessage() {
        String message = text("message").trim();
        if (message.startsWith("/")) {
            sendCommand(message);
        } else if (MC.getConnection() != null && !PackHideState.isHardLocked()) {
            MC.getConnection().sendChat(message);
        }
    }

    private void hold(int ticks, KeyMapping key) {
        key.setDown(true);
        if (!held.contains(key)) held.add(key);
        holdTicks = Math.max(holdTicks, ticks);
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
