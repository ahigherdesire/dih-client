package baritone.acquire.exec;

import baritone.acquire.model.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link Step.Smelt}: walk to the nearest usable furnace (or blast furnace / smoker), open it, put the
 * input in slot 0 and the fuel in slot 1, and stand by with the screen open, taking the output from
 * slot 2 as it comes. The screen closes when the input is used up or the count is reached. A furnace
 * busy with something else is skipped for the rest of the run.
 *
 * <p>Slot layout of {@link AbstractFurnaceMenu} in 26.2: 0 ingredient, 1 fuel, 2 result, then the
 * player inventory. The runner finds inventory slots by container rather than by index.
 */
final class SmeltRunner extends RunnerBase {

    private enum State { FIND, WALK, OPEN, AWAIT_MENU, LOAD, COOK }

    private static final int INPUT = AbstractFurnaceMenu.INGREDIENT_SLOT;
    private static final int FUEL = AbstractFurnaceMenu.FUEL_SLOT;
    private static final int RESULT = AbstractFurnaceMenu.RESULT_SLOT;
    private static final int MENU_TIMEOUT = 60;
    private static final int MAX_OPENS = 4;
    /** Unlit with input left for this long means it ran out of fuel (one item cooks in 200 ticks). */
    private static final int STALL_TICKS = 300;

    private final Step.Smelt step;
    private final Block block;
    private State state = State.FIND;
    private BlockPos furnace;
    private int ticks;
    private int opens;
    private boolean loaded;
    private boolean weOpened;
    private long lastProgress = Long.MIN_VALUE;
    private int stall;

    SmeltRunner(ExecContext x, Step.Smelt step) {
        super(x);
        this.step = step;
        this.block = StationFinder.block(step.recipe().station());
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (block == null) return Result.failed("unknown station " + step.recipe().station());
        if (x.have(step.item()) >= step.untilCount()) {
            closeOurs();
            return Result.done();
        }
        for (int guard = 0; guard < 5; guard++) {
            switch (state) {
                case FIND -> {
                    if (!loaded && x.have(step.input()) <= 0) return Result.failed("no " + Step.shortId(step.input()) + " to smelt");
                    List<BlockPos> found = x.stations.find(step.recipe().station(), x.stationRadius());
                    if (found.isEmpty()) return Result.failed("no " + Step.shortId(step.recipe().station()) + " within " + x.stationRadius() + " blocks");
                    furnace = found.get(0);
                    state = State.WALK;
                }
                case WALK -> {
                    if (!ctx.world().getBlockState(furnace).is(block)) {
                        state = State.FIND;
                        continue;
                    }
                    Result walking = approach(furnace, calcFailed);
                    if (walking == null) {
                        state = State.OPEN;
                        ticks = 0;
                        continue;
                    }
                    if (walking.kind() == Result.Kind.FAILED) {
                        x.stations.markUnusable(furnace);
                        state = State.FIND;
                        return Result.pause();
                    }
                    return walking;
                }
                case OPEN -> {
                    if (!safeToCancel) return Result.pause();
                    if (!InventoryOps.inventoryMenuOpen(ctx.player())) ctx.player().closeContainer();
                    Optional<BlockHitResult> hit = aim(furnace);
                    if (++ticks < 3 || hit.isEmpty()) {
                        if (ticks > 20) {
                            // Never got a clean look at it: try another furnace.
                            x.stations.markUnusable(furnace);
                            state = loaded ? State.WALK : State.FIND;
                            ticks = 0;
                        }
                        return Result.pause();
                    }
                    use(hit.get(), InteractionHand.MAIN_HAND);
                    opens++;
                    weOpened = true;
                    state = State.AWAIT_MENU;
                    ticks = 0;
                    return Result.pause();
                }
                case AWAIT_MENU -> {
                    // The menu opens empty; its contents (and a state id above 0) arrive in a later packet.
                    AbstractFurnaceMenu opened = menu();
                    if (opened != null && opened.getStateId() > 0 && ++ticks >= 3) {
                        state = loaded ? State.COOK : State.LOAD;
                        continue;
                    }
                    if (opened == null && ++ticks > MENU_TIMEOUT) {
                        if (opens >= MAX_OPENS) {
                            x.stations.markUnusable(furnace);
                            return Result.failed("the " + Step.shortId(step.recipe().station()) + " at " + furnace.toShortString() + " won't open");
                        }
                        state = State.WALK;
                    }
                    return Result.pause();
                }
                case LOAD -> {
                    AbstractFurnaceMenu menu = menu();
                    if (menu == null) {
                        state = State.WALK;
                        continue;
                    }
                    Result busy = load(menu);
                    if (busy != null) return busy;
                    loaded = true;
                    state = State.COOK;
                    stall = 0;
                    return Result.pause();
                }
                case COOK -> {
                    AbstractFurnaceMenu menu = menu();
                    if (menu == null) {
                        // The screen was closed (Esc, or the furnace broke): go back and reopen it.
                        if (opens >= MAX_OPENS) return Result.failed("the furnace screen keeps closing");
                        state = State.WALK;
                        continue;
                    }
                    ItemStack result = menu.getSlot(RESULT).getItem();
                    if (!result.isEmpty()) InventoryOps.quickMove(ctx, menu.containerId, RESULT);
                    ItemStack input = menu.getSlot(INPUT).getItem();
                    if (input.isEmpty() && menu.getSlot(RESULT).getItem().isEmpty()) {
                        closeOurs();
                        return Result.done(); // used up; the process checks the count and re-plans if short
                    }
                    long progress = (long) input.getCount() << 32 | x.have(step.item());
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        stall = 0;
                    } else if (++stall > STALL_TICKS && !menu.isLit()) {
                        closeOurs();
                        return Result.failed("the furnace stopped with " + input.getCount() + " " + Step.shortId(step.input()) + " left (out of fuel?)");
                    }
                    return Result.pause();
                }
            }
        }
        return Result.pause();
    }

    /** Takes old output, then tops slot 0 up to {@code times} of the input and slot 1 up to {@code fuelCount} of the fuel. */
    private Result load(AbstractFurnaceMenu menu) {
        if (!menu.getSlot(RESULT).getItem().isEmpty()) InventoryOps.quickMove(ctx, menu.containerId, RESULT);
        Item inputItem = InventoryReader.itemOf(step.input());
        ItemStack in = menu.getSlot(INPUT).getItem();
        if (inputItem == null) return Result.failed("unknown item " + step.input());
        if (!in.isEmpty() && !in.is(inputItem)) {
            x.stations.markUnusable(furnace);
            closeOurs();
            return Result.failed("the furnace at " + furnace.toShortString() + " is busy with " + InventoryReader.idOf(in));
        }
        fill(menu, inputItem, INPUT, step.times() - in.getCount(), in);

        Item fuelItem = InventoryReader.itemOf(step.fuel());
        ItemStack fuel = menu.getSlot(FUEL).getItem();
        // A different fuel already in the slot stays; the furnace burns it and the stall check catches a shortfall.
        if (step.fuelCount() > 0 && fuelItem != null && (fuel.isEmpty() || fuel.is(fuelItem))) {
            fill(menu, fuelItem, FUEL, step.fuelCount() - fuel.getCount(), fuel);
        }
        return null;
    }

    /** Moves up to {@code amount} of {@code item} from the player's slots into furnace slot {@code target}. */
    private void fill(AbstractFurnaceMenu menu, Item item, int target, int amount, ItemStack current) {
        int room = (current.isEmpty() ? item.getDefaultMaxStackSize() : current.getMaxStackSize()) - current.getCount();
        amount = Math.min(amount, room);
        if (amount <= 0) return;
        Inventory inventory = ctx.player().getInventory();
        List<Integer> slots = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getItem().is(item)) {
                slots.add(slot.index);
                counts.add(slot.getItem().getCount());
            }
        }
        int[] s = slots.stream().mapToInt(Integer::intValue).toArray();
        int[] c = counts.stream().mapToInt(Integer::intValue).toArray();
        for (ClickPlan.Click click : ClickPlan.transfer(s, c, target, amount)) {
            InventoryOps.click(ctx, menu.containerId, click);
        }
    }

    private AbstractFurnaceMenu menu() {
        return ctx.player().containerMenu instanceof AbstractFurnaceMenu menu ? menu : null;
    }

    private void closeOurs() {
        if (weOpened && menu() != null) ctx.player().closeContainer();
        weOpened = false;
    }

    @Override
    public void cancel() {
        closeOurs();
    }
}
