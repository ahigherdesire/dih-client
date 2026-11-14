package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RegistryListSetting;
import dihclient.util.DihInputClicker;
import dihclient.util.RegistryListCodec;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Eats from the hotbar when hungry, and reaches for golden apples or healing potions when health runs low. Ported
 * from LiquidBounce's SmartEat (GPL-3.0, CCBlueX) AutoEat and its food ranking (most urgent first, then most filling,
 * then nearest). Changed here: golden apples are kept for when health is low instead of being eaten as plain food,
 * foods with bad side effects are never picked, low health alone can start a golden apple, and the slot switches back
 * afterwards. LiquidBounce's silent-offhand eating is not ported.
 */
public final class SmartEatModule extends Module {
    /** Gives up on one bite after this many ticks (the slowest food takes 32, a potion 32). */
    private static final int BITE_TIMEOUT = 60;

    enum Kind { FOOD, GOLDEN_APPLE, NOTCH_APPLE, HEALING_POTION }

    /** A hotbar food, how urgent it is (lower health threshold first), and how much hunger it restores. */
    record Candidate(int slot, Kind kind, int threshold, int nutrition) {
    }

    /** Foods that poison, nauseate or teleport; a holder so the ranking can be tested without item registries. */
    private static final class Avoid {
        static final Set<net.minecraft.world.item.Item> ITEMS = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE,
            Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.CHICKEN);
    }

    private boolean eating;
    private int foodSlot = -1;
    private int previousSlot = -1;
    private int biteTicks;
    private boolean started;
    private int hurtPause;

    public SmartEatModule() {
        super("smart-eat", "SmartEat", ModuleCategory.PLAYER, "Eats when hungry and uses golden apples when hurt.");
        add(new IntSetting("min-hunger", "Min Hunger", 15, 0, 20, 1)
            .group("General")
            .description("Eat below this hunger (20 is full)."));
        add(new ChoiceSetting("prefer", "Prefer", "Most Filling", "Most Filling", "Nearest Slot")
            .group("General")
            .description("Among plain foods, the most filling one or the one closest to your slot"));
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("General")
            .description("Return to the slot you had."));
        add(new BoolSetting("save-golden-apples", "Save Golden Apples", true)
            .group("General")
            .description("Keeps golden apples for low health instead of eating them as food"));
        add(new BoolSetting("avoid-bad-food", "Avoid Bad Food", true)
            .group("General")
            .description("Never eats rotten flesh, spider eyes, raw chicken, chorus fruit and the like"));
        add(RegistryListSetting.items("blacklist", "Blacklist", "")
            .group("General")
            .description("Foods it never eats"));

        add(new BoolSetting("golden-apples", "Golden Apples", true)
            .group("Healing")
            .description("Eats golden apples when health is low"));
        add(new DoubleSetting("gapple-health", "Golden Apple Health", 9.0D, 0.0D, 20.0D, 0.5D)
            .group("Healing")
            .description("Eat golden apples at or below this health (20 is full).")
            .visibleWhen(() -> bool("golden-apples")));
        add(new BoolSetting("notch-apples", "Notch Apples", true)
            .group("Healing")
            .description("Eats enchanted golden apples when health is very low"));
        add(new DoubleSetting("notch-health", "Notch Apple Health", 4.0D, 0.0D, 20.0D, 0.5D)
            .group("Healing")
            .description("Eat enchanted golden apples at or below this health.")
            .visibleWhen(() -> bool("notch-apples")));
        add(new BoolSetting("healing-potions", "Healing Potions", true)
            .group("Healing")
            .description("Drinks Instant Health potions when health is low"));
        add(new BoolSetting("regen-potions", "Regeneration Potions", false)
            .group("Healing")
            .description("Also drinks Regeneration potions at the same health")
            .visibleWhen(() -> bool("healing-potions")));
        add(new DoubleSetting("potion-health", "Potion Health", 8.0D, 0.0D, 20.0D, 0.5D)
            .group("Healing")
            .description("Drink healing potions at or below this health.")
            .visibleWhen(() -> bool("healing-potions")));
        add(new BoolSetting("skip-if-regenerating", "Skip If Regenerating", false)
            .group("Healing")
            .description("Saves apples and potions while you already have Regeneration"));

        add(new IntSetting("hurt-pause", "Hurt Pause", 0, 0, 100, 1).unit("ticks")
            .group("Timing")
            .description("Don't start eating this soon after taking damage; 0 eats any time."));
        add(new IntSetting("bite-timeout", "Bite Timeout", BITE_TIMEOUT, 40, 200, 5).unit("ticks")
            .group("Timing")
            .description("Gives up on a bite that takes longer than this"));
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public void onGameLeft() {
        stop();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null) return;
        if (player.hurtTime > 0) hurtPause = integer("hurt-pause");
        else if (hurtPause > 0) hurtPause--;

        if (eating) {
            Inventory inventory = player.getInventory();
            if (player.isUsingItem()) started = true;
            boolean bitten = started && !player.isUsingItem();
            if (inventory.getSelectedSlot() != foodSlot || ++biteTicks > integer("bite-timeout")
                || MC.gui.screen() != null) {
                stop();
            } else if (bitten) {
                // The bite finished: carry on with the next food if still needed.
                Candidate next = pick(player);
                if (next == null) stop();
                else begin(player, next);
            } else {
                DihInputClicker.setUseHeld(true);
            }
            return;
        }
        if (MC.gui.screen() != null || player.isUsingItem() || hurtPause > 0 || player.isCreative()) return;
        Candidate best = pick(player);
        if (best == null) return;
        previousSlot = player.getInventory().getSelectedSlot();
        begin(player, best);
    }

    private void begin(LocalPlayer player, Candidate candidate) {
        player.getInventory().setSelectedSlot(candidate.slot());
        foodSlot = candidate.slot();
        biteTicks = 0;
        started = false;
        eating = true;
        DihInputClicker.setUseHeld(true);
    }

    private void stop() {
        if (!eating) return;
        eating = false;
        DihInputClicker.setUseHeld(false);
        LocalPlayer player = MC.player;
        if (player != null && bool("switch-back") && previousSlot >= 0 && previousSlot != foodSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        foodSlot = -1;
    }

    /** The food to eat now, or null when nothing is needed. */
    private Candidate pick(LocalPlayer player) {
        float health = player.getHealth();
        boolean hungry = player.getFoodData().getFoodLevel() < integer("min-hunger");
        List<Candidate> candidates = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            Candidate candidate = candidate(slot, inventory.getItem(slot), health,
                bool("skip-if-regenerating") && player.hasEffect(MobEffects.REGENERATION));
            if (candidate != null) candidates.add(candidate);
        }
        return best(candidates, hungry, inventory.getSelectedSlot(), "Nearest Slot".equals(choice("prefer")));
    }

    private boolean blacklisted(ItemStack stack) {
        List<String> blacklist = list("blacklist");
        if (blacklist.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String entry : blacklist) {
            if (RegistryListCodec.matches(id, entry)) return true;
        }
        return false;
    }

    private Candidate candidate(int slot, ItemStack stack, float health, boolean regenerating) {
        if (stack.isEmpty() || blacklisted(stack)) return null;
        if (stack.is(Items.POTION)) {
            if (!bool("healing-potions") || regenerating || health > decimal("potion-health")) return null;
            PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
            if (potion == null) return null;
            for (MobEffectInstance effect : potion.getAllEffects()) {
                if (effect.is(MobEffects.INSTANT_HEALTH)
                    || bool("regen-potions") && effect.is(MobEffects.REGENERATION)) {
                    return new Candidate(slot, Kind.HEALING_POTION, (int) decimal("potion-health"), 0);
                }
            }
            return null;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || bool("avoid-bad-food") && Avoid.ITEMS.contains(stack.getItem())) return null;
        boolean saveApples = bool("save-golden-apples");
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            if (bool("notch-apples") && !regenerating && health <= decimal("notch-health")) {
                return new Candidate(slot, Kind.NOTCH_APPLE, (int) decimal("notch-health"), food.nutrition());
            }
            return saveApples ? null : new Candidate(slot, Kind.FOOD, 20, food.nutrition());
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            if (bool("golden-apples") && !regenerating && health <= decimal("gapple-health")) {
                return new Candidate(slot, Kind.GOLDEN_APPLE, (int) decimal("gapple-health"), food.nutrition());
            }
            return saveApples ? null : new Candidate(slot, Kind.FOOD, 20, food.nutrition());
        }
        return new Candidate(slot, Kind.FOOD, 20, food.nutrition());
    }

    /**
     * Picks among hotbar foods: anything for low health always qualifies; plain food only when hungry. The most urgent
     * (lowest health threshold) wins, then the most filling, then the slot nearest the selected one.
     */
    static Candidate best(List<Candidate> candidates, boolean hungry, int selectedSlot) {
        return best(candidates, hungry, selectedSlot, false);
    }

    /** As {@link #best(List, boolean, int)}; with {@code nearestFirst} the nearest slot beats the most filling. */
    static Candidate best(List<Candidate> candidates, boolean hungry, int selectedSlot, boolean nearestFirst) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.kind() == Kind.FOOD && !hungry) continue;
            if (best == null || better(candidate, best, selectedSlot, nearestFirst)) best = candidate;
        }
        return best;
    }

    private static boolean better(Candidate a, Candidate b, int selectedSlot, boolean nearestFirst) {
        if (a.threshold() != b.threshold()) return a.threshold() < b.threshold();
        int distanceA = Math.abs(a.slot() - selectedSlot);
        int distanceB = Math.abs(b.slot() - selectedSlot);
        if (nearestFirst && distanceA != distanceB) return distanceA < distanceB;
        if (a.nutrition() != b.nutrition()) return a.nutrition() > b.nutrition();
        return distanceA < distanceB;
    }
}
