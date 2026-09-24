/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import baritone.util.SleepHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * The Autopilot Survival behavior — auto-sleep only.
 *
 * <p>Originally planned with four reactive watchers (eat / flee / sleep / torch), but
 * eat / flee / torch (and the master toggle) were dropped because Meteor Client already
 * provides equivalent features. This fork keeps only the auto-sleep piece, which is
 * built on Baritone's own bed cache and dovetails with the existing {@code #sleep}
 * command via the shared {@link SleepHelper}.
 *
 * <p>Death-point waypointing remains handled by {@code WaypointBehavior.onPlayerDeath()}
 * via the built-in {@code doDeathWaypoints} setting — no code needed here.
 */
public final class AutopilotBehavior extends Behavior implements AbstractGameEventListener {

    /** Set once the auto-sleep watcher has issued a goal for the current night. */
    private boolean sleepInProgress = false;

    /** Latched once a mining failsafe has fired, until mining stops. */
    private boolean mineFled = false;

    /** Whether we're currently forcing the "use" input to eat. */
    private boolean eatingHeld = false;

    /** Tracks {@code autoSleep}'s previous tick value to fire a one-time experimental warning. */
    private boolean prevAutoSleep = false;

    public AutopilotBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            // World unloading — reset state so re-entering re-fires the warning.
            this.sleepInProgress = false;
            this.prevAutoSleep = false;
            this.mineFled = false;
            setEatingHeld(false);
            return;
        }
        if (ctx.player() == null || ctx.world() == null) return;

        checkExperimentalWarning();
        tickSleep();
        tickMineGuards();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MINING GUARDS  (only while #mine is running)
    //   • low health          → #stop + mineFleeCommand (/home)
    //   • player within range  → #stop + mineFleeCommand (/home)
    //   • N stacks of one item → #stop + mineFleeCommand (deposit yourself)
    //   • low hunger           → auto-eat cooked beef from the hotbar
    // ════════════════════════════════════════════════════════════════════════

    private void tickMineGuards() {
        final boolean mining = baritone.getMineProcess() != null && baritone.getMineProcess().isActive();
        if (!mining) {
            mineFled = false;
            // The loop keeps eating while it waits out health regen, which
            // doesn't happen at all on an empty food bar.
            if (recoveryEating && ctx.player() != null) {
                tickAutoEat(ctx.player());
            } else {
                setEatingHeld(false);
            }
            return;
        }
        final LocalPlayer p = ctx.player();
        if (p == null) return;

        // Auto-eat runs independently — it doesn't stop mining.
        tickAutoEat(p);

        if (mineFled) return; // already fled this mining session

        // 1) Low health
        if (Baritone.settings().mineFleeOnLowHealth.value) {
            float hp = p.getHealth();
            if (hp > 0.0f && hp <= Baritone.settings().mineFleeHealth.value) {
                flee(String.format("Health low (%.1f hearts)", hp / 2.0f));
                return;
            }
        }
        // 1b) Hunger low — auto-eat can only reach the hotbar while mining, and
        //     at very low food you stop sprinting and stop regenerating, so go
        //     home and eat properly instead of limping on.
        int hungerFloor = Baritone.settings().mineFleeHunger.value;
        if (hungerFloor > 0 && p.getFoodData().getFoodLevel() <= hungerFloor) {
            flee("Hunger low (" + p.getFoodData().getFoodLevel() + "/20)");
            return;
        }
        // 2) Player nearby — ONLY during the unattended #minecmd loop. A plain
        //    #mine is attended, so having a passing player yank you home is more
        //    annoying than useful; the flee-on-player guard belongs to the
        //    #minecmd/#automine cycle (AutoMineBehavior), not to raw mining.
        if (Baritone.settings().mineFleeOnPlayer.value && baritone.getAutoMineBehavior().isRunning()) {
            String who = nearbyPlayerName(Baritone.settings().mineFleePlayerRadius.value);
            if (who != null) {
                flee("Player nearby: " + who);
                return;
            }
        }
        // 3) No usable tool left.
        //
        // Carrying spares is the normal case, so a single worn pickaxe is NOT a
        // reason to go home — ToolSet.getBestSlot already refuses to select a
        // tool with <= itemSaverThreshold durability left (provided itemSaver is
        // on; the mining loop turns it on and syncs the threshold at start), and
        // InventoryBehavior pulls a fresh one down from the main inventory.
        // We only flee once every matching tool is worn out.
        int durThreshold = Baritone.settings().mineFleeDurability.value;
        if (durThreshold > 0) {
            String match = Baritone.settings().mineToolMatch.value.trim().toLowerCase(Locale.ROOT);
            if (!match.isEmpty()) {
                int total = countTools(p, match, -1);
                int usable = countTools(p, match, durThreshold);
                if (total == 0) {
                    flee("No " + match + " left in inventory", true);
                    return;
                }
                if (usable == 0) {
                    flee("All " + total + " " + match + "(s) below " + durThreshold + " durability", true);
                    return;
                }
            }
        }
        // 3b) Nowhere left to put the ore.
        //
        // With seeding on, every main-inventory slot starts holding one raw_gold
        // and fills to 64, so "inventory full" is the normal end of a run rather
        // than an error. Not terminal — going home and depositing fixes it.
        if (Baritone.settings().mineFleeWhenFull.value && !hasRoomFor(p, Baritone.settings().mineFleeItem.value)) {
            flee("Inventory full of " + Baritone.settings().mineFleeItem.value);
            return;
        }
        // 4) N full stacks of a specific item (e.g. raw_gold)
        int itemStacks = Baritone.settings().mineFleeItemStacks.value;
        if (itemStacks > 0) {
            String id = Baritone.settings().mineFleeItem.value;
            int have = stacksOfItem(p, id);
            if (have >= itemStacks) {
                flee(have + " stacks of " + id + " collected");
                return;
            }
        }
    }

    /**
     * Count damageable items whose registry path contains {@code match}.
     *
     * @param minRemaining only count tools with strictly more than this much
     *                     durability left; pass -1 to count every one of them
     */
    private int countTools(LocalPlayer p, String match, int minRemaining) {
        int n = 0;
        try {
            for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
                if (st == null || st.isEmpty() || !st.isDamageableItem()) continue;
                String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().toLowerCase(Locale.ROOT);
                if (!path.contains(match)) continue;
                if (minRemaining < 0 || (st.getMaxDamage() - st.getDamageValue()) > minRemaining) {
                    n++;
                }
            }
        } catch (Throwable ignored) {}
        return n;
    }

    /**
     * Can another {@code itemPath} still be picked up? True if any slot is empty
     * or any existing stack of it has room. Covers the hotbar as well as the main
     * inventory, since dropped items land wherever there is space.
     */
    private boolean hasRoomFor(LocalPlayer p, String itemPath) {
        try {
            for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
                if (st == null || st.isEmpty()) {
                    return true;
                }
                if (st.getCount() < st.getMaxStackSize()
                        && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equalsIgnoreCase(itemPath)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return true; // never flee on a lookup failure
        }
    }

    /** Per-tool durability line for {@code #start status}. */
    public String toolReport() {
        LocalPlayer p = ctx.player();
        if (p == null) return "no player";
        String match = Baritone.settings().mineToolMatch.value.trim().toLowerCase(Locale.ROOT);
        int threshold = Baritone.settings().mineFleeDurability.value;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
            if (st == null || st.isEmpty() || !st.isDamageableItem()) continue;
            String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().toLowerCase(Locale.ROOT);
            if (!path.contains(match)) continue;
            int left = st.getMaxDamage() - st.getDamageValue();
            if (n > 0) sb.append(", ");
            sb.append(path).append(' ').append(left).append(left > threshold ? " ok" : " WORN");
            n++;
        }
        return n == 0 ? "no " + match + " found" : n + " found: " + sb;
    }

    /** Full-(64)-stack count of the item whose registry path equals {@code itemPath}. */
    private int stacksOfItem(LocalPlayer p, String itemPath) {
        try {
            int total = 0;
            for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
                if (st == null || st.isEmpty()) continue;
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equalsIgnoreCase(itemPath)) {
                    total += st.getCount();
                }
            }
            return total / 64;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Set on each flee so {@link AutoMineBehavior} can pick the cycle up where
     * the guards left off. Consumed (and cleared) by {@link #consumeFleeEvent()}.
     */
    private boolean fleeEvent = false;
    private String fleeReason = "";
    /** True when the flee reason cannot fix itself by going home (no usable tool). */
    private boolean fleeTerminal = false;

    /** True exactly once per flee. */
    public boolean consumeFleeEvent() {
        boolean f = fleeEvent;
        fleeEvent = false;
        return f;
    }

    public String lastFleeReason() {
        return fleeReason;
    }

    /**
     * Whether the last flee was terminal — a condition that resting and
     * depositing cannot clear, so the caller must stop rather than start another
     * cycle. Running out of usable pickaxes is the case that matters: the loop
     * would otherwise teleport out, instantly re-trip the durability guard, come
     * home, and repeat forever.
     */
    public boolean lastFleeWasTerminal() {
        return fleeTerminal;
    }

    /**
     * Keep auto-eating even though we aren't mining — used while the loop waits
     * for health to regenerate, which needs a full food bar to happen at all.
     */
    public void setRecoveryEating(boolean on) {
        this.recoveryEating = on;
        if (!on) {
            setEatingHeld(false);
        }
    }

    private boolean recoveryEating = false;

    private void flee(String reason) {
        flee(reason, false);
    }

    private void flee(String reason, boolean terminal) {
        mineFled = true;
        fleeEvent = true;
        fleeReason = reason;
        fleeTerminal = terminal;
        setEatingHeld(false);
        final String cmd = Baritone.settings().mineFleeCommand.value;
        logHelper("⚠ " + reason + " while mining — stopping and running " + cmd);
        baritone.getPathingBehavior().cancelEverything();
        runFleeCommand(cmd);
    }

    /** Name of the nearest OTHER player within {@code radius}, or {@code null}. */
    private String nearbyPlayerName(double radius) {
        try {
            final double r2 = radius * radius;
            final LocalPlayer self = ctx.player();
            for (Player pl : ctx.world().players()) {
                if (pl == self || pl == null) continue;
                if (pl.distanceToSqr(self) <= r2) return pl.getName().getString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ── Auto-eat (best effort: the input handler suppresses "use" while actively
    //    breaking a block, so eating happens in the gaps between breaks) ────────

    private void tickAutoEat(LocalPlayer p) {
        if (!Baritone.settings().mineAutoEat.value) {
            setEatingHeld(false);
            return;
        }
        // While mining we only top up enough to keep sprinting. While recovering
        // we have to clear the vanilla health-regen threshold (food >= 18) or
        // health never comes back and the loop waits forever.
        final int food = p.getFoodData().getFoodLevel();
        final boolean enough = recoveryEating
                ? food >= Baritone.settings().autoMineResumeFood.value
                : food > Baritone.settings().mineAutoEatHunger.value;
        if (enough) {
            setEatingHeld(false);
            return;
        }
        int slot = hotbarSlotOf(p, Items.COOKED_BEEF);
        if (slot < 0) slot = hotbarSlotOf(p, Items.BEEF);
        if (slot < 0) {
            // Nothing on the hotbar — pull a stack down from the main inventory.
            // Only while recovering: we're stationary then, and an inventory move
            // mid-mining would fight the block-breaking input.
            if (recoveryEating && pullFoodToHotbar(p)) {
                return; // swap requested; it lands within a tick or two
            }
            setEatingHeld(false); // no beef anywhere we can reach
            return;
        }
        p.getInventory().setSelectedSlot(slot);
        setEatingHeld(true); // hold "use" to eat
    }

    private int hotbarSlotOf(LocalPlayer p, Item item) {
        var items = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9 && i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (st != null && !st.isEmpty() && st.getItem() == item) return i;
        }
        return -1;
    }

    /**
     * Holds the real vanilla "use" key down.
     *
     * <p>Baritone's own {@code Input.CLICK_RIGHT} cannot eat. It routes to
     * {@link baritone.utils.BlockPlaceHelper}, which returns early unless the
     * crosshair is on a {@code HitResult.Type.BLOCK}, and even when it does fire
     * {@code processRightClick} the eat is cancelled a tick later: this fork has
     * no keybind mixin, so {@code Minecraft.handleKeybinds()} sees
     * {@code options.keyUse} up and calls {@code releaseUsingItem}. Eating takes
     * ~32 continuous ticks, so it never completes — the bot just holds the food.
     *
     * <p>Driving {@code keyUse} directly is the vanilla path: it both starts the
     * use and keeps {@code releaseUsingItem} from firing.
     *
     * <p>Re-applied every tick rather than only on transitions, because
     * {@code KeyMapping.releaseAll()} runs on screen open/close and would
     * silently drop our held state mid-meal.
     */
    private void setEatingHeld(boolean held) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                if (held) {
                    // Hold the real "use" key down, re-applied every tick (see
                    // above — KeyMapping.releaseAll() on screen open/close would
                    // otherwise drop it mid-meal), and keep the old food-incapable
                    // Baritone path disarmed while we do.
                    mc.options.keyUse.setDown(true);
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
                } else if (eatingHeld) {
                    // Release the key ONLY on the transition out of eating, and only
                    // because we were the ones holding it. Slamming keyUse back to
                    // "up" on every idle tick is what stopped you from manually
                    // eating or drawing a bow while the mod is on — so when we
                    // aren't eating, we now leave the use key entirely alone and
                    // your own right-clicks come through.
                    mc.options.keyUse.setDown(false);
                }
            }
        } catch (Throwable ignored) {}
        eatingHeld = held;
    }

    /** Release the use key no matter what — called on stop and on world unload. */
    public void releaseEating() {
        setEatingHeld(false);
    }

    /**
     * Move a stack of beef from the main inventory down to the hotbar, because
     * {@link #hotbarSlotOf} only scans slots 0-8 and the eat path can only
     * select a hotbar slot.
     */
    private boolean pullFoodToHotbar(LocalPlayer p) {
        var items = p.getInventory().getNonEquipmentItems();
        for (int i = 9; i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (st == null || st.isEmpty()) continue;
            if (st.getItem() == Items.COOKED_BEEF || st.getItem() == Items.BEEF) {
                try {
                    return baritone.getInventoryBehavior().attemptToPutOnHotbar(i, x -> false);
                } catch (Throwable t) {
                    return false;
                }
            }
        }
        return false;
    }

    /** Runs the flee command: {@code /x} → server command, {@code #x} → Baritone command. */
    private void runFleeCommand(String raw) {
        try {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) return;
            if (c.startsWith("#")) {
                baritone.getCommandManager().execute(c.substring(1));
            } else {
                // Server command (Essentials /home etc.) — sendCommand takes it without the slash.
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand(c.startsWith("/") ? c.substring(1) : c);
                }
            }
        } catch (Throwable t) {
            logHelper("Failed to run flee command '" + raw + "': " + t.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Experimental-setting warning (one-time, on false→true transition)
    //  Mirrors the elytraWaveMode / elytraConserveFireworks pattern.
    // ════════════════════════════════════════════════════════════════════════

    private void checkExperimentalWarning() {
        final boolean curAutoSleep = Baritone.settings().autoSleep.value;
        if (curAutoSleep && !prevAutoSleep) {
            logHelper("⚠ autoSleep is EXPERIMENTAL. Requires a previously-cached bed; will not "
                    + "search beyond the disk cache. Will not interrupt active tasks unless "
                    + "autoSleepInterruptTasks=true. Disable with #autosleep off.");
        }
        prevAutoSleep = curAutoSleep;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-SLEEP
    // ════════════════════════════════════════════════════════════════════════

    private void tickSleep() {
        if (!Baritone.settings().autoSleep.value) {
            sleepInProgress = false;
            return;
        }
        if (ctx.player().isSleeping()) {
            sleepInProgress = false; // already asleep, done for the night
            return;
        }
        if (!SleepHelper.isNightOrStorm(ctx.world())) return;

        // Don't yank an active task unless explicitly allowed
        if (!Baritone.settings().autoSleepInterruptTasks.value) {
            if (baritone.getPathingControlManager().mostRecentInControl().isPresent()) return;
        }

        if (sleepInProgress) return; // already navigating to a bed

        Optional<BlockPos> bed = SleepHelper.findNearestBed(ctx);
        if (bed.isEmpty()) return; // no bed cached — silently skip

        sleepInProgress = true;
        BlockPos b = bed.get();
        logHelper("Night detected. Navigating to bed at X=" + b.getX()
                + " Y=" + b.getY() + " Z=" + b.getZ() + ".");
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(b, 1));
        // Right-click on arrival is intentionally not automated here — use the explicit
        // #sleep command if you want that. This watcher only handles navigation.
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Public read-only accessor
    // ════════════════════════════════════════════════════════════════════════

    public boolean isSleepInProgress() { return sleepInProgress; }

    private void logHelper(String msg) {
        Helper.HELPER.logDirect("[Autopilot] " + msg);
    }
}
