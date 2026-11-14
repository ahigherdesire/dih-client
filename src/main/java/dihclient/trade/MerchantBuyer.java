package dihclient.trade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Buys offers matching a {@link TradeRule} from the trade screen that is open now, one purchase at a time, and
 * never starts the next click before the server has answered the last one:
 * <ol>
 *   <li>select the offer (as clicking it in the list does), then wait for the server to fill the result slot;</li>
 *   <li>pick up one result (a plain click, so exactly one trade happens) and put it in an empty inventory slot;</li>
 *   <li>wait a round trip (the server only answers a click when it disagrees with it) and count the purchase if the
 *   bought item is still where it was put.</li>
 * </ol>
 * Call {@link #tick} every client tick until it returns something other than {@link State#WORKING}.
 */
public final class MerchantBuyer {

    public enum State { WORKING, FINISHED, FAILED }

    private enum Phase { READ, FILL, PLACE, CONFIRM }

    /** Ticks to wait for the server at each step before giving up. */
    static final int SERVER_TIMEOUT = 60;
    private static final int RESULT_SLOT = 2;
    private static final int FIRST_INVENTORY_SLOT = 3;
    private static final int LAST_INVENTORY_SLOT = 38;

    private final TradeRule rule;
    private final TradeBudget budget;
    private final String profession;
    private final int maxPurchases;
    private int spent;
    private int bought;
    private Phase phase = Phase.READ;
    private int ticks;
    private TradeOffer pending;
    private int stateBefore;
    private int placedSlot = -1;
    private String status = "reading offers";
    private TradeBudget.Verdict lastVerdict;
    private final StringBuilder boughtText = new StringBuilder();

    /**
     * @param spentSoFar   emeralds already spent this session (counts against the budget)
     * @param maxPurchases stop after this many purchases, 0 for no limit (the budget's per-visit cap also applies)
     */
    public MerchantBuyer(TradeRule rule, TradeBudget budget, String profession, int spentSoFar, int maxPurchases) {
        this.rule = rule;
        this.budget = budget;
        this.profession = profession;
        this.spent = spentSoFar;
        this.maxPurchases = maxPurchases;
    }

    public int spent() {
        return spent;
    }

    public int bought() {
        return bought;
    }

    public String status() {
        return status;
    }

    /** Why buying stopped, if a budget limit stopped it. */
    public TradeBudget.Verdict lastVerdict() {
        return lastVerdict;
    }

    /** "mending 1 – 14e, bread – 1e", or empty. */
    public String boughtText() {
        return boughtText.toString();
    }

    public State tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || mc.gameMode == null) return fail("not in a world");
        if (!(player.containerMenu instanceof MerchantMenu menu)) return fail("the trade screen closed");
        ticks++;
        switch (phase) {
            case READ -> {
                if (menu.getOffers().isEmpty()) {
                    if (ticks > SERVER_TIMEOUT) return fail("the villager sent no offers");
                    status = "waiting for offers";
                    return State.WORKING;
                }
                if (maxPurchases > 0 && bought >= maxPurchases) return finish("bought " + bought);
                TradeOffer next = pick(menu, player);
                if (next == null) {
                    return finish(bought > 0 ? "bought " + bought
                            : lastVerdict != null ? lastVerdict.describe() : "no offer matches " + rule.describe());
                }
                pending = next;
                stateBefore = menu.getStateId();
                menu.setSelectionHint(next.index());
                menu.tryMoveItems(next.index());
                mc.getConnection().send(new ServerboundSelectTradePacket(next.index()));
                phase = Phase.FILL;
                ticks = 0;
                status = "buying " + next.describe();
            }
            case FILL -> {
                ItemStack result = menu.getSlot(RESULT_SLOT).getItem();
                if (result.isEmpty() || !pending.resultId().equals(TradeOffers.id(result))) {
                    if (ticks > SERVER_TIMEOUT) return fail("the server didn't fill the trade (missing payment?)");
                    return State.WORKING;
                }
                if (emptyInventorySlot(menu) < 0) return fail("inventory full");
                stateBefore = menu.getStateId();
                mc.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.PICKUP, player);
                phase = Phase.PLACE;
                ticks = 0;
            }
            case PLACE -> {
                if (menu.getCarried().isEmpty()) {
                    if (ticks > SERVER_TIMEOUT) return fail("the server took the trade back");
                    return State.WORKING;
                }
                int slot = emptyInventorySlot(menu);
                if (slot < 0) return fail("inventory full");
                stateBefore = menu.getStateId();
                placedSlot = slot;
                mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
                phase = Phase.CONFIRM;
                ticks = 0;
            }
            case CONFIRM -> {
                // A click the server agrees with gets no reply, so give it one round trip to undo the trade.
                if (menu.getStateId() == stateBefore && ticks < settleTicks(mc)) return State.WORKING;
                ItemStack placed = menu.getSlot(placedSlot).getItem();
                if (!menu.getCarried().isEmpty() || !pending.resultId().equals(TradeOffers.id(placed))) {
                    if (ticks > SERVER_TIMEOUT) return fail("the server undid the trade");
                    return State.WORKING;
                }
                spent += pending.emeraldPrice();
                bought++;
                if (!boughtText.isEmpty()) boughtText.append(", ");
                boughtText.append(pending.describe());
                phase = Phase.READ;
                ticks = 0;
                status = "bought " + bought;
            }
        }
        return State.WORKING;
    }

    /** The first offer the rule matches that the budget allows and whose other payment is in the inventory. */
    private TradeOffer pick(MerchantMenu menu, LocalPlayer player) {
        List<TradeOffer> offers = TradeOffers.from(menu.getOffers());
        int emeralds = count(menu, player, TradeOffer.EMERALD);
        lastVerdict = null;
        for (TradeOffer offer : offers) {
            if (!rule.matches(offer, profession)) continue;
            TradeBudget.Verdict verdict = budget.check(offer, rule, emeralds, spent, bought);
            if (verdict != TradeBudget.Verdict.BUY) {
                lastVerdict = verdict;
                continue;
            }
            if (!TradeOffer.EMERALD.equals(offer.costAId()) && count(menu, player, offer.costAId()) < offer.costACount()) continue;
            if (offer.costBId() != null && !TradeOffer.EMERALD.equals(offer.costBId())
                    && count(menu, player, offer.costBId()) < offer.costBCount()) continue;
            return offer;
        }
        return null;
    }

    /** Items of {@code id} in the inventory plus the two payment slots. */
    private static int count(MerchantMenu menu, LocalPlayer player, String id) {
        if (id == null) return 0;
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (id.equals(TradeOffers.id(stack))) n += stack.getCount();
        }
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (id.equals(TradeOffers.id(stack))) n += stack.getCount();
        }
        return n;
    }

    /** Ticks for a click to reach the server and a correction to come back: two pings plus a margin. */
    static int settleTicks(Minecraft mc) {
        int latencyMs = 0;
        if (mc.getConnection() != null && mc.player != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) latencyMs = Math.max(0, info.getLatency());
        }
        return settleTicksFor(latencyMs);
    }

    static int settleTicksFor(int latencyMs) {
        return Math.min(SERVER_TIMEOUT / 2, 4 + (latencyMs * 2 + 49) / 50);
    }

    private static int emptyInventorySlot(MerchantMenu menu) {
        for (int slot = FIRST_INVENTORY_SLOT; slot <= LAST_INVENTORY_SLOT; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) return slot;
        }
        return -1;
    }

    private State finish(String why) {
        status = why;
        return State.FINISHED;
    }

    private State fail(String why) {
        status = why;
        return State.FAILED;
    }
}
