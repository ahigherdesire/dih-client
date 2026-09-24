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
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import baritone.process.MenuClickProcess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * {@code #start minecmd} — the unattended mining cycle.
 *
 * <pre>
 *   TRAVEL       run autoMineTravelCommand (/rtpmenu), click through the menu
 *                chain (heart of the sea -> badlands)
 *   TRAVEL_WAIT  wait for the teleport to actually land
 *   MINING       #mine autoMineTarget, with the existing AutopilotBehavior
 *                guards live (low health / player nearby / durability / stacks)
 *   FLEE_WAIT    a guard fired and ran mineFleeCommand (/home) — wait to arrive
 *   RECOVER      eat and wait for health + food to come back up
 *   DEPOSIT      path to the nearest chest, shift-click the loot in, close
 *                -> back to TRAVEL, forever, until #stop
 * </pre>
 *
 * <p>This is a {@link Behavior}, not a process, on purpose: it supervises other
 * processes ({@code MineProcess}, {@code GetToBlockProcess},
 * {@code MenuClickProcess}) rather than competing with them for pathing
 * control, and a low-priority process would simply never be ticked while one of
 * those held control.
 *
 * <p>Every phase has a deadline. Nothing here blocks forever — a phase that
 * times out either retries the cycle or aborts the loop with a chat message, so
 * a bad matcher or a missing chest cannot leave the bot wedged.
 */
public final class AutoMineBehavior extends Behavior implements Helper {

    public enum Phase {
        IDLE,
        TRAVEL,
        TRAVEL_WAIT,
        MINING,
        FLEE_WAIT,
        RECOVER,
        DEPOSIT_GOTO,
        DEPOSIT_MOVE,
        DEPOSIT_SEED
    }

    /** Ticks before a phase gives up. 20 ticks = 1 second. */
    private static final int TRAVEL_TIMEOUT = 600;   // 30s for the menu chain + tp
    private static final int RECOVER_TIMEOUT = 12000; // 10 min of regen
    private static final int DEPOSIT_TIMEOUT = 2400;  // 2 min to find + reach a chest
    private static final int MINE_START_GRACE = 200;  // 10s for MineProcess to spin up

    private Phase phase = Phase.IDLE;
    private int timer;
    private Vec3 anchor = Vec3.ZERO;
    private int cycles;
    private int depositCooldown;
    private boolean depositOnly;
    private boolean pendingStop;
    private String pendingStopReason;
    private int seedSourceSlot = -1;
    private boolean seedReturned;

    public AutoMineBehavior(Baritone baritone) {
        super(baritone);
    }

    // ── Control ───────────────────────────────────────────────────────────────

    public void start() {
        cycles = 0;
        depositOnly = false;
        pendingStop = false;
        pendingStopReason = null;
        syncToolSaver();
        logDirect("Mining loop started. It will run until #stop.", ChatFormatting.GREEN);
        logDirect("Tools: " + baritone.getAutopilotBehavior().toolReport(), ChatFormatting.GRAY);
        enter(Phase.TRAVEL);
    }

    /**
     * Rotating between spare pickaxes only works if {@code itemSaver} is on —
     * that flag is what makes {@code ToolSet.getBestSlot} refuse a tool with
     * {@code <= itemSaverThreshold} durability left and pick a fresh one instead.
     * It ships off by default, so the loop turns it on and points the threshold
     * at {@code mineFleeDurability} so the "don't use it" and "go home" limits
     * can't drift apart.
     */
    private void syncToolSaver() {
        int floor = Baritone.settings().mineFleeDurability.value;
        if (floor <= 0) {
            return;
        }
        boolean changed = false;
        if (!Baritone.settings().itemSaver.value) {
            Baritone.settings().itemSaver.value = true;
            changed = true;
        }
        if (!Baritone.settings().itemSaverThreshold.value.equals(floor)) {
            Baritone.settings().itemSaverThreshold.value = floor;
            changed = true;
        }
        if (changed) {
            logDirect("Enabled itemSaver at " + floor
                    + " so worn " + Baritone.settings().mineToolMatch.value
                    + "s are skipped in favour of spares.", ChatFormatting.GRAY);
        }
    }

    /**
     * Run only the deposit legs (find chest -> deposit -> seed) once, then stop.
     * Used by {@code #testdeposit} to exercise that half without teleporting or
     * mining first.
     */
    public void startDepositOnly() {
        cycles = 0;
        depositOnly = true;
        logDirect("Deposit test: finding the nearest "
                + Baritone.settings().autoMineDepositBlock.value + "...", ChatFormatting.GREEN);
        enter(Phase.DEPOSIT_GOTO);
    }

    /** Where to go once the deposit legs finish — next cycle, or stop if testing. */
    private void afterDeposit() {
        if (depositOnly) {
            stop("deposit test complete");
        } else {
            enter(Phase.TRAVEL);
        }
    }

    public void stop(String why) {
        if (phase == Phase.IDLE) {
            return;
        }
        phase = Phase.IDLE;
        baritone.getAutopilotBehavior().setRecoveryEating(false);
        baritone.getAutopilotBehavior().releaseEating(); // never leave "use" held down
        baritone.getMenuClickProcess().cancel();
        baritone.getMineProcess().cancel();
        baritone.getGetToBlockProcess().onLostControl();
        logDirect("Mining loop stopped" + (why == null ? "." : ": " + why), ChatFormatting.YELLOW);
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public Phase phase() {
        return phase;
    }

    public int cycles() {
        return cycles;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            if (event.getType() == TickEvent.Type.OUT) {
                phase = Phase.IDLE; // left the world
                baritone.getAutopilotBehavior().releaseEating();
            }
            return;
        }
        if (phase == Phase.IDLE) {
            return;
        }
        final LocalPlayer p = ctx.player();
        if (p == null || ctx.world() == null) {
            return;
        }
        if (p.isDeadOrDying()) {
            stop("you died");
            return;
        }
        if (depositCooldown > 0) {
            depositCooldown--;
        }
        timer++;

        switch (phase) {
            case TRAVEL -> tickTravel();
            case TRAVEL_WAIT -> tickTeleportWait(Phase.MINING, "travel");
            case MINING -> tickMining();
            case FLEE_WAIT -> tickTeleportWait(Phase.RECOVER, "flee");
            case RECOVER -> tickRecover(p);
            case DEPOSIT_GOTO -> tickDepositGoto();
            case DEPOSIT_MOVE -> tickDepositMove(p);
            case DEPOSIT_SEED -> tickDepositSeed(p);
            default -> {
            }
        }
    }

    private void enter(Phase next) {
        phase = next;
        timer = 0;
        anchor = ctx.player() == null ? Vec3.ZERO : ctx.player().position();
        logDebug("autoMine -> " + next);
    }

    // ── Phases ────────────────────────────────────────────────────────────────

    private void tickTravel() {
        MenuClickProcess menu = baritone.getMenuClickProcess();
        if (timer == 1) {
            String cmd = Baritone.settings().autoMineTravelCommand.value.trim();
            String chain = Baritone.settings().autoMineTravelSteps.value.trim();
            if (cmd.isEmpty() || chain.isEmpty()) {
                stop("autoMineTravelCommand / autoMineTravelSteps are not set");
                return;
            }
            baritone.getMineProcess().cancel();
            menu.start(cmd.startsWith("/") ? cmd.substring(1) : cmd, MenuClickProcess.parseChain(chain));
            return;
        }
        if (!menu.isActive()) {
            if (menu.succeeded()) {
                enter(Phase.TRAVEL_WAIT);
            } else {
                stop("the teleport menu chain failed — check autoMineTravelSteps with #menu dump");
            }
            return;
        }
        if (timer > TRAVEL_TIMEOUT) {
            menu.cancel();
            stop("the teleport menu took too long");
        }
    }

    /** Wait for a large position change, i.e. the server actually teleported us. */
    private void tickTeleportWait(Phase next, String what) {
        double min = Baritone.settings().autoMineTeleportDistance.value;
        if (ctx.player().position().distanceTo(anchor) >= min) {
            logDirect("Arrived (" + what + ").", ChatFormatting.GRAY);
            if (pendingStop) {
                stop(pendingStopReason);
                return;
            }
            enter(next);
            return;
        }
        if (timer > TRAVEL_TIMEOUT) {
            // Didn't move far. Either the tp is on cooldown or it silently
            // failed; carry on rather than wedging — the next phase copes.
            logDirect("No teleport detected after " + what + ", continuing anyway.", ChatFormatting.YELLOW);
            if (pendingStop) {
                stop(pendingStopReason);
                return;
            }
            enter(next);
        }
    }

    private void tickMining() {
        // A guard in AutopilotBehavior fired: it already ran mineFleeCommand.
        if (baritone.getAutopilotBehavior().consumeFleeEvent()) {
            String reason = baritone.getAutopilotBehavior().lastFleeReason();
            logDirect("Guard fired: " + reason, ChatFormatting.YELLOW);
            if (baritone.getAutopilotBehavior().lastFleeWasTerminal()) {
                // Resting and depositing can't restore a worn pickaxe, so ride
                // the /home teleport out and then end the loop rather than
                // teleporting straight back to mine with nothing to mine with.
                pendingStop = true;
                pendingStopReason = reason;
            }
            enter(Phase.FLEE_WAIT);
            return;
        }
        if (baritone.getMineProcess().isActive()) {
            return;
        }
        // Not mining yet — (re)start it, with a grace period for spin-up.
        if (timer == 1 || timer % MINE_START_GRACE == 0) {
            String target = Baritone.settings().autoMineTarget.value.trim();
            if (target.isEmpty()) {
                stop("autoMineTarget is not set");
                return;
            }
            logDirect("Mining " + target + " (cycle " + (cycles + 1) + ")", ChatFormatting.GRAY);
            baritone.getMineProcess().mineByName(0, target.split("[,\\s]+"));
        }
    }

    private void tickRecover(LocalPlayer p) {
        baritone.getAutopilotBehavior().setRecoveryEating(true);

        float wantHp = Baritone.settings().autoMineResumeHealth.value.floatValue();
        int wantFood = Baritone.settings().autoMineResumeFood.value;
        boolean healthy = p.getHealth() >= wantHp;
        boolean fed = p.getFoodData().getFoodLevel() >= wantFood;

        if (healthy && fed) {
            baritone.getAutopilotBehavior().setRecoveryEating(false);
            enter(Baritone.settings().autoMineDeposit.value ? Phase.DEPOSIT_GOTO : Phase.TRAVEL);
            return;
        }
        if (timer % 100 == 0) {
            logDirect(String.format("Recovering: %.1f/%.1f hp, %d/%d food",
                    p.getHealth(), wantHp, p.getFoodData().getFoodLevel(), wantFood), ChatFormatting.GRAY);
        }
        if (timer > RECOVER_TIMEOUT) {
            baritone.getAutopilotBehavior().setRecoveryEating(false);
            if (wouldTripImmediately(p)) {
                // Going back out now means mining one block, tripping the same
                // guard, teleporting home, and failing to recover again — an
                // endless thrash. If we can't get healthy here, we stay here.
                stop(String.format("couldn't recover (%.1f hp, %d food) — out of food?",
                        p.getHealth(), p.getFoodData().getFoodLevel()));
                return;
            }
            logDirect("Recovery timed out, but health and food are above the guard"
                    + " thresholds — continuing.", ChatFormatting.YELLOW);
            enter(Baritone.settings().autoMineDeposit.value ? Phase.DEPOSIT_GOTO : Phase.TRAVEL);
        }
    }

    /**
     * Would a mining guard fire on the very first block, sending us straight
     * home again? The loop must never leave home in a state that requires an
     * immediate return.
     */
    private boolean wouldTripImmediately(LocalPlayer p) {
        if (Baritone.settings().mineFleeOnLowHealth.value
                && p.getHealth() <= Baritone.settings().mineFleeHealth.value.floatValue()) {
            return true;
        }
        int hungerFloor = Baritone.settings().mineFleeHunger.value;
        return hungerFloor > 0 && p.getFoodData().getFoodLevel() <= hungerFloor;
    }

    private void tickDepositGoto() {
        if (foreign()) {
            // Critical: GetToBlockProcess opens the chest by *holding*
            // Input.CLICK_RIGHT on it. Left running, it keeps right-clicking the
            // chest, the server re-opens the container, and every deposit click
            // we send carries a now-stale containerId that gets rejected — which
            // looks exactly like "it just opens the chest and does nothing".
            baritone.getGetToBlockProcess().onLostControl();
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            enter(Phase.DEPOSIT_MOVE);
            return;
        }
        if (timer == 1) {
            if (!Baritone.settings().rightClickContainerOnArrival.value) {
                logDirect("rightClickContainerOnArrival is off — can't open the chest.", ChatFormatting.RED);
                afterDeposit();
                return;
            }
            String block = Baritone.settings().autoMineDepositBlock.value.trim();
            try {
                baritone.getGetToBlockProcess().getToBlock(new BlockOptionalMeta(block));
            } catch (Exception e) {
                logDirect("Bad autoMineDepositBlock \"" + block + "\": " + e.getMessage(), ChatFormatting.RED);
                afterDeposit();
            }
            return;
        }
        if (timer > DEPOSIT_TIMEOUT) {
            logDirect("Couldn't reach a chest — skipping deposit this cycle.", ChatFormatting.YELLOW);
            baritone.getGetToBlockProcess().onLostControl();
            afterDeposit();
        }
    }

    private void tickDepositMove(LocalPlayer p) {
        if (!foreign()) {
            // Menu closed (by us, or the chest went out of range).
            finishDeposit();
            return;
        }
        // Throttle: the server resyncs container state after every click, and
        // a burst of clicks gets rolled back or flagged. Same cadence as
        // InventoryBehavior's inventory moves.
        if (depositCooldown > 0) {
            return;
        }
        AbstractContainerMenu menu = p.containerMenu;
        Set<String> keep = keepSet();

        // Only the 27 main-inventory slots. In any container menu the player's
        // 36 slots come last, main inventory first and hotbar as the final 9 —
        // so the hotbar (tools, food, pearls) and armour are never touched.
        for (int i = mainInvStart(menu); i < hotbarStart(menu); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty() || shouldKeep(s, keep)) {
                continue;
            }
            // QUICK_MOVE from a player slot pushes the stack into the chest.
            ctx.playerController().windowClick(
                    menu.containerId, i, 0, ContainerInput.QUICK_MOVE, ctx.player());
            depositCooldown = Baritone.settings().ticksBetweenInventoryMoves.value;
            return;
        }
        // Nothing left worth depositing — seed the empty slots, then leave.
        enter(Phase.DEPOSIT_SEED);
    }

    /** First of the player's 27 main-inventory slots in an open container menu. */
    private static int mainInvStart(AbstractContainerMenu menu) {
        return MenuClickProcess.foreignSize(menu);
    }

    /** First of the player's 9 hotbar slots — i.e. one past the main inventory. */
    private static int hotbarStart(AbstractContainerMenu menu) {
        return mainInvStart(menu) + 27;
    }

    /**
     * Put one seed item in each of the 27 main-inventory slots, so mined junk
     * (cobblestone, deepslate) has nowhere to land and is left on the ground,
     * while the ore you actually want still stacks onto the seeds.
     *
     * <p>Three sub-steps: pick the seed stack up off the chest with a left
     * click, right-click once into each empty slot (right click drops exactly
     * one), then put the remainder back. The remainder <em>must</em> go back —
     * a carried stack is thrown on the floor when the menu closes.
     */
    private void tickDepositSeed(LocalPlayer p) {
        if (!foreign()) {
            finishDeposit();
            return;
        }
        if (seedReturned) {
            // Remainder is back in the chest and all 27 slots are seeded. Without
            // this guard the next tick would see an empty cursor, pick the stack
            // up again, find no empty slot, and put it straight back — forever.
            finishDeposit();
            return;
        }
        String seedId = Baritone.settings().autoMineSeedItem.value.trim().toLowerCase(Locale.ROOT);
        if (seedId.isEmpty()) {
            finishDeposit();
            return;
        }
        if (depositCooldown > 0) {
            return;
        }
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack carried = menu.getCarried();

        if (carried.isEmpty()) {
            if (seedSourceSlot >= 0) {
                // We ran the stack dry mid-distribution; nothing to give back.
                finishDeposit();
                return;
            }
            int src = findInChest(menu, seedId);
            if (src < 0) {
                logDirect("No " + seedId + " in the chest to seed with — skipping.", ChatFormatting.YELLOW);
                finishDeposit();
                return;
            }
            seedSourceSlot = src;
            click(menu, src, 0, ContainerInput.PICKUP);
            return;
        }

        // Right-click one into the next empty main-inventory slot.
        for (int i = mainInvStart(menu); i < hotbarStart(menu); i++) {
            if (menu.slots.get(i).getItem().isEmpty()) {
                click(menu, i, 1, ContainerInput.PICKUP); // button 1 = place one
                return;
            }
        }

        // All 27 occupied — return whatever's left on the cursor.
        click(menu, seedSourceSlot >= 0 ? seedSourceSlot : 0, 0, ContainerInput.PICKUP);
        seedSourceSlot = -1;
        seedReturned = true;
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ContainerInput type) {
        ctx.playerController().windowClick(menu.containerId, slot, button, type, ctx.player());
        depositCooldown = Baritone.settings().ticksBetweenInventoryMoves.value;
    }

    /** Chest-owned slot holding {@code seedId}, or -1. */
    private int findInChest(AbstractContainerMenu menu, String seedId) {
        int n = mainInvStart(menu);
        for (int i = 0; i < n; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) {
                continue;
            }
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().toLowerCase(Locale.ROOT);
            if (path.equals(seedId) || ("minecraft:" + path).equals(seedId)) {
                return i;
            }
        }
        return -1;
    }

    private void finishDeposit() {
        seedSourceSlot = -1;
        seedReturned = false;
        if (foreign()) {
            ctx.player().closeContainer();
        }
        cycles++;
        logDirect("Deposited. Cycle " + cycles + " complete.", ChatFormatting.GREEN);
        afterDeposit();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean foreign() {
        return ctx.player() != null && ctx.player().containerMenu != ctx.player().inventoryMenu;
    }

    private Set<String> keepSet() {
        Set<String> out = new HashSet<>();
        for (String s : Baritone.settings().autoMineKeep.value.split("[,\\s]+")) {
            if (!s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * Never deposit gear. Damageable items (pickaxes, swords, armour) are kept
     * unconditionally — losing the tool into a chest would end the run — plus
     * anything named in {@code autoMineKeep}.
     */
    private boolean shouldKeep(ItemStack s, Set<String> keep) {
        if (s.isDamageableItem()) {
            return true;
        }
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().toLowerCase(Locale.ROOT);
        return keep.contains(path);
    }

    public String status() {
        if (phase == Phase.IDLE) {
            return "idle";
        }
        return phase + " (" + (timer / 20) + "s, " + cycles + " cycles done)";
    }

    /** Registry paths kept by default: food, pearls, and the water bucket. */
    public static String defaultKeep() {
        return String.join(",", Arrays.asList(
                "cooked_beef", "beef", "ender_pearl", "water_bucket", "torch", "cobblestone"));
    }
}
