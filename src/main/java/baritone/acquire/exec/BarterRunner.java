package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Step.Barter}: puts a gold armour piece on, then throws gold ingots to adult piglins, one each while they look
 * it over, and picks up what the step wants from what they throw back ({@link BarterTactics} decides which). Ends when
 * the inventory holds the step's count; runs out of gold as a failure, so the rest is planned another way.
 */
final class BarterRunner extends RunnerBase {

    private static final String PIGLIN = "minecraft:piglin";
    /** Gold armour, the cheapest first, with the armour slot of each in the player's inventory menu. */
    private static final List<String> GOLD_ARMOUR = List.of("minecraft:golden_boots", "minecraft:golden_helmet",
            "minecraft:golden_leggings", "minecraft:golden_chestplate");
    private static final int[] ARMOUR_SLOTS = {InventoryMenu.ARMOR_SLOT_START + 3, InventoryMenu.ARMOR_SLOT_START,
            InventoryMenu.ARMOR_SLOT_START + 2, InventoryMenu.ARMOR_SLOT_START + 1};
    private static final double PIGLIN_RANGE = 32;
    private static final double GROUND_GOLD_RANGE = 8;
    /** Give up looking for piglins after this long. */
    private static final int EXPLORE_TICKS = 2400;
    /** The throw waits until the player faces the piglin this closely, so the server throws it that way. */
    private static final float AIM_DEGREES = 4;

    private final Step.Barter step;
    private final Item currency;
    private final Item wanted;
    private final Map<Integer, Integer> thrownAt = new HashMap<>();
    private int ticks;
    private int lastThrow = Integer.MIN_VALUE / 2;
    private int exploring;
    private GoalXZ explore;

    BarterRunner(ExecContext x, Step.Barter step) {
        super(x);
        this.step = step;
        this.currency = InventoryReader.itemOf(step.currency());
        this.wanted = InventoryReader.itemOf(step.item());
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (!PIGLIN.equals(step.entity()) || currency == null) return Result.failed("can't barter with " + step.entity());
        ticks++;
        Result full = x.checkRoom(step.item());
        if (full != null) return full;
        LocalPlayer player = ctx.player();
        List<Piglin> piglins = piglins();
        ItemEntity loot = nearestLoot();
        BarterTactics.Situation situation = BarterTactics.Situation.builder()
                .have(x.have(step.item())).want(step.untilCount()).gold(x.have(step.currency()))
                .wearingGold(PiglinAi.isWearingSafeArmor(player)).goldPieceHeld(goldPiece() >= 0)
                .lootDistance(loot == null ? Double.POSITIVE_INFINITY : loot.distanceTo(player))
                .piglins(piglins.stream().map(this::seen).toList())
                .sinceThrow(ticks - lastThrow).goldOnGround(goldOnGround())
                .build();
        BarterTactics.Decision decision = BarterTactics.decide(situation);
        if (decision.move() != BarterTactics.Move.EXPLORE) exploring = 0;
        return switch (decision.move()) {
            case DONE -> Result.done();
            case NO_ARMOUR -> Result.failed("no gold armour to wear: piglins attack a player with none on");
            case OUT_OF_GOLD -> Result.failed("out of " + Step.shortId(step.currency()) + " with "
                    + x.have(step.item()) + " of " + step.untilCount() + " " + Step.shortId(step.item()));
            case WEAR -> {
                wear();
                yield Result.pause();
            }
            case LOOT -> calcFailed ? Result.pause() : follow(new GoalBlock(loot.blockPosition()));
            case APPROACH -> {
                Piglin target = byId(piglins, decision.piglin());
                yield calcFailed ? Result.pause() : follow(new GoalNear(target.blockPosition(), 2));
            }
            case THROW -> throwTo(byId(piglins, decision.piglin()));
            case WAIT -> Result.pause();
            case EXPLORE -> {
                if (++exploring > EXPLORE_TICKS) yield Result.failed("no piglins found to barter with");
                if (explore == null || explore.isInGoal(ctx.playerFeet()) || calcFailed) {
                    explore = GoalXZ.fromDirection(player.position(), player.getYRot() + (calcFailed ? 90 : 0), 48);
                }
                yield walk(explore);
            }
        };
    }

    @Override
    public void cancel() {
        // Nothing held open: the throws are single clicks.
    }

    /** Adult piglins in range, alive. */
    private List<Piglin> piglins() {
        LocalPlayer player = ctx.player();
        List<Piglin> out = new ArrayList<>();
        for (Entity e : ctx.entitiesStream().toList()) {
            if (e instanceof Piglin piglin && piglin.isAlive() && !piglin.isBaby() && piglin.distanceTo(player) <= PIGLIN_RANGE) {
                out.add(piglin);
            }
        }
        return out;
    }

    private BarterTactics.Piglin seen(Piglin piglin) {
        Integer thrown = thrownAt.get(piglin.getId());
        int since = thrown == null ? Integer.MAX_VALUE : ticks - thrown;
        return new BarterTactics.Piglin(piglin.getId(), piglin.distanceTo(ctx.player()),
                piglin.getArmPose() == PiglinArmPose.ADMIRING_ITEM, since);
    }

    private static Piglin byId(List<Piglin> piglins, int id) {
        for (Piglin piglin : piglins) if (piglin.getId() == id) return piglin;
        throw new IllegalStateException("piglin " + id + " went missing");
    }

    /** Faces the piglin's feet, then (the tick after, once the server has that rotation) throws one ingot. */
    private Result throwTo(Piglin piglin) {
        LocalPlayer player = ctx.player();
        Rotation want = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), piglin.position().add(0, 0.3, 0), ctx.playerRotations());
        boolean facing = Math.abs(Mth.wrapDegrees(player.getYRot() - want.getYaw())) <= AIM_DEGREES
                && Math.abs(player.getXRot() - want.getPitch()) <= AIM_DEGREES;
        if (!facing) {
            player.setYRot(want.getYaw());
            player.setXRot(want.getPitch());
            return Result.pause();
        }
        int index = InventoryReader.find(player, stack -> stack.is(currency));
        if (index < 0 || !InventoryOps.inventoryMenuOpen(player)) return Result.pause();
        // Button 0 throws one item of the stack, the way the player is facing.
        ctx.playerController().windowClick(player.inventoryMenu.containerId, InventoryOps.inventoryMenuSlot(index), 0,
                ContainerInput.THROW, player);
        thrownAt.put(piglin.getId(), ticks);
        lastThrow = ticks;
        return Result.pause();
    }

    /** Swaps a held gold armour piece into its slot, by way of the hotbar. */
    private void wear() {
        LocalPlayer player = ctx.player();
        int which = goldPiece();
        if (which < 0 || !InventoryOps.inventoryMenuOpen(player)) return;
        String id = GOLD_ARMOUR.get(which);
        int hotbar = InventoryOps.toHotbar(ctx, stack -> id.equals(InventoryReader.idOf(stack)));
        if (hotbar < 0) return;
        ctx.playerController().windowClick(player.inventoryMenu.containerId, ARMOUR_SLOTS[which], hotbar, ContainerInput.SWAP, player);
    }

    /** Which of {@link #GOLD_ARMOUR} is held in the inventory, or -1. */
    private int goldPiece() {
        return goldArmourHeld(ctx.player());
    }

    /** Which of the gold armour pieces {@code player} carries (worn or not), the cheapest first, or -1. */
    static int goldArmourHeld(net.minecraft.world.entity.player.Player player) {
        for (int i = 0; i < GOLD_ARMOUR.size(); i++) if (InventoryReader.count(player, GOLD_ARMOUR.get(i)) > 0) return i;
        return -1;
    }

    private ItemEntity nearestLoot() {
        if (wanted == null) return null;
        LocalPlayer player = ctx.player();
        return ctx.entitiesStream()
                .filter(e -> e instanceof ItemEntity item && item.isAlive() && item.getItem().is(wanted))
                .filter(e -> e.distanceTo(player) <= BarterTactics.LOOT_RADIUS)
                .min(Comparator.comparingDouble((Entity e) -> e.distanceToSqr(player)))
                .map(e -> (ItemEntity) e)
                .orElse(null);
    }

    private int goldOnGround() {
        Vec3 at = ctx.player().position();
        return (int) ctx.entitiesStream()
                .filter(e -> e instanceof ItemEntity item && item.isAlive() && item.getItem().is(currency))
                .filter(e -> e.position().distanceTo(at) <= GROUND_GOLD_RANGE)
                .count();
    }
}
