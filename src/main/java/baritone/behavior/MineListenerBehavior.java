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
import baritone.api.event.events.ChatReceivedEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Helper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * {@code #listentomine} — watch incoming chat for a trigger word and, on a match,
 * warp to the mining area, free up an inventory slot, and start mining.
 *
 * <p>The whole reaction only runs while enabled (toggled by
 * {@link baritone.command.defaults.ListenToMineCommand}). When
 * {@link baritone.api.Settings#listenToMineWord} appears in any chat line, the
 * behavior runs a short one-shot sequence on the client thread:
 * <ol>
 *   <li>run {@link baritone.api.Settings#listenToMineWarp} (default {@code /warp mine});</li>
 *   <li>wait {@link baritone.api.Settings#listenToMineWarpDelay} ticks for the teleport;</li>
 *   <li>if the inventory has no empty slot, drop one non-tool main-inventory stack;</li>
 *   <li>{@code #mine} {@link baritone.api.Settings#listenToMineTarget} (default {@code ancient_debris}).</li>
 * </ol>
 *
 * <p>After firing, further trigger words are ignored for
 * {@link baritone.api.Settings#listenToMineCooldown} ticks (and while a sequence
 * is already in flight or mining is already active) so a burst of chat can't
 * re-warp mid-run. The chat callback only sets a flag; every action happens in
 * {@link #onTick} so nothing touches the world off-thread.
 */
public final class MineListenerBehavior extends Behavior implements AbstractGameEventListener {

    /** Whether the listener is active. Toggled by {@code #listentomine}. */
    public static volatile boolean enabled = false;

    private enum Phase { IDLE, WARP, WARP_WAIT, CLEAR, MINE }

    private Phase phase = Phase.IDLE;
    private int timer;
    private int cooldown;
    /** Set by the chat callback, consumed on the next tick. */
    private volatile boolean pendingTrigger;

    public MineListenerBehavior(Baritone baritone) {
        super(baritone);
    }

    // ── Chat: just latch a trigger, do the work on-tick ─────────────────────────

    @Override
    public void onReceiveChatMessage(ChatReceivedEvent event) {
        if (!enabled) {
            return;
        }
        String text = event.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        // Intentionally react to ANY chat line containing the word — including one
        // you type yourself, since that is how you test it and the mod never sends
        // chat, so there is no feedback loop to guard against.
        String word = Baritone.settings().listenToMineWord.value.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty() || !text.toLowerCase(Locale.ROOT).contains(word)) {
            return;
        }
        // Only accept a fresh trigger when idle, off cooldown, and not already mining.
        if (phase != Phase.IDLE || cooldown > 0 || baritone.getMineProcess().isActive()) {
            return;
        }
        pendingTrigger = true;
        logHelper("Heard \"" + Baritone.settings().listenToMineWord.value + "\" — warping and mining "
                + Baritone.settings().listenToMineTarget.value + ".");
    }

    // ── Tick: run the sequence ──────────────────────────────────────────────────

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            phase = Phase.IDLE;
            pendingTrigger = false;
            cooldown = 0;
            timer = 0;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (!enabled) {
            // Disabling mid-sequence just abandons the pending reaction; anything
            // already handed off to #mine keeps running until the user #stops it.
            phase = Phase.IDLE;
            pendingTrigger = false;
            return;
        }
        timer++;

        switch (phase) {
            case IDLE -> {
                if (pendingTrigger) {
                    pendingTrigger = false;
                    enter(Phase.WARP);
                }
            }
            case WARP -> {
                String warp = Baritone.settings().listenToMineWarp.value;
                logHelper("Warping: " + warp);
                runCommand(warp);
                enter(Phase.WARP_WAIT);
            }
            case WARP_WAIT -> {
                if (timer >= Math.max(0, Baritone.settings().listenToMineWarpDelay.value)) {
                    enter(Phase.CLEAR);
                }
            }
            case CLEAR -> {
                ensureFreeSlot(ctx.player());
                enter(Phase.MINE);
            }
            case MINE -> {
                String target = Baritone.settings().listenToMineTarget.value.trim();
                if (!target.isEmpty()) {
                    // Same as typing "#mine ancient_debris": quantity 0 = unlimited.
                    baritone.getMineProcess().mineByName(0, target.split("[,\\s]+"));
                    logHelper("Mining " + target + ".");
                }
                cooldown = Math.max(0, Baritone.settings().listenToMineCooldown.value);
                phase = Phase.IDLE;
            }
        }
    }

    private void enter(Phase next) {
        phase = next;
        timer = 0;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Ensure at least one empty inventory slot, dropping a single non-tool stack
     * from the main inventory (never the hotbar) if the inventory is full, so
     * mined ancient debris has somewhere to land.
     */
    private void ensureFreeSlot(LocalPlayer p) {
        try {
            // A chest/other menu being open would make these slot indices wrong.
            if (p.containerMenu != p.inventoryMenu) {
                return;
            }
            NonNullList<ItemStack> items = p.getInventory().getNonEquipmentItems();
            for (ItemStack st : items) {
                if (st == null || st.isEmpty()) {
                    return; // already have room
                }
            }
            // Full — drop one non-tool stack from the main inventory (slots 9..35).
            for (int i = 9; i < items.size(); i++) {
                ItemStack st = items.get(i);
                if (st == null || st.isEmpty() || st.isDamageableItem()) {
                    continue; // keep tools/armour
                }
                // Whole-stack throw (button 1) from that slot; the player inventory
                // menu maps main-inventory index i directly to menu slot i.
                ctx.playerController().windowClick(
                        p.inventoryMenu.containerId, i, 1, ContainerInput.THROW, p);
                logHelper("Inventory full — dropped a stack to make room.");
                return;
            }
            logHelper("Inventory full and nothing safe to drop — mining anyway.");
        } catch (Throwable t) {
            logHelper("Couldn't clear an inventory slot: " + t.getMessage());
        }
    }

    /** Runs a command string: {@code #x} → Baritone command, otherwise a server command. */
    private void runCommand(String raw) {
        try {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) {
                return;
            }
            if (c.startsWith("#")) {
                baritone.getCommandManager().execute(c.substring(1));
            } else {
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand(c.startsWith("/") ? c.substring(1) : c);
                }
            }
        } catch (Throwable t) {
            logHelper("Failed to run '" + raw + "': " + t.getMessage());
        }
    }

    private void logHelper(String msg) {
        Helper.HELPER.logDirect("[ListenToMine] " + msg, ChatFormatting.LIGHT_PURPLE);
    }
}
