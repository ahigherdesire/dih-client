package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihInputClicker;
import dihclient.util.DihInventoryClickHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The legit mace: while you are really falling far enough for a smash and your crosshair is on a target, it presses
 * the mace's hotbar key and clicks, the same inputs a player would make, then goes back to what you held. It never
 * turns your view or reaches further than vanilla. With Elytra Swap it also trades your elytra for a chestplate
 * mid-flight once a target is in your sights below you, so the dive turns into a smash, and can put the elytra back
 * on afterwards for the next run. Wind Burst maces chain on their own: each smash launches you up for another fall.
 */
public final class AutoMaceModule extends Module {
    /** Inventory menu slot of the chest armour. */
    static final int CHEST_MENU_SLOT = 6;

    private int previousSlot = -1;
    private int swapBackIn = -1;
    private boolean struckThisFall;
    private boolean elytraOff;
    private boolean reequipNextTick;

    public AutoMaceModule() {
        super("auto-mace", "AutoMace", ModuleCategory.COMBAT,
            "Swaps to your mace and smashes what you are falling onto.");
        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living"));
        add(new DoubleSetting("min-fall", "Min Fall", 2.0D, 1.6D, 20.0D, 0.1D).unit("blocks")
            .description("Fall at least this far before smashing; more fall, more damage."));
        add(new ChoiceSetting("prefer", "Prefer", "Density", "Density", "Breach", "Wind Burst")
            .description("Which mace to use when your hotbar holds more than one."));
        add(new BoolSetting("swap-back", "Swap Back", true).description("Go back to the item you held after the hit."));
        add(new IntSetting("swap-back-delay", "Swap Back Delay", 2, 0, 20, 1).unit("ticks")
            .visibleWhen(() -> bool("swap-back")));

        add(new BoolSetting("elytra-swap", "Elytra Swap", false).group("Elytra")
            .description("Mid-flight, swap the elytra for a chestplate when a target is in your sights below."));
        add(new IntSetting("elytra-range", "Sight Range", 32, 8, 96, 4).unit("blocks").group("Elytra")
            .visibleWhen(() -> bool("elytra-swap")));
        add(new IntSetting("elytra-drop", "Min Height Above", 6, 2, 40, 1).unit("blocks").group("Elytra")
            .description("Only drop on targets at least this far below you.")
            .visibleWhen(() -> bool("elytra-swap")));
        add(new ChoiceSetting("elytra-reequip", "Re-equip Elytra", "After Hit", "Off", "After Hit", "On Landing")
            .group("Elytra").description("Put the elytra back on for the next run.")
            .visibleWhen(() -> bool("elytra-swap")));
    }

    @Override
    public void onDisable() {
        restoreSlot();
        struckThisFall = false;
        elytraOff = false;
        reequipNextTick = false;
    }

    @Override
    public void onGameLeft() {
        previousSlot = -1;
        swapBackIn = -1;
        struckThisFall = false;
        elytraOff = false;
        reequipNextTick = false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (swapBackIn > 0 && --swapBackIn == 0) restoreSlot();

        boolean free = MC.gui.screen() == null && player.containerMenu == player.inventoryMenu
            && player.containerMenu.getCarried().isEmpty();
        if (free && bool("elytra-swap")) handleElytra(player);

        if (player.onGround() || player.fallDistance < decimal("min-fall")) struckThisFall = false;
        if (!free || struckThisFall || !canSmash(player)) return;

        LivingEntity target = crosshairTarget();
        if (target == null) return;
        Inventory inventory = player.getInventory();
        int mace = bestMace(inventory);
        if (mace < 0) return;

        int selected = inventory.getSelectedSlot();
        if (mace != selected) {
            if (previousSlot < 0) previousSlot = selected;
            DihInputClicker.queueHotbarSlot(mace);
        }
        DihInputClicker.queueAttackClick();
        struckThisFall = true;
        if (previousSlot >= 0 && bool("swap-back")) swapBackIn = Math.max(1, integer("swap-back-delay") + 1);
        // Put the elytra back once the hit has gone out, not before it.
        if (elytraOff && "After Hit".equals(choice("elytra-reequip"))) reequipNextTick = true;
    }

    /** Vanilla's smash test, with this module's own minimum fall. */
    private boolean canSmash(LocalPlayer player) {
        return player.fallDistance >= decimal("min-fall") && !player.isFallFlying() && !player.isPassenger()
            && !player.getAbilities().flying && !player.isInWater();
    }

    private LivingEntity crosshairTarget() {
        if (!(MC.hitResult instanceof EntityHitResult hit)) return null;
        Entity entity = hit.getEntity();
        return entity instanceof LivingEntity living && validTarget(living) ? living : null;
    }

    private boolean validTarget(LivingEntity living) {
        if (living == MC.player || !living.isAlive() || living.isSpectator()) return false;
        if (DihAntiBot.suppress(living) || TeamsModule.isFriendOrTeam(living)) return false;
        return switch (choice("targets")) {
            case "All Living" -> true;
            case "Players + Hostile" -> living instanceof Player || living instanceof Enemy;
            default -> living instanceof Player;
        };
    }

    /** Hotbar slot of the mace with the most of the preferred enchantment, or -1. */
    private int bestMace(Inventory inventory) {
        String wanted = switch (choice("prefer")) {
            case "Breach" -> "breach";
            case "Wind Burst" -> "wind_burst";
            default -> "density";
        };
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof MaceItem)) continue;
            int score = enchantLevel(stack, wanted) * 100 + stack.getEnchantments().size();
            if (slot == inventory.getSelectedSlot()) score++; // a tie keeps what you hold
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    private static int enchantLevel(ItemStack stack, String path) {
        for (var entry : stack.getEnchantments().entrySet()) {
            Optional<String> id = entry.getKey().unwrapKey().map(key -> key.identifier().getPath());
            if (id.isPresent() && id.get().equals(path)) return entry.getIntValue();
        }
        return 0;
    }

    private void restoreSlot() {
        swapBackIn = -1;
        if (previousSlot < 0) return;
        LocalPlayer player = MC.player;
        if (player != null && MC.gui.screen() == null && player.getInventory().getSelectedSlot() != previousSlot) {
            DihInputClicker.queueHotbarSlot(previousSlot);
        }
        previousSlot = -1;
    }

    // ---- Elytra swap -------------------------------------------------------------------------------------------

    private void handleElytra(LocalPlayer player) {
        if (elytraOff) {
            if (hasElytraWorn(player)) {
                elytraOff = false; // put back by hand
            } else if (reequipNextTick || player.onGround()) {
                // After Hit puts it back after the smash, and on landing too if the hit never came.
                if ("Off".equals(choice("elytra-reequip"))) elytraOff = false;
                else reequipElytra(player);
            }
            reequipNextTick = false;
            return;
        }
        if (!player.isFallFlying() || !hasElytraWorn(player)) return;
        LivingEntity target = sightedBelow(player);
        if (target == null) return;
        if (swapChest(player, bestChestplate(player.getInventory()))) elytraOff = true;
    }

    private static boolean hasElytraWorn(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
    }

    /** A valid target your view ray passes, at least the set height below you. */
    private LivingEntity sightedBelow(LocalPlayer player) {
        double range = integer("elytra-range");
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(player.getLookAngle().scale(range));
        AABB sweep = new AABB(eyes, end).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : MC.level.getEntities(player, sweep)) {
            if (!(entity instanceof LivingEntity living) || !validTarget(living)) continue;
            if (player.getY() - living.getY() < integer("elytra-drop")) continue;
            Optional<Vec3> hit = living.getBoundingBox().inflate(0.5D).clip(eyes, end);
            if (hit.isEmpty()) continue;
            double distance = hit.get().distanceToSqr(eyes);
            if (distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Inventory index (0-35) of the strongest chestplate that is not a glider, or -1. */
    static int bestChestplate(Inventory inventory) {
        int best = -1;
        double bestArmor = -1.0D;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.slot() != EquipmentSlot.CHEST || stack.has(DataComponents.GLIDER)) continue;
            double armor = armorOf(stack);
            if (armor > bestArmor) {
                best = i;
                bestArmor = armor;
            }
        }
        return best;
    }

    private static double armorOf(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0.0D;
        return modifiers.compute(Attributes.ARMOR, 0.0D, EquipmentSlot.CHEST)
            + modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0D, EquipmentSlot.CHEST) * 0.5D;
    }

    private void reequipElytra(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).has(DataComponents.GLIDER)) {
                if (swapChest(player, i)) elytraOff = false;
                return;
            }
        }
        elytraOff = false; // the elytra is gone (dropped, broken); nothing to put back
    }

    /**
     * Swaps the chest slot with inventory index {@code index}: one number-key swap from the hotbar, or pick up, place
     * and put back for the main inventory. With no index, shift-clicks the chest item into the inventory.
     */
    private static boolean swapChest(LocalPlayer player, int index) {
        if (index < 0) return DihInventoryClickHelper.click(MC, CHEST_MENU_SLOT, 0, ContainerInput.QUICK_MOVE);
        if (index < 9) return DihInventoryClickHelper.click(MC, CHEST_MENU_SLOT, index, ContainerInput.SWAP);
        int menuSlot = menuSlotOf(index);
        return DihInventoryClickHelper.click(MC, menuSlot, 0, ContainerInput.PICKUP)
            && DihInventoryClickHelper.click(MC, CHEST_MENU_SLOT, 0, ContainerInput.PICKUP)
            && DihInventoryClickHelper.click(MC, menuSlot, 0, ContainerInput.PICKUP);
    }

    /** Inventory index to the player inventory menu's slot id: hotbar 0-8 sits at 36-44, the rest keep their number. */
    static int menuSlotOf(int index) {
        return index < 9 ? 36 + index : index;
    }
}
