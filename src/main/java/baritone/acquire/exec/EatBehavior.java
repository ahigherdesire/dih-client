package baritone.acquire.exec;

import baritone.Baritone;
import baritone.acquire.model.Step;
import baritone.api.event.events.TickEvent;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.behavior.Behavior;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Eats one item for {@code #eat} and {@code #acquire}: puts it in hand, starts using it, holds the use
 * key until it is eaten, then puts the old hotbar slot back. While it eats, {@link #pauser()} holds
 * Baritone still without cancelling what it was doing (mining resumes afterwards).
 *
 * <p>Only one meal at a time. Every meal ends in exactly one call of its callback, on the game thread:
 * null when the item was eaten, else why not.
 */
public final class EatBehavior extends Behavior {

    /** Eating takes 32 ticks (dried kelp 16); give up well after that. */
    private static final int MAX_TICKS = 5 * 20;
    /** Hotbar slots for the food: 0 and 8 hold Baritone's pickaxe and throwaway blocks. */
    private static final int FIRST_SLOT = 1;
    private static final int LAST_SLOT = 7;

    private String item;
    private Consumer<String> onDone;
    private InteractionHand hand;
    private int slot;
    private int previousSlot = -1;
    private int before;
    private int ticks;

    private final IBaritoneProcess pauser = new IBaritoneProcess() {
        @Override
        public boolean isActive() {
            return item != null;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true; // the process below keeps its state and carries on after the meal
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return DEFAULT_PRIORITY + 0.75; // above #acquire (+0.5), below #pause (+1)
        }

        @Override
        public String displayName0() {
            return "Eating";
        }
    };

    public EatBehavior(Baritone baritone) {
        super(baritone);
    }

    /** The pause process; registered with the control manager once. */
    public IBaritoneProcess pauser() {
        return pauser;
    }

    public boolean isBusy() {
        return item != null;
    }

    /** What is being eaten, or null. */
    public String eating() {
        return item;
    }

    /**
     * Starts eating one {@code id}. Returns null when the meal started ({@code done} runs when it ends),
     * else why it could not start ({@code done} is not called).
     */
    public String start(String id, Consumer<String> done) {
        if (item != null) return "already eating " + item;
        LocalPlayer player = ctx.player();
        Minecraft mc = ctx.minecraft();
        if (player == null || ctx.world() == null) return "not in a world";
        if (player.isDeadOrDying()) return "you are dead";
        if (mc.gui.screen() != null) return "a screen is open";
        if (player.isUsingItem()) return "already using an item";
        Item food = InventoryReader.itemOf(id);
        if (food == null) return "unknown item " + id;
        if (!inHand(player, food)) return "no " + Step.shortId(id) + " to eat";

        before = InventoryReader.count(player, id);
        ctx.playerController().processRightClick(player, ctx.world(), hand);
        if (!player.isUsingItem() || !player.getUseItem().is(food)) {
            restoreSlot(player);
            return "couldn't start eating " + Step.shortId(id) + (player.getFoodData().needsFood() ? "" : " (not hungry)");
        }
        item = id;
        onDone = done;
        ticks = 0;
        mc.options.keyUse.setDown(true);
        return null;
    }

    /**
     * {@code #eat}: eats {@code itemText} (an id or plain words), or with null the food {@link FoodChoice}
     * picks for now. Returns the food's id once the meal started; throws {@link IllegalArgumentException}
     * with a message for chat when there is nothing to eat.
     */
    public String eatNow(String itemText, Consumer<String> done) {
        LocalPlayer player = ctx.player();
        if (player == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        if (item != null) throw new IllegalArgumentException("Already eating " + Step.shortId(item) + ".");
        List<FoodChoice.Food> held = Foods.held(player);
        int food = player.getFoodData().getFoodLevel();
        String id;
        if (itemText != null && !itemText.isBlank()) {
            Item wanted = InventoryReader.itemOf(itemText.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
            if (wanted == null) throw new IllegalArgumentException("No item called " + itemText.trim() + ".");
            id = InventoryReader.idOf(new ItemStack(wanted));
            FoodChoice.Food f = held.stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
            if (f == null) {
                throw new IllegalArgumentException(Foods.of(new ItemStack(wanted)) == null
                        ? Step.shortId(id) + " isn't food." : "You have no " + Step.shortId(id) + ".");
            }
            if (food >= HealthPolicy.MAX_FOOD && !f.alwaysEdible()) throw new IllegalArgumentException("You're not hungry.");
        } else {
            HealthPolicy.Need need = HealthPolicy.need(player.getHealth(), player.getAbsorptionAmount(), player.getMaxHealth(),
                    food, Baritone.settings().acquireEmergencyHealth.value);
            FoodChoice.Food pick = FoodChoice.choose(held, food, need == HealthPolicy.Need.EMERGENCY, Set.of());
            if (pick == null) {
                if (food >= HealthPolicy.MAX_FOOD) throw new IllegalArgumentException("You're not hungry.");
                throw new IllegalArgumentException("Nothing safe to eat. #acquire food gets some, or #eat <item> eats it anyway.");
            }
            id = pick.id();
        }
        String why = start(id, done);
        if (why != null) throw new IllegalArgumentException("Can't eat: " + why + ".");
        return id;
    }

    /** Stops the meal, if any; its callback gets {@code reason}. */
    public void cancel(String reason) {
        if (item != null) end(reason);
    }

    @Override
    public void onTick(TickEvent event) {
        if (item == null || event.getType() == TickEvent.Type.OUT) return;
        LocalPlayer player = ctx.player();
        if (player == null || player.isDeadOrDying()) {
            end("you died");
            return;
        }
        if (ctx.minecraft().gui.screen() != null) {
            end("a screen opened");
            return;
        }
        Item food = InventoryReader.itemOf(item);
        if (player.isUsingItem() && player.getUseItem().is(food)) {
            if (++ticks > MAX_TICKS) {
                end("eating took too long");
                return;
            }
            ctx.minecraft().options.keyUse.setDown(true);
            return;
        }
        end(InventoryReader.count(player, item) < before ? null : "stopped before finishing " + Step.shortId(item));
    }

    private void end(String failure) {
        Consumer<String> done = onDone;
        item = null;
        onDone = null;
        Minecraft mc = ctx.minecraft();
        DihKeyMappingBridge.of(mc.options.keyUse).dih$resetPressedState();
        LocalPlayer player = ctx.player();
        if (player != null) {
            if (failure != null && player.isUsingItem()) mc.gameMode.releaseUsingItem(player);
            restoreSlot(player);
        }
        if (done != null) done.accept(failure);
    }

    /**
     * Puts {@code food} in a hand: the offhand or selected slot if it is already there, else a hotbar
     * slot, else swaps it onto the hotbar from the main inventory (only with no container open).
     */
    private boolean inHand(LocalPlayer player, Item food) {
        previousSlot = -1;
        if (player.getOffhandItem().is(food)) {
            hand = InteractionHand.OFF_HAND;
            return true;
        }
        hand = InteractionHand.MAIN_HAND;
        if (player.getMainHandItem().is(food)) return true;
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        int selected = player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (main.get(i).is(food)) {
                select(player, i, selected);
                return true;
            }
        }
        int from = InventoryReader.find(player, stack -> stack.is(food));
        if (from < 9 || !InventoryOps.inventoryMenuOpen(player)) return false;
        int dest = -1;
        for (int i = FIRST_SLOT; i <= LAST_SLOT && dest < 0; i++) {
            if (main.get(i).isEmpty()) dest = i;
        }
        for (int i = LAST_SLOT; i >= FIRST_SLOT && dest < 0; i--) {
            if (i != selected && !main.get(i).has(DataComponents.TOOL)) dest = i;
        }
        if (dest < 0) dest = LAST_SLOT;
        ctx.playerController().windowClick(player.inventoryMenu.containerId, from, dest, ContainerInput.SWAP, player);
        if (!main.get(dest).is(food)) return false;
        select(player, dest, selected);
        return true;
    }

    private void select(LocalPlayer player, int slot, int selected) {
        if (slot == selected) return;
        this.slot = slot;
        previousSlot = selected;
        player.getInventory().setSelectedSlot(slot);
    }

    /** Back to the slot held before the meal, unless something else has changed the selection since. */
    private void restoreSlot(LocalPlayer player) {
        if (previousSlot >= 0 && player.getInventory().getSelectedSlot() == slot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }

}
