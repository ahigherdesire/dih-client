package baritone.acquire.exec;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import baritone.behavior.Behavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Eats one item on request: {@code #eat}, and {@link AcquireProcess} when health comes first. Never on its
 * own; there is no always-on auto-eat.
 *
 * <p>A meal puts the food on the hotbar, selects it, starts using it with {@code useItem} (so the
 * crosshair never opens a chest or a door) and holds the real "use" key until the item count drops.
 * Vanilla lets go of an item in use as soon as "use" is up, so the key is re-asserted every tick.
 * It is always released afterwards: when the meal ends or fails, on {@code #stop} (through the pause
 * process's {@code onLostControl}), on death, when a screen opens and when the world is left.
 *
 * <p>While a meal runs, {@link #pauser()} (a temporary Baritone process above {@code #acquire}) pauses
 * pathing without taking control away from the process it pauses, so mining carries on afterwards.
 */
public final class EatBehavior extends Behavior implements Helper {

    private final Pauser pauser = new Pauser();
    private Meal meal;
    private Consumer<String> onEnd;
    private boolean holding;
    private boolean startedUse;
    private int previousSlot = -1;
    private int selectedSlot = -1;

    public EatBehavior(Baritone baritone) {
        super(baritone);
    }

    /** The process that pauses pathing while a meal runs. Registered by {@link Baritone}. */
    public IBaritoneProcess pauser() {
        return pauser;
    }

    public boolean isBusy() {
        return meal != null;
    }

    /** The item being eaten, or null. */
    public String eating() {
        return meal == null ? null : meal.item;
    }

    /**
     * Starts eating {@code item} (a namespaced id). {@code onEnd} gets null once it is eaten, or why not.
     * Returns why the meal can't start, or null when it started. Game thread.
     */
    public String start(String item, Consumer<String> onEnd) {
        LocalPlayer player = ctx.player();
        if (player == null) return "not in a world";
        if (meal != null) return "already eating " + Meal.shortId(meal.item);
        ItemStack stack = find(player, InventoryReader.itemOf(item));
        if (stack == null) return "no " + Meal.shortId(item) + " in your inventory";
        this.meal = new Meal(item, Foods.eatTicks(stack));
        this.onEnd = onEnd;
        return null;
    }

    /** Stops the meal, if any, and lets go of "use". */
    public void cancel(String why) {
        if (meal == null) return;
        meal.cancel(why);
        release();
        end();
    }

    /**
     * {@code #eat} / {@code #eat <item>}: picks the food (see {@link FoodChoice}) or checks the one named,
     * starts eating and returns the chat line. Throws {@link IllegalArgumentException} with a readable
     * reason when there is nothing to eat.
     */
    public String eatNow(String itemText) {
        LocalPlayer player = ctx.player();
        if (player == null) throw new IllegalArgumentException("Join a world first.");
        if (meal != null) throw new IllegalArgumentException("Already eating " + Meal.shortId(meal.item) + ".");
        int food = player.getFoodData().getFoodLevel();
        String item;
        if (itemText == null || itemText.isBlank()) {
            item = pickForCommand(player, food);
        } else {
            item = checkNamed(player, itemText.trim());
        }
        String line = "Eating " + Meal.shortId(item) + " (" + HealthPolicy.hearts(player.getHealth()) + ", food " + food + "/20)";
        String why = start(item, failure -> {
            LocalPlayer p = ctx.player();
            if (failure != null) logDirect("Couldn't eat " + Meal.shortId(item) + ": " + failure + ".");
            else if (p != null) logDirect("Ate " + Meal.shortId(item) + ": " + HealthPolicy.hearts(p.getHealth())
                    + ", food " + p.getFoodData().getFoodLevel() + "/20.");
        });
        if (why != null) throw new IllegalArgumentException("Can't eat: " + why + ".");
        return line;
    }

    private String pickForCommand(LocalPlayer player, int food) {
        List<FoodChoice.Food> held = Foods.held(player);
        if (held.isEmpty()) throw new IllegalArgumentException("Nothing to eat. #acquire food gets some.");
        HealthPolicy.Need need = HealthPolicy.need(player.getHealth(), player.getAbsorptionAmount(), player.getMaxHealth(),
                food, Baritone.settings().acquireEmergencyHealth.value);
        FoodChoice.Food pick = FoodChoice.choose(held, food, need == HealthPolicy.Need.EMERGENCY, java.util.Set.of());
        if (pick != null) return pick.id();
        boolean golden = held.stream().anyMatch(FoodChoice.Food::golden);
        if (food >= HealthPolicy.MAX_FOOD) {
            throw new IllegalArgumentException("You're full (food 20/20)." + (golden
                    ? " Golden apples are kept for emergencies; #eat golden_apple eats one anyway." : ""));
        }
        FoodChoice.Food other = held.stream().filter(f -> !f.golden()).findFirst().orElse(null);
        if (other != null) {
            throw new IllegalArgumentException("Only harmful food held (" + Meal.shortId(other.id())
                    + "); it's eaten only when starving. #eat " + Meal.shortId(other.id()) + " eats it anyway.");
        }
        throw new IllegalArgumentException("Only golden apples held; they're kept for emergencies. #eat golden_apple eats one anyway.");
    }

    private String checkNamed(LocalPlayer player, String text) {
        String id = text.toLowerCase(Locale.ROOT).replace(' ', '_');
        Item item = InventoryReader.itemOf(id);
        if (item == null) throw new IllegalArgumentException("Unknown item '" + text + "'.");
        ItemStack stack = find(player, item);
        String name = Meal.shortId(InventoryReader.idOf(new ItemStack(item)));
        if (stack == null) throw new IllegalArgumentException("No " + name + " in your inventory.");
        FoodProperties props = stack.get(DataComponents.FOOD);
        if (props == null || Foods.of(stack) == null) throw new IllegalArgumentException(name + " isn't food.");
        if (!player.canEat(props.canAlwaysEat())) throw new IllegalArgumentException("You're full (food 20/20); " + name + " can't be eaten now.");
        return InventoryReader.idOf(stack);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            // Leaving the world: nothing to stop using, just let go of the key.
            if (meal != null) meal.cancel("you left the world");
            release();
            if (meal != null) end();
            return;
        }
        if (meal == null) return;
        LocalPlayer player = ctx.player();
        Minecraft mc = ctx.minecraft();
        Meal.Action action = meal.tick(player.isDeadOrDying(), mc.gui.screen() != null,
                InventoryReader.count(player, meal.item), player.isUsingItem());
        switch (action) {
            case WAIT, RELEASE -> release();
            case START -> {
                String why = begin(player);
                if (why != null) meal.cancel(why);
            }
            case HOLD -> hold();
        }
        if (meal.finished()) {
            release();
            end();
        }
    }

    /** Food in hand, use started, key held. Returns why not, or null. */
    private String begin(LocalPlayer player) {
        Item item = InventoryReader.itemOf(meal.item);
        if (item == null) return "unknown item";
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (!player.getMainHandItem().is(item)) {
            if (player.getOffhandItem().is(item)) {
                hand = InteractionHand.OFF_HAND;
            } else {
                int slot = InventoryOps.toHotbar(ctx, stack -> stack.is(item));
                if (slot < 0) return "can't get it onto the hotbar";
                int now = player.getInventory().getSelectedSlot();
                if (previousSlot < 0) previousSlot = now;
                player.getInventory().setSelectedSlot(slot);
                selectedSlot = slot;
            }
        }
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        InteractionResult result = ctx.minecraft().gameMode.useItem(player, hand);
        if (!result.consumesAction() || !player.isUsingItem()) return "the game wouldn't start eating it (full?)";
        startedUse = true;
        hold();
        return null;
    }

    private void hold() {
        Minecraft mc = ctx.minecraft();
        if (mc.options == null) return;
        mc.options.keyUse.setDown(true);
        holding = true;
    }

    /** Lets go of "use" if this held it, and stops using the item if this started it. */
    private void release() {
        Minecraft mc = Minecraft.getInstance();
        if (holding && mc.options != null) mc.options.keyUse.setDown(false);
        holding = false;
        LocalPlayer player = ctx.player();
        if (startedUse && player != null && player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(player);
        startedUse = false;
    }

    private void end() {
        Meal ended = meal;
        Consumer<String> callback = onEnd;
        meal = null;
        onEnd = null;
        LocalPlayer player = ctx.player();
        // Back to what was held before (a sword mid-fight), unless something else changed the slot since.
        if (player != null && previousSlot >= 0 && player.getInventory().getSelectedSlot() == selectedSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        selectedSlot = -1;
        if (callback != null && ended != null) {
            try {
                callback.accept(ended.ate() ? null : ended.failure());
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private static ItemStack find(LocalPlayer player, Item item) {
        if (item == null) return null;
        for (ItemStack stack : InventoryReader.stacks(player)) {
            if (!stack.isEmpty() && stack.is(item)) return stack;
        }
        return null;
    }

    /** Pauses pathing while a meal runs; temporary, so the paused process keeps control. */
    private final class Pauser implements IBaritoneProcess {
        @Override
        public boolean isActive() {
            return meal != null && ctx.player() != null;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
            // #stop, a disconnect, or a non-temporary process above this one taking over.
            cancel("stopped");
        }

        @Override
        public double priority() {
            // Above #acquire (-0.5), below #pause (0).
            return DEFAULT_PRIORITY + 0.75;
        }

        @Override
        public String displayName0() {
            Meal m = meal;
            return m == null ? "Eat" : "Eating " + Meal.shortId(m.item);
        }
    }
}
