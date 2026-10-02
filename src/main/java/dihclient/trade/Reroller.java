package dihclient.trade;

import dihclient.util.DihEntities;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Rerolls one villager's trades until the rule matches, then buys once to lock them in:
 * open the trades, and if nothing matches, close, break the workstation, wait until the villager loses its
 * profession, pick the block up, put it back, wait until the villager takes the job again, and open the trades
 * again. Every step waits for the game to confirm it (the block gone, the profession gone, the block back, the
 * profession back, the offers arrived) with a timeout and a clear failure.
 *
 * <p>Only a villager with 0 trade XP can be rerolled; anyone who has traded with it fixes its trades. Villagers
 * take jobs during work hours, so it waits for daytime before breaking the workstation.
 */
public final class Reroller {

    public enum State { WORKING, LOCKED, FAILED }

    private enum Phase { OPEN, READ, CLOSE, BREAK, WAIT_LOSS, COLLECT, PLACE, WAIT_GAIN }

    private static final int OPEN_TIMEOUT = 100;
    private static final int BREAK_TIMEOUT = 400;
    private static final int LOSS_TIMEOUT = 400;
    private static final int COLLECT_TIMEOUT = 200;
    private static final int PLACE_TIMEOUT = 60;
    private static final int GAIN_TIMEOUT = 1200;
    private static final double REACH = 4.0;

    private final Villager villager;
    private final BlockPos workstation;
    private final Item workstationItem;
    private final Holder<VillagerProfession> profession;
    private final TradeRule rule;
    private final TradeBudget budget;
    private Phase phase = Phase.OPEN;
    private int ticks;
    private int rerolls;
    private int placeTries;
    private MerchantBuyer buyer;
    /** Set once a purchase has started on the open screen. */
    private boolean buying;
    /** Where the player stood when the reroll started: somewhere to step back to, clear of the workstation. */
    private final BlockPos standAt;
    private String status = "opening trades";
    private String locked;

    private Reroller(Villager villager, BlockPos workstation, Item item, Holder<VillagerProfession> profession,
                     TradeRule rule, TradeBudget budget, BlockPos standAt) {
        this.standAt = standAt;
        this.villager = villager;
        this.workstation = workstation;
        this.workstationItem = item;
        this.profession = profession;
        this.rule = rule;
        this.budget = budget;
    }

    /**
     * Picks the villager to reroll (the one you look at, else the nearest within 6 blocks with a profession) and its
     * workstation. Throws {@link IllegalStateException} with a readable reason when there is none.
     */
    public static Reroller start(Minecraft mc, TradeRule rule, TradeBudget budget) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) throw new IllegalStateException("not in a world");
        Villager target = null;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Villager v) target = v;
        if (target == null) {
            target = mc.level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(6),
                            v -> v.isAlive() && !v.isBaby() && !isNone(v.getVillagerData().profession()))
                    .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElse(null);
        }
        if (target == null) throw new IllegalStateException("no villager with a job within 6 blocks; look at one");
        Holder<VillagerProfession> profession = target.getVillagerData().profession();
        if (isNone(profession)) throw new IllegalStateException("that villager has no job yet; give it a workstation first");
        BlockPos station = findWorkstation(mc, target, profession);
        if (station == null) throw new IllegalStateException("no workstation for its job within 4 blocks of it");
        Item item = mc.level.getBlockState(station).getBlock().asItem();
        return new Reroller(target, station, item, profession, rule, budget, player.blockPosition());
    }

    private static BlockPos findWorkstation(Minecraft mc, Villager villager, Holder<VillagerProfession> profession) {
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            BlockState state = mc.level.getBlockState(pos);
            Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
            if (poi.isEmpty() || !profession.value().heldJobSite().test(poi.get())) continue;
            if (best == null || pos.distSqr(center) < best.distSqr(center)) best = pos.immutable();
        }
        return best;
    }

    private static boolean isNone(Holder<VillagerProfession> profession) {
        return profession.is(VillagerProfession.NONE);
    }

    public String status() {
        return status;
    }

    public int rerolls() {
        return rerolls;
    }

    /** What was bought to lock the trades, once {@link State#LOCKED}. */
    public String locked() {
        return locked;
    }

    public Villager villager() {
        return villager;
    }

    public void cancel(Minecraft mc) {
        if (mc.gameMode != null && phase == Phase.BREAK) mc.gameMode.stopDestroyBlock();
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    public State tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return fail("not in a world");
        if (!villager.isAlive() || villager.isRemoved()) return fail("the villager is gone");
        ticks++;
        switch (phase) {
            case OPEN -> {
                if (player.containerMenu instanceof MerchantMenu) {
                    phase = Phase.READ;
                    ticks = 0;
                    buyer = new MerchantBuyer(rule, budget, TradeOffers.profession(villager), 0, 1);
                    buying = false;
                    return State.WORKING;
                }
                if (player.distanceTo(villager) > REACH) return fail("the villager moved out of reach");
                if (ticks == 1 || ticks % 40 == 0) {
                    lookAt(mc, villager.getBoundingBox().getCenter());
                    Vec3 hit = villager.getBoundingBox().getCenter();
                    mc.gameMode.interact(player, villager, new EntityHitResult(villager, hit), InteractionHand.MAIN_HAND);
                }
                if (ticks > OPEN_TIMEOUT) return fail("the trade screen didn't open");
                status = "opening trades";
            }
            case READ -> {
                if (!(player.containerMenu instanceof MerchantMenu menu)) return fail("the trade screen closed");
                if (menu.getOffers().isEmpty()) {
                    if (ticks > OPEN_TIMEOUT) return fail("the villager sent no offers");
                    return State.WORKING;
                }
                // Checked only before buying: the purchase that locks the trades gives the villager xp itself.
                if (!buying && menu.getTraderXp() > 0) {
                    return fail("someone has traded with this villager (" + menu.getTraderXp() + " xp), so its trades are fixed");
                }
                boolean match = buying || TradeOffers.from(menu.getOffers()).stream().anyMatch(o -> rule.matches(o, TradeOffers.profession(villager)));
                if (!match) {
                    phase = Phase.CLOSE;
                    ticks = 0;
                    return State.WORKING;
                }
                buying = true;
                MerchantBuyer.State s = buyer.tick(mc);
                status = "locking: " + buyer.status();
                if (s == MerchantBuyer.State.WORKING) return State.WORKING;
                if (buyer.bought() > 0) {
                    locked = buyer.boughtText();
                    player.closeContainer();
                    status = "locked " + locked + " after " + rerolls + " reroll" + (rerolls == 1 ? "" : "s");
                    return State.LOCKED;
                }
                return fail("it matches but can't be bought: " + buyer.status());
            }
            case CLOSE -> {
                if (player.containerMenu instanceof MerchantMenu) player.closeContainer();
                long time = mc.level.getOverworldClockTime() % 24000L;
                if (time < 2000 || time >= 9000) {
                    status = "no match yet; waiting for villager work hours (daytime)";
                    return State.WORKING;
                }
                phase = Phase.BREAK;
                ticks = 0;
            }
            case BREAK -> {
                if (mc.level.getBlockState(workstation).isAir()) {
                    mc.gameMode.stopDestroyBlock();
                    rerolls++;
                    phase = Phase.WAIT_LOSS;
                    ticks = 0;
                    return State.WORKING;
                }
                if (player.getEyePosition().distanceTo(Vec3.atCenterOf(workstation)) > REACH) return fail("the workstation is out of reach");
                selectAxe(player);
                lookAt(mc, Vec3.atCenterOf(workstation));
                if (ticks == 1) mc.gameMode.startDestroyBlock(workstation, Direction.UP);
                else mc.gameMode.continueDestroyBlock(workstation, Direction.UP);
                DihEntities.swing(player, InteractionHand.MAIN_HAND);
                if (ticks > BREAK_TIMEOUT) return fail("couldn't break the workstation");
                status = "reroll " + (rerolls + 1) + ": breaking the workstation";
            }
            case WAIT_LOSS -> {
                if (isNone(villager.getVillagerData().profession())) {
                    phase = Phase.COLLECT;
                    ticks = 0;
                    return State.WORKING;
                }
                if (ticks > LOSS_TIMEOUT) return fail("the villager kept its job (has someone traded with it?)");
                status = "reroll " + rerolls + ": waiting for the villager to lose its job";
            }
            case COLLECT -> {
                if (count(player, workstationItem) > 0) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                    phase = Phase.PLACE;
                    ticks = 0;
                    placeTries = 0;
                    return State.WORKING;
                }
                List<ItemEntity> drops = mc.level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(workstation).inflate(6),
                        e -> e.isAlive() && e.getItem().is(workstationItem));
                if (!drops.isEmpty() && ticks % 20 == 1) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(drops.get(0).blockPosition()));
                }
                if (ticks > COLLECT_TIMEOUT) return fail("couldn't pick the workstation back up");
                status = "reroll " + rerolls + ": picking up the workstation";
            }
            case PLACE -> {
                if (!mc.level.getBlockState(workstation).isAir()) {
                    phase = Phase.WAIT_GAIN;
                    ticks = 0;
                    return State.WORKING;
                }
                if (player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(workstation))) {
                    // Picking the workstation up walked us into its spot; nothing can be placed inside the player.
                    if (player.blockPosition().equals(standAt)) {
                        // On the right block but against its edge: Baritone counts the goal as reached, so walk to the middle.
                        Vec3 toMiddle = Vec3.atBottomCenterOf(standAt).subtract(player.position()).multiply(1, 0, 1);
                        if (toMiddle.lengthSqr() > 1.0E-4) {
                            Vec3 step = toMiddle.normalize().scale(Math.min(0.1, toMiddle.length()));
                            player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
                        }
                    } else if (ticks % 20 == 1) {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(standAt));
                    }
                    if (ticks > COLLECT_TIMEOUT) return fail("couldn't step out of the workstation spot");
                    status = "reroll " + rerolls + ": stepping back";
                    return State.WORKING;
                }
                if (player.getEyePosition().distanceTo(Vec3.atCenterOf(workstation)) > REACH + 0.5) {
                    if (ticks % 20 == 1) BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                            .setGoalAndPath(new baritone.api.pathing.goals.GoalNear(workstation, 2));
                    if (ticks > COLLECT_TIMEOUT) return fail("couldn't get back to the workstation spot");
                    return State.WORKING;
                }
                if (ticks == 1 || ticks % PLACE_TIMEOUT == 0) {
                    if (++placeTries > 3) return fail("couldn't put the workstation back");
                    int slot = hotbarSlot(player, workstationItem);
                    if (slot < 0) return fail("the workstation isn't on the hotbar");
                    player.getInventory().setSelectedSlot(slot);
                    BlockPos below = workstation.below();
                    Vec3 face = Vec3.atCenterOf(below).add(0, 0.5, 0);
                    lookAt(mc, face);
                    mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(face, Direction.UP, below, false));
                    DihEntities.swing(player, InteractionHand.MAIN_HAND);
                }
                status = "reroll " + rerolls + ": putting the workstation back";
            }
            case WAIT_GAIN -> {
                if (villager.getVillagerData().profession().is(profession.unwrapKey().orElseThrow())) {
                    phase = Phase.OPEN;
                    ticks = 0;
                    return State.WORKING;
                }
                if (ticks > GAIN_TIMEOUT) return fail("the villager didn't take the job back");
                status = "reroll " + rerolls + ": waiting for the villager to take the job";
            }
        }
        return State.WORKING;
    }

    private State fail(String why) {
        status = why;
        return State.FAILED;
    }

    private static void lookAt(Minecraft mc, Vec3 point) {
        LocalPlayer player = mc.player;
        var rotation = RotationUtils.calcRotationFromVec3d(player.getEyePosition(), point,
                new baritone.api.utils.Rotation(player.getYRot(), player.getXRot()));
        player.setYRot(rotation.getYaw());
        player.setXRot(rotation.getPitch());
    }

    private static void selectAxe(LocalPlayer player) {
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9; i++) {
            if (main.get(i).is(ItemTags.AXES)) {
                player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private static int count(LocalPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) if (stack.is(item)) n += stack.getCount();
        return n;
    }

    private static int hotbarSlot(LocalPlayer player, Item item) {
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9; i++) if (main.get(i).is(item)) return i;
        for (int i = 9; i < main.size(); i++) {
            if (!main.get(i).is(item)) continue;
            // Swap it onto the selected hotbar slot, as a number-key press would.
            Minecraft mc = Minecraft.getInstance();
            int hotbar = player.getInventory().getSelectedSlot();
            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, i, hotbar,
                    net.minecraft.world.inventory.ContainerInput.SWAP, player);
            return hotbar;
        }
        return -1;
    }

    /** For status lines: the entity being rerolled. */
    public Entity target() {
        return villager;
    }
}
