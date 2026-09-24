/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #testeat} and {@code #testdeposit} — run one leg of the mining loop in
 * isolation, so a failure points at a single subsystem instead of the whole cycle.
 *
 * <p>Companion to {@code #testrtp}, which covers the travel leg.
 */
public class TestLegCommand extends Command {

    public TestLegCommand(IBaritone baritone) {
        super(baritone, "testeat", "testdeposit");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        boolean off = args.hasAny() && args.getString().equalsIgnoreCase("off");
        args.requireMax(0);

        if (label.equalsIgnoreCase("testdeposit")) {
            doDeposit();
        } else {
            doEat(off);
        }
    }

    // ── #testeat ──────────────────────────────────────────────────────────────

    private void doEat(boolean off) {
        var autopilot = ((Baritone) baritone).getAutopilotBehavior();

        if (off) {
            autopilot.setRecoveryEating(false);
            autopilot.releaseEating();
            logDirect("Eat test off — use key released.", ChatFormatting.YELLOW);
            return;
        }

        var p = ctx.player();
        int food = p.getFoodData().getFoodLevel();
        int target = Baritone.settings().autoMineResumeFood.value;

        logDirect("Eat test: food " + food + "/20, eating up to " + target, ChatFormatting.AQUA);

        if (!Baritone.settings().mineAutoEat.value) {
            logDirect("mineAutoEat is OFF — nothing will happen. #set mineAutoEat true",
                    ChatFormatting.RED);
            return;
        }
        if (food >= target) {
            logDirect("Already at or above the target, so it won't eat. Lower the target or"
                    + " get hungrier to test: #set autoMineResumeFood " + Math.max(1, food - 1),
                    ChatFormatting.YELLOW);
            return;
        }

        // Report where the food actually is — the eat path can only select a
        // hotbar slot, and only pulls from the main inventory while recovering.
        int hotbar = findSlot(0, 9);
        int main = findSlot(9, 36);
        if (hotbar >= 0) {
            logDirect("Beef found in hotbar slot " + hotbar + " — should eat now.", ChatFormatting.GREEN);
        } else if (main >= 0) {
            logDirect("Beef only in main inventory slot " + main
                    + " — it will be swapped to the hotbar first.", ChatFormatting.GREEN);
        } else {
            logDirect("No cooked_beef or beef anywhere in your inventory — nothing to eat.",
                    ChatFormatting.RED);
            return;
        }

        autopilot.setRecoveryEating(true);
        logDirect("Holding use. Watch your food bar; run '#testeat off' to stop.");
    }

    /** First inventory slot in [from, to) holding cooked or raw beef, else -1. */
    private int findSlot(int from, int to) {
        var items = ctx.player().getInventory().getNonEquipmentItems();
        for (int i = from; i < to && i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (st != null && !st.isEmpty()
                    && (st.getItem() == Items.COOKED_BEEF || st.getItem() == Items.BEEF)) {
                return i;
            }
        }
        return -1;
    }

    // ── #testdeposit ──────────────────────────────────────────────────────────

    private void doDeposit() {
        var loop = ((Baritone) baritone).getAutoMineBehavior();
        if (loop.isRunning()) {
            logDirect("Already running: " + loop.status() + " — #stop first.", ChatFormatting.YELLOW);
            return;
        }

        String seed = Baritone.settings().autoMineSeedItem.value.trim();
        logDirect("Deposit test: chest=" + Baritone.settings().autoMineDepositBlock.value
                + "  seed=" + (seed.isEmpty() ? "(disabled)" : seed), ChatFormatting.AQUA);
        logDirect("Depositing the 27 main-inventory slots only — hotbar and armour are untouched.");

        if (!seed.isEmpty() && !BuiltInRegistries.ITEM.containsKey(
                net.minecraft.resources.Identifier.withDefaultNamespace(seed))) {
            logDirect("Warning: \"" + seed + "\" isn't a known item id — seeding will be skipped.",
                    ChatFormatting.YELLOW);
        }

        loop.startDepositOnly();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (label.equalsIgnoreCase("testeat") && args.hasExactlyOne()) {
            return Stream.of("off");
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Test one leg of the mining loop on its own";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Runs a single leg of the #start minecmd cycle so a failure points at one",
                "subsystem rather than the whole loop.",
                "",
                "> #testeat        - hold the use key and eat beef up to autoMineResumeFood.",
                "                    Reports whether the beef is on your hotbar or in the main",
                "                    inventory, and whether mineAutoEat is even on.",
                "> #testeat off    - stop eating and release the use key.",
                "> #testdeposit    - path to the nearest chest, deposit the 27 main-inventory",
                "                    slots, seed one autoMineSeedItem into each, then stop.",
                "",
                "See also #testrtp for the travel leg."
        );
    }
}
