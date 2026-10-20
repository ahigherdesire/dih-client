package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihInventoryHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Throws an ender pearl and pops a wind charge together to launch you high up (or toward where you look).
 * Bind a key to it: pressing the key arms one launch, the two throws go out over the next ticks, and the
 * module turns itself back off. Needs an ender pearl and a wind charge on the hotbar.
 */
public final class PearlChargeModule extends Module {
    private static final String ORDER_PEARL = "Pearl First";
    private static final String ORDER_CHARGE = "Charge First";

    private PearlChargePlan plan;
    private int armTick;
    private boolean threwPearl;
    private boolean usedCharge;
    private boolean checkedItems;
    private int originalSlot = -1;

    PearlChargeModule() {
        super("pearl-charge", "PearlCharge", ModuleCategory.MOVEMENT,
            "Throws an ender pearl and a wind charge together to launch you. Bind a key to it.");

        add(new ChoiceSetting("order", "Order", ORDER_PEARL, ORDER_PEARL, ORDER_CHARGE)
            .group("General")
            .description("Throw the pearl first, or pop the wind charge first.").build());
        add(new IntSetting("delay", "Delay", 1, 0, 10, 1).unit("ticks")
            .group("General")
            .description("Ticks between the two throws; 0 fires them on the same tick.").build());
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
        plan = new PearlChargePlan(ORDER_PEARL.equals(choice("order")), integer("delay"));
        armTick = 0;
        threwPearl = false;
        usedCharge = false;
        checkedItems = false;
        originalSlot = MC.player == null ? -1 : MC.player.getInventory().getSelectedSlot();
    }

    @Override
    public void onDisable() {
        switchBack();
        plan = null;
    }

    @Override
    public void onGameLeft() {
        originalSlot = -1;
        plan = null;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (plan == null || player == null || MC.gameMode == null || MC.getConnection() == null) return;

        Inventory inventory = player.getInventory();
        if (!checkedItems) {
            checkedItems = true;
            if (slotOf(inventory, Items.ENDER_PEARL) < 0 || slotOf(inventory, Items.WIND_CHARGE) < 0) {
                DihClientMessaging.sendPrefixed("PearlCharge: put an ender pearl and a wind charge on your hotbar");
                finish();
                return;
            }
        }

        boolean pearlFirst = ORDER_PEARL.equals(choice("order"));
        if (pearlFirst) {
            firePearl(inventory, player);
            fireCharge(inventory, player);
        } else {
            fireCharge(inventory, player);
            firePearl(inventory, player);
        }

        armTick++;
        if ((threwPearl && usedCharge) || armTick > plan.finalTick() + 2) finish();
    }

    private void firePearl(Inventory inventory, LocalPlayer player) {
        if (threwPearl || !plan.fires(PearlChargePlan.Action.PEARL, armTick)) return;
        float pitch = bool("pearl-where-looking") ? player.getXRot() : -90.0f;
        throwItem(slotOf(inventory, Items.ENDER_PEARL), player.getYRot(), pitch);
        threwPearl = true;
    }

    private void fireCharge(Inventory inventory, LocalPlayer player) {
        if (usedCharge || !plan.fires(PearlChargePlan.Action.CHARGE, armTick)) return;
        throwItem(slotOf(inventory, Items.WIND_CHARGE), player.getYRot(), integer("launch-pitch"));
        usedCharge = true;
    }

    /** Selects the slot, faces the throw direction for the server, uses the item, then restores the look. */
    private void throwItem(int slot, float yaw, float pitch) {
        LocalPlayer player = MC.player;
        if (slot < 0 || player == null || MC.gameMode == null || MC.getConnection() == null) return;
        if (!DihHandArbiter.beginHandPacketGroup(id())) return;
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
        plan = null;
        setEnabledSilently(false);
    }
}
