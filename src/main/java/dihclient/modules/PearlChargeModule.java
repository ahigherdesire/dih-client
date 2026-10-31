package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihInventoryHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pops a wind charge at your feet and throws an ender pearl into the burst, so you hop about 6 blocks and the pearl
 * flies a third faster: about 50 blocks up instead of 30, or 65 blocks out at 45 degrees instead of 50. The burst only
 * catches the pearl if it reaches the server in the tick after the charge, from the ground; any other tick leaves a
 * plain throw. Client ticks don't line up with server ticks, so the pearl goes on the frame the server's copy of the
 * charge shows up, which is sent at the end of the tick it spawned. On a high ping that news comes too late, so the
 * pearl follows one server tick behind the charge by the clock instead. A pearl lands you where it lands, so aimed
 * straight up into open sky it falls back to where you were.
 * Bind a key to it: one press does one launch and the module turns itself back off.
 */
public final class PearlChargeModule extends Module {
    /** Gives up if another module keeps the hand for this many ticks. */
    private static final int MAX_WAIT_TICKS = 5;
    /** At or above this ping the server's copy of the charge arrives after the pearl has to leave. */
    private static final int HIGH_PING_MS = 40;
    private static final long SERVER_TICK_NANOS = 50_000_000L;

    private boolean armed;
    private int armTick;
    private boolean usedCharge;
    private long chargeSentNanos;
    private boolean checkedItems;
    private int originalSlot = -1;

    PearlChargeModule() {
        super("pearl-charge", "PearlCharge", ModuleCategory.MOVEMENT,
            "Pops a wind charge at your feet and throws a pearl into the burst. Bind a key to it.");

        add(new IntSetting("launch-pitch", "Launch Pitch", 90, 0, 90, 1).unit("°")
            .group("General")
            .description("How far down the wind charge is aimed; 90 pops it at your feet for a straight-up launch.").build());
        add(new BoolSetting("pearl-where-looking", "Pearl Where Looking", true)
            .group("General")
            .description("Throw the pearl where you are looking; off throws it straight up for height.").build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("General")
            .description("Return to the slot you were holding after launching.").build());
    }

    @Override
    public boolean emitsToggleMessage() {
        return false;
    }

    @Override
    public void onEnable() {
        armed = true;
        armTick = 0;
        usedCharge = false;
        checkedItems = false;
        originalSlot = MC.player == null ? -1 : MC.player.getInventory().getSelectedSlot();
    }

    @Override
    public void onDisable() {
        switchBack();
        armed = false;
    }

    @Override
    public void onGameLeft() {
        originalSlot = -1;
        armed = false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (!armed || player == null || MC.gameMode == null || MC.getConnection() == null) return;

        Inventory inventory = player.getInventory();
        if (!checkedItems) {
            checkedItems = true;
            String problem = launchProblem(player, inventory);
            if (problem != null) {
                DihClientMessaging.sendPrefixed("PearlCharge: " + problem);
                finish();
                return;
            }
        }

        if (usedCharge) {
            if (++armTick > MAX_WAIT_TICKS) throwPearl(player); // the charge never showed up
        } else if (throwItem(slotOf(inventory, Items.WIND_CHARGE), player.getYRot(), integer("launch-pitch"))) {
            usedCharge = true;
            chargeSentNanos = System.nanoTime();
            armTick = 0;
        } else if (++armTick > MAX_WAIT_TICKS) {
            finish();
        }
    }

    @Override
    public void onPacketProcessFrame() {
        LocalPlayer player = MC.player;
        if (!armed || !usedCharge || player == null || MC.level == null) return;
        boolean due = highPing(player)
            ? System.nanoTime() - chargeSentNanos >= SERVER_TICK_NANOS
            : chargeSpawned(player);
        if (due) throwPearl(player);
    }

    private void throwPearl(LocalPlayer player) {
        float pitch = bool("pearl-where-looking") ? player.getXRot() : -90.0f;
        throwItem(slotOf(player.getInventory(), Items.ENDER_PEARL), player.getYRot(), pitch);
        finish();
    }

    private static boolean highPing(LocalPlayer player) {
        var info = MC.getConnection() == null ? null : MC.getConnection().getPlayerInfo(player.getUUID());
        return info != null && info.getLatency() >= HIGH_PING_MS;
    }

    private static boolean chargeSpawned(LocalPlayer player) {
        return !MC.level.getEntitiesOfClass(WindCharge.class, player.getBoundingBox().inflate(4.0D),
            charge -> charge.getOwner() == player).isEmpty();
    }

    /** Why a launch can't work right now, or null if it can. */
    private static String launchProblem(LocalPlayer player, Inventory inventory) {
        int pearl = slotOf(inventory, Items.ENDER_PEARL);
        int charge = slotOf(inventory, Items.WIND_CHARGE);
        if (pearl < 0 || charge < 0) return "put an ender pearl and a wind charge on your hotbar";
        if (!player.onGround()) return "stand on the ground, the wind charge needs it to burst";
        if (player.getCooldowns().isOnCooldown(inventory.getItem(pearl))
            || player.getCooldowns().isOnCooldown(inventory.getItem(charge))) {
            return "still on cooldown";
        }
        return null;
    }

    /** Selects the slot, faces the throw direction for the server, uses the item, then restores the look. */
    private boolean throwItem(int slot, float yaw, float pitch) {
        LocalPlayer player = MC.player;
        if (slot < 0 || player == null || MC.gameMode == null || MC.getConnection() == null) return false;
        if (!DihHandArbiter.beginHandPacketGroup(id())) return false;
        try {
            int selected = player.getInventory().getSelectedSlot();
            if (slot != selected) {
                if (originalSlot < 0) originalSlot = selected;
                DihInventoryHelper.selectHotbarSlot(MC, slot);
            }
            float oldYaw = player.getYRot();
            float oldPitch = player.getXRot();
            player.setYRot(yaw);
            player.setXRot(pitch);
            MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
            try {
                MC.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            } finally {
                player.setYRot(oldYaw);
                player.setXRot(oldPitch);
            }
            return true;
        } finally {
            DihHandArbiter.endHandPacketGroup(id());
        }
    }

    private static int slotOf(Inventory inventory, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) return slot;
        }
        return -1;
    }

    private void switchBack() {
        if (originalSlot < 0 || MC.player == null) {
            originalSlot = -1;
            return;
        }
        if (bool("switch-back") && DihHandArbiter.beginHandPacketGroup(id())) {
            try {
                DihInventoryHelper.selectHotbarSlot(MC, originalSlot);
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }
        originalSlot = -1;
    }

    private void finish() {
        switchBack();
        armed = false;
        setEnabledSilently(false);
    }
}
