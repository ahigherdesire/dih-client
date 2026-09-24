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

package baritone.process;

import baritone.Baritone;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clicks a slot — or a chain of slots across successive menus — in a
 * server-sent container menu ("plugin GUI").
 *
 * <p>A plugin menu is an ordinary {@link AbstractContainerMenu}: the server
 * sends real {@link ItemStack}s as icons and cancels the click event so nothing
 * actually moves. Clicking one is a {@code windowClick(..., PICKUP, 0)} on the
 * right slot, which maps to Bukkit's {@code ClickType.LEFT}.
 *
 * <p>The awkward part is timing. The server sends, in order and with
 * unpredictable gaps:
 * <ol>
 *   <li>{@code ClientboundOpenScreenPacket} — the menu exists but every slot
 *       is empty,</li>
 *   <li>{@code ClientboundContainerSetContentPacket} — the icons appear,</li>
 *   <li>possibly that again, because plugins commonly draw a "loading" pane
 *       layer and replace it a tick or two later.</li>
 * </ol>
 * So this never waits on "is a menu open" — it waits on "is my target present",
 * rescanning every tick until a deadline expires.
 *
 * <p>Multi-step chains (menu → submenu → …) work the same way: after a click we
 * idle for {@code menuStepDelay} ticks to let the server repaint, then start
 * scanning for the next step's matcher. We deliberately do <em>not</em> require
 * the container id to change — plenty of plugins repaint the same inventory in
 * place rather than opening a new one.
 */
public final class MenuClickProcess extends BaritoneProcessHelper {

    /** How a step identifies its target slot. */
    public enum MatchType {
        /** Registry id, e.g. {@code minecraft:heart_of_the_sea}. Most reliable. */
        ITEM,
        /** Case-insensitive substring of the icon's display name or lore. */
        TEXT,
        /** Literal container-slot index. */
        INDEX
    }

    /** One click in a chain. */
    public record Step(MatchType type, String matcher, int index) {

        public static Step parse(String spec) {
            String s = spec.trim();
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("item:")) {
                return new Step(MatchType.ITEM, lower.substring(5).trim(), -1);
            }
            if (lower.startsWith("slot:")) {
                return new Step(MatchType.INDEX, null, Integer.parseInt(lower.substring(5).trim()));
            }
            return new Step(MatchType.TEXT, lower, -1);
        }

        @Override
        public String toString() {
            return switch (type) {
                case ITEM -> "item:" + matcher;
                case INDEX -> "slot:" + index;
                case TEXT -> "\"" + matcher + "\"";
            };
        }
    }

    private enum State { SEND, AWAIT_MENU, AWAIT_ITEM, SETTLE, CLICKED, IDLE }

    /** Ticks to wait for the server to open a menu after the command is sent. */
    private static final int OPEN_TIMEOUT = 100;
    /** Ticks to wait for a step's icon to appear once a menu is open. */
    private static final int FILL_TIMEOUT = 100;
    /** Ticks to wait for the menu to close after the final click. */
    private static final int CLOSE_TIMEOUT = 60;
    /** Safety cap on "next page" hops so a mis-typed matcher can't page forever. */
    private static final int MAX_PAGES = 5;

    private State state = State.IDLE;

    private String command;
    private List<Step> steps = new ArrayList<>();
    private int stepIdx;

    private int deadline;
    private int settle;
    private int pagesSeen;
    private int clickedContainerId;
    private boolean succeeded;

    public MenuClickProcess(Baritone baritone) {
        super(baritone);
    }

    /**
     * Run {@code command}, wait for the menu it opens, then click each step in
     * order across whatever menus the server shows.
     *
     * @param command the command to send, without the leading slash. {@code null}
     *                to act on a menu that is already open.
     */
    public void start(String command, List<Step> steps) {
        this.command = command;
        this.steps = new ArrayList<>(steps);
        this.stepIdx = 0;
        this.pagesSeen = 0;
        this.clickedContainerId = -1;
        this.succeeded = false;
        this.state = command == null ? State.AWAIT_ITEM : State.SEND;
        this.deadline = command == null ? FILL_TIMEOUT : OPEN_TIMEOUT;
    }

    public void cancel() {
        state = State.IDLE;
    }

    /** True once the whole chain clicked through without a timeout. */
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean isActive() {
        return state != State.IDLE && ctx.player() != null && ctx.world() != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        switch (state) {
            case SEND -> {
                // Sent exactly once. Re-firing on a timeout would trip plugin
                // command cooldowns and read as command spam to anticheat.
                Minecraft.getInstance().getConnection().sendCommand(command);
                logDebug("sent /" + command);
                deadline = OPEN_TIMEOUT;
                state = State.AWAIT_MENU;
            }
            case AWAIT_MENU -> {
                if (foreign()) {
                    deadline = FILL_TIMEOUT;
                    state = State.AWAIT_ITEM;
                } else if (--deadline < 0) {
                    fail("the server never opened a menu");
                }
            }
            case SETTLE -> {
                if (--settle <= 0) {
                    deadline = FILL_TIMEOUT;
                    pagesSeen = 0;
                    state = State.AWAIT_ITEM;
                }
            }
            case AWAIT_ITEM -> tickAwaitItem();
            case CLICKED -> {
                if (!foreign()) {
                    logDirect("Menu chain complete.", ChatFormatting.GREEN);
                    succeeded = true;
                    state = State.IDLE;
                } else if (--deadline < 0) {
                    // Plugin left the menu up. Treat the clicks as done but
                    // close it so we aren't stuck in a screen.
                    logDirect("Clicked, but the menu stayed open — closing it.", ChatFormatting.YELLOW);
                    ctx.player().closeContainer();
                    succeeded = true;
                    state = State.IDLE;
                }
            }
            default -> {
            }
        }
        // Hold pathing still for the whole exchange: many teleport plugins
        // cancel on movement, and clicking mid-path desyncs the menu anyway.
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private void tickAwaitItem() {
        if (!foreign()) {
            fail("the menu closed before step " + (stepIdx + 1) + " (" + steps.get(stepIdx) + ") appeared");
            return;
        }

        Step step = steps.get(stepIdx);
        int slot = find(step);
        if (slot >= 0) {
            AbstractContainerMenu menu = ctx.player().containerMenu;
            clickedContainerId = menu.containerId;
            // PICKUP/button 0 == Bukkit ClickType.LEFT, which is what plugin
            // handlers listen for. QUICK_MOVE becomes SHIFT_LEFT, and a handler
            // with an incomplete setCancelled would really move the icon.
            ctx.playerController().windowClick(
                    clickedContainerId, slot, 0, ContainerInput.PICKUP, ctx.player());
            logDirect("Clicked slot " + slot + " ("
                    + menu.slots.get(slot).getItem().getHoverName().getString() + ")", ChatFormatting.GREEN);
            stepIdx++;
            if (stepIdx >= steps.size()) {
                deadline = CLOSE_TIMEOUT;
                state = State.CLICKED;
            } else {
                settle = Baritone.settings().menuStepDelay.value;
                state = State.SETTLE;
            }
            return;
        }

        Step nextPage = new Step(MatchType.TEXT, "next page", -1);
        int next = find(nextPage);
        if (next >= 0 && pagesSeen < MAX_PAGES) {
            ctx.playerController().windowClick(
                    ctx.player().containerMenu.containerId, next, 0, ContainerInput.PICKUP, ctx.player());
            pagesSeen++;
            deadline = FILL_TIMEOUT;
            logDebug("step " + (stepIdx + 1) + " not on this page, advancing");
            return;
        }

        if (--deadline < 0) {
            fail("no slot matched " + step + " — run #menu dump with that menu open to see the real names");
        }
    }

    private void fail(String why) {
        logDirect("#menu failed: " + why, ChatFormatting.RED);
        succeeded = false;
        state = State.IDLE;
    }

    // ── Menu inspection ───────────────────────────────────────────────────────

    private boolean foreign() {
        return ctx.player().containerMenu != ctx.player().inventoryMenu;
    }

    /** Number of container-owned slots; the player's own 36 are always last. */
    public static int foreignSize(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - 36);
    }

    private int find(Step step) {
        AbstractContainerMenu menu = ctx.player().containerMenu;
        int n = foreignSize(menu);
        if (step.type() == MatchType.INDEX) {
            return step.index() >= 0 && step.index() < n
                    && !menu.slots.get(step.index()).getItem().isEmpty() ? step.index() : -1;
        }
        for (int i = 0; i < n; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) {
                continue;
            }
            if (step.type() == MatchType.ITEM) {
                String id = BuiltInRegistries.ITEM.getResourceKey(s.getItem())
                        .map(rk -> rk.identifier().toString())
                        .orElse("");
                if (id.equals(step.matcher()) || id.equals("minecraft:" + step.matcher())) {
                    return i;
                }
            } else {
                if (plain(s.getHoverName()).contains(step.matcher())) {
                    return i;
                }
                for (Component line : s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
                    if (plain(line).contains(step.matcher())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Plugins routinely build names from legacy-formatted literals, so the
     * {@code §} codes end up in the text itself rather than in the Style and
     * survive {@link Component#getString()}. Strip them before matching.
     */
    public static String plain(Component c) {
        return c.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
    }

    /** Parse a {@code >}-separated chain like {@code item:heart_of_the_sea>orange sand}. */
    public static List<Step> parseChain(String spec) {
        List<Step> out = new ArrayList<>();
        for (String part : spec.split(">")) {
            if (!part.isBlank()) {
                out.add(Step.parse(part));
            }
        }
        return out;
    }

    @Override
    public void onLostControl() {
        state = State.IDLE;
    }

    @Override
    public String displayName0() {
        return "Menu click (step " + (stepIdx + 1) + "/" + steps.size() + ", " + state + ")";
    }

    @Override
    public double priority() {
        return 5.2; // just above the inventory pauser
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
