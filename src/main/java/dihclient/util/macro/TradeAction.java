package dihclient.util.macro;

import dihclient.api.macro.ContextMacroAction;
import dihclient.api.macro.MacroExecutionContext;
import dihclient.trade.MerchantBuyer;
import dihclient.trade.TradeBudget;
import dihclient.trade.TradeRule;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Trade: buys from the villager trade screen that is open (open it with an earlier step, e.g. Interact), up to
 * {@link #count} purchases of offers matching {@link #rule}, each confirmed by the server. Later steps can read
 * {@code trade_count} (purchases), {@code trade_spent} (emeralds) and {@code trade_status} ({@code ok},
 * {@code none} when nothing matched or could be afforded, or {@code failed: <reason>}).
 */
public class TradeAction implements ContextMacroAction {
    /** Same text as AutoTrade's Match setting: mending, unbreaking3, slot 2, item:bread, any. */
    public String rule = "any";
    public int maxPrice = 0;
    /** Purchases to make; 0 buys until nothing matching is affordable. */
    public int count = 1;
    /** How long to wait for a trade screen to be open. */
    public int timeoutMs = 5000;
    private boolean enabled = true;

    @Override
    public void run(MacroExecutionContext ctx) throws InterruptedException {
        MacroTemplate.Resolution resolved = ctx.resolveTemplate(rule == null ? "" : rule);
        TradeRule parsed;
        try {
            parsed = TradeRule.parse(resolved.success() ? resolved.value() : rule).withMaxPrice(maxPrice);
        } catch (IllegalArgumentException e) {
            publish(ctx, 0, 0, "failed: " + e.getMessage());
            return;
        }
        int waited = 0;
        while (!ctx.callOnClientThread(TradeAction::screenOpen)) {
            if (!ctx.isActive()) return;
            if (waited >= timeoutMs) {
                publish(ctx, 0, 0, "failed: no trade screen open");
                return;
            }
            ctx.waitTicks(1);
            waited += 50;
        }
        MerchantBuyer buyer = new MerchantBuyer(parsed, TradeBudget.UNLIMITED, null, 0, Math.max(0, count));
        MerchantBuyer.State state = MerchantBuyer.State.WORKING;
        while (state == MerchantBuyer.State.WORKING) {
            if (!ctx.isActive()) break;
            state = ctx.callOnClientThread(() -> buyer.tick(Minecraft.getInstance()));
            ctx.setStatus("Trade: " + buyer.status());
            if (state == MerchantBuyer.State.WORKING) ctx.waitTicks(1);
        }
        String status = state == MerchantBuyer.State.FAILED ? "failed: " + buyer.status()
                : buyer.bought() > 0 ? "ok" : "none";
        publish(ctx, buyer.bought(), buyer.spent(), status);
    }

    private static boolean screenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.containerMenu instanceof MerchantMenu;
    }

    private static void publish(MacroExecutionContext ctx, int count, int spent, String status) {
        ctx.setVariable("trade_count", MacroValue.number(count));
        ctx.setVariable("trade_spent", MacroValue.number(spent));
        ctx.setVariable("trade_status", MacroValue.text(status));
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("rule", rule == null ? "" : rule);
        tag.putInt("maxPrice", maxPrice);
        tag.putInt("count", count);
        tag.putInt("timeoutMs", timeoutMs);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        rule = tag.getStringOr("rule", "any");
        maxPrice = Math.max(0, tag.getIntOr("maxPrice", 0));
        count = Math.max(0, tag.getIntOr("count", 1));
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 5000));
        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.TRADE;
    }

    @Override
    public String getDisplayName() {
        String what = rule == null || rule.isBlank() ? "any" : rule.trim();
        return "Trade " + (count == 0 ? "all " : count + "x ") + what + (maxPrice > 0 ? " ≤" + maxPrice + "e" : "");
    }

    @Override
    public String getIcon() {
        return "E";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
