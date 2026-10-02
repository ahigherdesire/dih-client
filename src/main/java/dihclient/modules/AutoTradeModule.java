package dihclient.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalNear;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import dihclient.trade.MerchantBuyer;
import dihclient.trade.Reroller;
import dihclient.trade.TradeBudget;
import dihclient.trade.TradeCaches;
import dihclient.trade.TradeOffers;
import dihclient.trade.TradeRule;
import dihclient.trade.VillagerOffers;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * AutoTrade. <b>Trade</b> mode buys offers matching a rule from the villager whose trades are open, or walks to
 * nearby villagers (with Baritone) and opens their trades itself, within an emerald budget, a reserve it never goes
 * below, a per-visit cap and a max price; a visited villager is left alone until its restock wait passes.
 * <b>Reroll</b> mode rerolls the villager you look at until the rule matches, then buys once to lock the trade.
 *
 * <p>Whenever any trade screen opens, even with the module off, the villager's offers are remembered (per world)
 * for {@code .enchant plan} and the labels above villagers.
 */
public final class AutoTradeModule extends Module {

    private static final double WALK_RADIUS = 48;
    private static final double REACH = 3.5;
    private static final int OPEN_TIMEOUT = 100;
    private static final int RECORD_EVERY = 20;

    private MerchantBuyer buyer;
    private AbstractVillager buyingFrom;
    private Reroller reroller;
    private int spent;
    private int bought;
    private final Map<String, Long> visitedAt = new HashMap<>();
    private AbstractVillager walkingTo;
    private int walkTicks;
    private int recordTicks;
    private int recordedContainer = -1;
    private String status = "";

    public AutoTradeModule() {
        super("auto-trade", "AutoTrade", ModuleCategory.MISC,
            "Buys villager trades that match a rule, or rerolls a villager until one does.");
        add(new ChoiceSetting("mode", "Mode", "Trade", "Trade", "Reroll")
            .description("Trade: buy matching offers. Reroll: reroll the villager you look at until one matches, then lock it.").build());
        add(new StringSetting("rule", "Match", "mending")
            .description("mending, unbreaking3, sharpness 4-5, book, slot 2, item:bread or any.").build());
        add(new StringSetting("profession", "Profession", "")
            .description("Only villagers with this job, e.g. librarian. Empty: any.").build());
        add(new IntSetting("max-price", "Max Price", 0, 0, 64, 1)
            .description("Most emeralds to pay per purchase. 0: no limit.").build());
        add(new IntSetting("budget", "Emerald Budget", 0, 0, 2304, 8)
            .description("Most emeralds to spend while enabled. 0: no limit.").build());
        add(new IntSetting("reserve", "Emerald Reserve", 0, 0, 2304, 8)
            .description("Never spend below this many emeralds.").build());
        add(new IntSetting("per-visit", "Per-Visit Cap", 0, 0, 64, 1)
            .description("Purchases per villager visit. 0: no limit.").build());
        add(new IntSetting("restock-wait", "Restock Wait (s)", 300, 0, 1800, 30)
            .description("Trade mode: seconds before visiting the same villager again.").build());
        add(new BoolSetting("walk", "Walk To Villagers", true)
            .description("Trade mode: walk to nearby villagers and open their trades.").build());
        add(new BoolSetting("labels", "Offer Labels", true)
            .description("Show each villager's best remembered offer above it (works with the module off).").build());
    }

    // ---------------------------------------------------------------- public view

    /** A short line for the HUD and macros: what AutoTrade is doing now. */
    public String status() {
        return status;
    }

    @Override
    public String info() {
        return choice("mode");
    }

    public static boolean labelsOn() {
        return ModuleRegistry.get("auto-trade") instanceof AutoTradeModule m && m.bool("labels");
    }

    public TradeRule rule() {
        return TradeRule.parse(text("rule")).withMaxPrice(integer("max-price")).withProfession(text("profession"));
    }

    public TradeBudget budget() {
        return new TradeBudget(integer("budget"), integer("reserve"), integer("per-visit"));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        spent = 0;
        bought = 0;
        visitedAt.clear();
        buyer = null;
        reroller = null;
        walkingTo = null;
        try {
            TradeRule rule = rule();
            if ("Reroll".equals(choice("mode"))) {
                reroller = Reroller.start(MC, rule, budget());
                status = "rerolling for " + rule.describe();
            } else {
                status = "trading for " + rule.describe();
            }
            chat("§7AutoTrade: " + status + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            chat("§cAutoTrade: " + e.getMessage() + ".");
            disableSilentlyWithToggleMessage(null);
        }
    }

    @Override
    public void onDisable() {
        if (reroller != null) reroller.cancel(MC);
        if (walkingTo != null) stopWalking();
        reroller = null;
        buyer = null;
        walkingTo = null;
    }

    @Override
    public void onGameLeft() {
        if (isEnabled()) setEnabled(false);
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public boolean hasDisabledTickWork() {
        return true;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return;
        recordOpenTrades(player);
        if (!isEnabled()) return;
        if (reroller != null) {
            tickReroll();
        } else {
            tickTrade(player);
        }
    }

    // ---------------------------------------------------------------- offer cache

    /** Remembers the offers of the villager whose trade screen is open. */
    private void recordOpenTrades(LocalPlayer player) {
        if (!(player.containerMenu instanceof MerchantMenu menu) || menu.getOffers().isEmpty()) {
            recordedContainer = -1;
            return;
        }
        if (menu.containerId == recordedContainer && ++recordTicks < RECORD_EVERY) return;
        recordTicks = 0;
        AbstractVillager merchant = openMerchant(player);
        if (merchant == null) return;
        recordedContainer = menu.containerId;
        TradeCaches.record(new VillagerOffers(merchant.getStringUUID(), TradeOffers.profession(merchant),
                TradeOffers.level(merchant), menu.getTraderXp(), merchant.getBlockX(), merchant.getBlockY(),
                merchant.getBlockZ(), TradeOffers.from(menu.getOffers()), System.currentTimeMillis()));
    }

    /** The villager whose trades are open: the one AutoTrade opened, the one looked at, else the nearest. */
    private AbstractVillager openMerchant(LocalPlayer player) {
        if (buyingFrom != null && buyingFrom.isAlive()) return buyingFrom;
        if (reroller != null) return reroller.villager();
        if (MC.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof AbstractVillager v) return v;
        return MC.level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(5), AbstractVillager::isAlive)
                .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElse(null);
    }

    // ---------------------------------------------------------------- reroll mode

    private void tickReroll() {
        Reroller.State s = reroller.tick(MC);
        status = reroller.status();
        if (s == Reroller.State.WORKING) return;
        if (s == Reroller.State.LOCKED) chat("§aAutoTrade: " + reroller.status() + ".");
        else chat("§cAutoTrade: reroll stopped: " + reroller.status() + ".");
        reroller = null;
        disableSilentlyWithToggleMessage(null);
    }

    // ---------------------------------------------------------------- trade mode

    private void tickTrade(LocalPlayer player) {
        if (player.containerMenu instanceof MerchantMenu) {
            if (walkingTo != null) stopWalking();
            if (buyer == null) {
                buyingFrom = openMerchant(player);
                buyer = new MerchantBuyer(rule(), budget(), buyingFrom == null ? null : TradeOffers.profession(buyingFrom),
                        spent, 0);
            }
            MerchantBuyer.State s = buyer.tick(MC);
            status = buyer.status();
            if (s == MerchantBuyer.State.WORKING) return;
            finishVisit(player);
            return;
        }
        if (buyer != null) finishVisit(player);
        if (!bool("walk")) {
            status = "open a villager's trades";
            return;
        }
        tickWalk(player);
    }

    private void finishVisit(LocalPlayer player) {
        int got = buyer.bought();
        spent = buyer.spent();
        bought += got;
        String why = buyer.status();
        TradeBudget.Verdict verdict = buyer.lastVerdict();
        if (buyingFrom != null) visitedAt.put(buyingFrom.getStringUUID(), System.currentTimeMillis());
        if (got > 0) chat("§aAutoTrade: bought " + buyer.boughtText() + " §7(" + spent + " emeralds this session).");
        else if (!why.isBlank()) chat("§7AutoTrade: " + why + ".");
        buyer = null;
        buyingFrom = null;
        if (player.containerMenu instanceof MerchantMenu) player.closeContainer();
        if (verdict == TradeBudget.Verdict.BUDGET_SPENT || verdict == TradeBudget.Verdict.BELOW_RESERVE
                || verdict == TradeBudget.Verdict.NOT_ENOUGH_EMERALDS) {
            chat("§7AutoTrade: stopping, " + verdict.describe() + ".");
            disableSilentlyWithToggleMessage(null);
        }
    }

    private void tickWalk(LocalPlayer player) {
        if (walkingTo != null && (!walkingTo.isAlive() || ++walkTicks > 20 * 60)) {
            if (walkingTo.isAlive()) visitedAt.put(walkingTo.getStringUUID(), System.currentTimeMillis());
            stopWalking();
        }
        if (walkingTo == null) {
            walkingTo = nextVillager(player);
            walkTicks = 0;
            if (walkingTo == null) {
                status = visitedAt.isEmpty() ? "no villagers nearby" : "waiting for villagers to restock";
                return;
            }
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                    .setGoalAndPath(new GoalNear(walkingTo.blockPosition(), 2));
        }
        status = "walking to a villager";
        if (player.distanceTo(walkingTo) > REACH) {
            if (walkTicks % 40 == 0) BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                    .setGoalAndPath(new GoalNear(walkingTo.blockPosition(), 2));
            return;
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        status = "opening trades";
        if (walkTicks % 20 == 0) {
            Vec3 center = walkingTo.getBoundingBox().getCenter();
            var rotation = baritone.api.utils.RotationUtils.calcRotationFromVec3d(player.getEyePosition(), center,
                    new baritone.api.utils.Rotation(player.getYRot(), player.getXRot()));
            player.setYRot(rotation.getYaw());
            player.setXRot(rotation.getPitch());
            buyingFrom = walkingTo;
            MC.gameMode.interact(player, walkingTo, new EntityHitResult(walkingTo, center), InteractionHand.MAIN_HAND);
        }
        if (walkTicks > OPEN_TIMEOUT * 4) {
            visitedAt.put(walkingTo.getStringUUID(), System.currentTimeMillis());
            stopWalking();
        }
    }

    /** The nearest villager worth visiting: right job, not visited within the restock wait, and a match if known. */
    private AbstractVillager nextVillager(LocalPlayer player) {
        TradeRule rule = rule();
        long wait = integer("restock-wait") * 1000L;
        long now = System.currentTimeMillis();
        return MC.level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(WALK_RADIUS),
                        v -> v.isAlive() && !v.isBaby())
                .stream()
                .filter(v -> {
                    Long seen = visitedAt.get(v.getStringUUID());
                    return seen == null || now - seen >= wait;
                })
                .filter(v -> rule.profession() == null || rule.profession().equals(TradeOffers.profession(v)))
                .filter(v -> {
                    VillagerOffers cached = TradeCaches.current().get(v.getStringUUID());
                    return cached == null || cached.best(rule) != null;
                })
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
                .orElse(null);
    }

    private void stopWalking() {
        walkingTo = null;
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    private static void chat(String message) {
        DihClientMessaging.sendPrefixed(message);
    }
}
