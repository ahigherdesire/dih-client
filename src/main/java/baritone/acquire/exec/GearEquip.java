package baritone.acquire.exec;

import baritone.api.utils.IPlayerContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

/** Equips planned gear with the same inventory clicks used by AutoArmorModule. One click per tick. */
final class GearEquip {
    private static final List<String> ARMOR = List.of(
            "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots");
    private static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private GearEquip() {}

    static boolean tick(IPlayerContext ctx) {
        Player player = ctx.player();
        if (player == null || !InventoryOps.inventoryMenuOpen(player)) return false;
        for (int i = 0; i < ARMOR.size(); i++) {
            if (!player.getItemBySlot(SLOTS.get(i)).isEmpty()) continue;
            String wanted = ARMOR.get(i);
            int index = InventoryReader.find(player, stack -> wanted.equals(InventoryReader.idOf(stack)));
            if (index < 0) continue;
            InventoryOps.quickMove(ctx, player.inventoryMenu.containerId, InventoryOps.inventoryMenuSlot(index));
            return true;
        }
        if (player.getOffhandItem().isEmpty()) {
            int index = InventoryReader.find(player, stack -> "minecraft:shield".equals(InventoryReader.idOf(stack)));
            if (index >= 0) {
                ctx.playerController().windowClick(player.inventoryMenu.containerId,
                        InventoryOps.inventoryMenuSlot(index), 40, ContainerInput.SWAP, player);
                return true;
            }
        }
        return false;
    }
}
