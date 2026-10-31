package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.mixin.accessor.DihMinecraftAccessor;
import dihclient.util.DihCombatClicker;
import dihclient.util.DihEntities;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihInputClicker;
import dihclient.util.DihInventoryClickHelper;
import dihclient.util.DihInventoryHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
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
 * The mace helper: while you are really falling far enough for a smash and a target is under your crosshair (or, with
 * Aim on Nearby, below you within vanilla reach and in sight), it presses the mace's hotbar key and clicks, the same
 * inputs a player would make, then goes back to what you held. It never turns your view or reaches further than
 * vanilla. The check runs at the start of the tick, right after Minecraft picks the crosshair target, so the swap and
 * the hit go out in that same tick. Each drop is measured from its own top ({@link MaceFall}), so a Wind Burst smash
 * that throws you back up arms the next one. With Elytra Swap it also trades your elytra for a chestplate mid-flight
 * once a target is in your sights below you, so the dive turns into a smash, and can put the elytra back on
 * afterwards for the next run. With Stun Slam, a target whose shield would stop the smash takes an axe hit and the
 * mace in the same tick: the axe disables the shield on the server before the mace hit is read, and a blocked hit deals
 * no damage, so the mace hit still counts in full.
 */
public final class AutoMaceModule extends Module {
    /** Inventory menu slot of the chest armour. */
    static final int CHEST_MENU_SLOT = 6;

    private int previousSlot = -1;
    private int swapBackIn = -1;
    private boolean struckThisFall;
    private boolean elytraOff;
    private boolean reequipNextTick;
    private final MaceFall fall = new MaceFall();

    public AutoMaceModule() {
        super("auto-mace", "AutoMace", ModuleCategory.COMBAT,
            "Swaps to your mace and smashes what you are falling onto.");
        add(new ChoiceSetting("aim", "Aim", "Nearby", "Nearby", "Crosshair")
            .group("General")
            .description("Nearby: hit a target below you in reach and in sight. Crosshair: only what you look at."));
        add(new DoubleSetting("min-fall", "Min Fall", 2.0D, 1.6D, 20.0D, 0.1D).unit("blocks")
            .group("General")
            .description("Fall at least this far before smashing; more fall, more damage."));
        add(new DoubleSetting("nearby-range", "Nearby Range", 6.0D, 1.0D, 6.0D, 0.1D).unit("blocks")
            .group("General")
            .description("How far a Nearby target may be; never beyond your reach.")
            .visibleWhen(() -> "Nearby".equals(value("aim"))));
        add(new BoolSetting("need-sight", "Need Line Of Sight", true)
            .group("General")
            .description("Only pick Nearby targets you can see.")
            .visibleWhen(() -> "Nearby".equals(value("aim"))));

        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living")
            .group("Targets")
            .description("What to smash."));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
            .group("Targets")
            .description("Skip invisible entities."));
        add(new BoolSetting("ignore-naked", "Ignore Naked", false)
            .group("Targets")
            .description("Skip players wearing no armor."));
        add(new BoolSetting("skip-blocking", "Skip Blocking", false)
            .group("Targets")
            .description("Save the smash when the target raises a shield, unless Stun Slam can break it."));
        add(new BoolSetting("skip-hurt", "Skip Hurt Targets", false)
            .group("Targets")
            .description("Save the smash while the target still flashes from a hit and takes reduced damage."));

        add(new BoolSetting("auto-swap", "Swap To Mace", true)
            .group("Swap")
            .description("Switch to a mace on your hotbar for the hit; off only smashes with a mace already in hand."));
        add(new ChoiceSetting("prefer", "Prefer", "Density", "Density", "Breach", "Wind Burst")
            .group("Swap")
            .description("Which mace to use when your hotbar holds more than one.")
            .visibleWhen(() -> bool("auto-swap")));
        add(new BoolSetting("stun-slam", "Stun Slam", true)
            .group("Swap")
            .description("Hit a raised shield with a hotbar axe, then smash through it in the same tick.")
            .visibleWhen(() -> bool("auto-swap")));
        add(new BoolSetting("swap-back", "Swap Back", true)
            .group("Swap")
            .description("Go back to the item you held after the hit.")
            .visibleWhen(() -> bool("auto-swap")));
        add(new IntSetting("swap-back-delay", "Swap Back Delay", 2, 0, 20, 1).unit("ticks")
            .group("Swap")
            .description("Ticks to keep the mace out after the hit.")
            .visibleWhen(() -> bool("auto-swap") && bool("swap-back")));

        add(new BoolSetting("elytra-swap", "Elytra Swap", false).group("Elytra")
            .description("Mid-flight, swap the elytra for a chestplate when a target is in your sights below."));
        add(new IntSetting("elytra-range", "Sight Range", 32, 8, 96, 4).unit("blocks").group("Elytra")
            .description("How far ahead along your view a target counts.")
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
        fall.reset();
    }

    @Override
    public void onGameLeft() {
        previousSlot = -1;
        swapBackIn = -1;
        struckThisFall = false;
        elytraOff = false;
        reequipNextTick = false;
        fall.reset();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        if (swapBackIn > 0 && --swapBackIn == 0) restoreSlot();
        if (bool("elytra-swap") && isFree(player)) handleElytra(player);
    }

    /**
     * Called from the head of {@code Minecraft.handleKeybinds}: the crosshair target was just picked and the player has
     * not moved yet, so the swap and click queued here are pressed in this same tick against the target checked here.
     * Queued from the end-of-tick {@link #tick()} instead, they would land a tick later, a block or two further down,
     * often past the target.
     */
    public static void beforeHandleKeybinds() {
        if (ModuleRegistry.get("auto-mace") instanceof AutoMaceModule module && module.isEnabled()) module.smash();
    }

    private void smash() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gameMode == null) return;

        boolean grounded = player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger()
            || player.isFallFlying() || player.getAbilities().flying;
        if (fall.update(player.getY(), player.getDeltaMovement().y, grounded)) struckThisFall = false;
        if (struckThisFall || !isFree(player) || !canSmash(player)) return;
        // A click Minecraft would swallow (miss cooldown, eating, blocking) must not use up this drop's smash.
        if (((DihMinecraftAccessor) MC).dih$getMissTime() > 0 || player.isUsingItem() || player.isHandsBusy()) return;

        EntityHitResult hit = target(player);
        if (hit == null) return;
        Inventory inventory = player.getInventory();
        int mace = bool("auto-swap") ? bestMace(inventory)
            : player.getMainHandItem().getItem() instanceof MaceItem ? inventory.getSelectedSlot() : -1;
        if (mace < 0) return;
        boolean stun = wantsStunSlam(player, hit);
        if (stun && !inServerReach(player, hit.getEntity())) return;
        if (!stun || !stunSlam(player, (LivingEntity) hit.getEntity(), mace)) {
            if (!DihCombatClicker.queueAttack(hit)) return; // another module owns the attack key this tick

            int selected = inventory.getSelectedSlot();
            if (mace != selected) {
                if (previousSlot < 0) previousSlot = selected;
                DihInputClicker.queueHotbarSlot(mace);
            }
        }
        struckThisFall = true;
        if (previousSlot >= 0 && bool("swap-back")) swapBackIn = Math.max(1, integer("swap-back-delay") + 1);
        // Put the elytra back once the hit has gone out, not before it.
        if (elytraOff && "After Hit".equals(choice("elytra-reequip"))) reequipNextTick = true;
    }

    /** Whether this hit should be a stun slam: the shield would block it and there's an axe to break it with. */
    private boolean wantsStunSlam(LocalPlayer player, EntityHitResult hit) {
        return bool("auto-swap") && bool("stun-slam") && !PackHideState.isHardLocked()
            && hit.getEntity() instanceof Player target && KillAuraModule.wouldBlockHit(target)
            && axeSlot(player.getInventory()) >= 0;
    }

    /**
     * The server measures reach from where it last had you, a tick behind on the way down. A plain swing can afford to
     * miss for that; a stun slam is the one swing of this drop, so it waits until the target is in reach from there.
     */
    private static boolean inServerReach(LocalPlayer player, Entity target) {
        Vec3 eyes = player.getEyePosition().subtract(player.getDeltaMovement());
        double range = player.entityInteractionRange();
        return target.getBoundingBox().distanceToSqr(eyes) <= range * range;
    }

    /** Axe, then mace, on one target in one tick. False if another module holds the hand. */
    private boolean stunSlam(LocalPlayer player, LivingEntity target, int mace) {
        Inventory inventory = player.getInventory();
        if (!DihHandArbiter.beginHandPacketGroup(id())) return false;
        try {
            if (previousSlot < 0) previousSlot = inventory.getSelectedSlot();
            DihInventoryHelper.selectHotbarSlot(MC, axeSlot(inventory));
            MC.gameMode.attack(player, target);
            DihEntities.swing(player, InteractionHand.MAIN_HAND);
            DihInventoryHelper.selectHotbarSlot(MC, mace);
            MC.gameMode.attack(player, target);
            DihEntities.swing(player, InteractionHand.MAIN_HAND);
            return true;
        } finally {
            DihHandArbiter.endHandPacketGroup(id());
        }
    }

    private static int axeSlot(Inventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(ItemTags.AXES)) return slot;
        }
        return -1;
    }

    private static boolean isFree(LocalPlayer player) {
        return MC.gui.screen() == null && player.containerMenu == player.inventoryMenu
            && player.containerMenu.getCarried().isEmpty();
    }

    /** Vanilla's smash test, with this module's own minimum fall measured over the current drop. */
    private boolean canSmash(LocalPlayer player) {
        return fall.distance(player.getY(), player.fallDistance) >= decimal("min-fall") && !player.isFallFlying()
            && !player.isPassenger() && !player.getAbilities().flying && !player.isInWater();
    }

    /** The crosshair target, or with Aim on Nearby the closest valid target below you, in reach and in sight. */
    private EntityHitResult target(LocalPlayer player) {
        if (MC.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
            && validTarget(living)) {
            return hit;
        }
        if (!"Nearby".equals(choice("aim"))) return null;

        Vec3 eyes = player.getEyePosition();
        double range = Math.min(player.entityInteractionRange(), decimal("nearby-range"));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : MC.level.getEntities(player, player.getBoundingBox().inflate(range + 1.0D))) {
            if (!(entity instanceof LivingEntity living) || !validTarget(living)) continue;
            AABB box = living.getBoundingBox();
            // Below you means their feet are not above yours; you may already be dropping through their hitbox.
            if (box.minY > player.getY() || !player.isWithinEntityInteractionRange(living, 0.0D)) continue;
            if (bool("need-sight") && !player.hasLineOfSight(living)) continue;
            double distance = box.distanceToSqr(eyes);
            if (distance > range * range) continue;
            if (distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        if (best == null) return null;
        AABB box = best.getBoundingBox();
        Vec3 point = new Vec3(Math.clamp(eyes.x, box.minX, box.maxX), Math.clamp(eyes.y, box.minY, box.maxY),
            Math.clamp(eyes.z, box.minZ, box.maxZ));
        return new EntityHitResult(best, point);
    }

    private boolean validTarget(LivingEntity living) {
        if (living == MC.player || !living.isAlive() || living.isSpectator()) return false;
        if (DihAntiBot.suppress(living) || TeamsModule.isFriendOrTeam(living)) return false;
        if (bool("ignore-invisible") && living.isInvisible()) return false;
        if (bool("ignore-naked") && living instanceof Player && CombatFilters.wearsNoArmor(living)) return false;
        if (bool("skip-blocking") && living.isBlocking()
            && !(bool("auto-swap") && bool("stun-slam") && axeSlot(MC.player.getInventory()) >= 0)) {
            return false;
        }
        if (bool("skip-hurt") && living.hurtTime > 0) return false;
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
