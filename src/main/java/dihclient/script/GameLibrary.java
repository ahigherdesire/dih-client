package dihclient.script;

import dihclient.commands.DihCommands;
import dihclient.modules.DihAntiBot;
import dihclient.modules.TeamsModule;
import dihclient.script.lang.Interpreter;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import dihclient.util.DihGuiActions;
import dihclient.util.DihInventoryClickHelper;
import dihclient.util.DihInventoryHelper;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.macro.ItemTarget;
import dihclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Built-ins that read or drive the game. Scripts run on their own thread, so everything that touches
 * Minecraft state is marshalled onto the client thread with {@link #onClient}.
 */
public final class GameLibrary {
    private static final double REACH = 4.5;
    private static final int MAX_SCAN_RADIUS = 32;

    private GameLibrary() {
    }

    public static Library create() {
        Library lib = new Library();
        definePlayer(lib);
        defineChat(lib);
        defineBaritone(lib);
        defineInventory(lib);
        defineGui(lib);
        defineWorld(lib);
        return lib;
    }

    // ---------------------------------------------------------------- player

    private static void definePlayer(Library lib) {
        lib.define("Player", "inWorld()", "True while you are in a world/server.", 0, 0,
            a -> onClient(() -> mc().player != null && mc().level != null));
        lib.define("Player", "health()", "Your health (20 = full, 2 per heart).", 0, 0, a -> onPlayer(p -> (double) p.getHealth()));
        lib.define("Player", "maxHealth()", "Your maximum health.", 0, 0, a -> onPlayer(p -> (double) p.getMaxHealth()));
        lib.define("Player", "food()", "Your hunger bar (20 = full).", 0, 0, a -> onPlayer(p -> (double) p.getFoodData().getFoodLevel()));
        lib.define("Player", "x()", "Your X coordinate.", 0, 0, a -> onPlayer(p -> p.getX()));
        lib.define("Player", "y()", "Your Y coordinate.", 0, 0, a -> onPlayer(p -> p.getY()));
        lib.define("Player", "z()", "Your Z coordinate.", 0, 0, a -> onPlayer(p -> p.getZ()));
        lib.define("Player", "dimension()", "\"overworld\", \"the_nether\" or \"the_end\".", 0, 0,
            a -> onPlayer(p -> p.level().dimension().identifier().getPath()));
        lib.define("Player", "playerName()", "Your username.", 0, 0, a -> onPlayer(p -> p.getName().getString()));
        lib.define("Player", "playerNearby(radius)", "True if another player is within radius blocks (default 32). Friends, teammates and bots don't count.", 0, 1,
            a -> !nearbyPlayers(a.number(0, 32)).isEmpty());
        lib.define("Player", "nearestPlayer(radius)", "Name of the closest other player within radius (default 32), or null.", 0, 1, a -> {
            List<Object> names = nearbyPlayers(a.number(0, 32));
            return names.isEmpty() ? null : names.get(0);
        });
        lib.define("Player", "playersNearby(radius)", "List of other players' names within radius (default 32), closest first.", 0, 1,
            a -> nearbyPlayers(a.number(0, 32)));
        lib.define("Player", "eat()", "Eat one food item from your hotbar. Returns false if there is nothing to eat.", 0, 0,
            a -> eat(a.interpreter()));
        lib.define("Player", "eatUntilFull()", "Keep eating until the hunger bar is full or you run out of food. Returns true if full.", 0, 0, a -> {
            for (int i = 0; i < 64; i++) {
                if (onPlayer(p -> p.getFoodData().getFoodLevel()) >= 20) return true;
                if (!eat(a.interpreter())) return false;
            }
            return onPlayer(p -> p.getFoodData().getFoodLevel()) >= 20;
        });
    }

    private static List<Object> nearbyPlayers(double radius) {
        return onPlayer(self -> {
            double r2 = radius * radius;
            List<Player> found = new ArrayList<>();
            for (Player other : mc().level.players()) {
                if (other == self || other.isSpectator()) continue;
                if (TeamsModule.isFriendOrTeam(other) || DihAntiBot.isBot(other)) continue;
                if (other.distanceToSqr(self) <= r2) found.add(other);
            }
            found.sort((x, y) -> Double.compare(x.distanceToSqr(self), y.distanceToSqr(self)));
            List<Object> names = new ArrayList<>();
            for (Player p : found) names.add(p.getName().getString());
            return names;
        });
    }

    private static boolean eat(Interpreter it) {
        int[] slots = onPlayer(p -> {
            int food = p.getFoodData().getFoodLevel();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = p.getInventory().getItem(i);
                FoodProperties props = stack.get(DataComponents.FOOD);
                if (props == null) continue;
                if (food >= 20 && !props.canAlwaysEat()) continue;
                return new int[] {i, p.getInventory().getSelectedSlot()};
            }
            return null;
        });
        if (slots == null) return false;
        int foodSlot = slots[0];
        int previousSlot = slots[1];
        try {
            onClient(() -> {
                DihInventoryHelper.selectHotbarSlot(mc(), foodSlot);
                mc().options.keyUse.setDown(true);
                if (mc().gameMode != null) mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
                return null;
            });
            boolean started = false;
            for (int t = 0; t < 60; t++) {
                it.waitTicks(1);
                boolean using = onPlayer(LocalPlayer::isUsingItem);
                if (using) started = true;
                else if (started || t >= 10) break;
            }
            return started;
        } finally {
            releaseUse();
            onClient(() -> {
                DihInventoryHelper.selectHotbarSlot(mc(), previousSlot);
                return null;
            });
        }
    }

    /** Lets go of the use key if a script was holding it. Safe from any thread. */
    static void releaseUse() {
        Minecraft mc = mc();
        if (mc.isSameThread()) mc.options.keyUse.setDown(false);
        else mc.execute(() -> mc.options.keyUse.setDown(false));
    }

    // ---------------------------------------------------------------- chat / commands

    private static void defineChat(Library lib) {
        lib.define("Chat", "command(text)", "Run a command as if typed in chat: \"/home\" (server), \"#mine gold_ore\" (Baritone) or \".toggle flee\" (DIH).", 1, 1, a -> {
            runCommand(a.string(0).trim(), a);
            return null;
        });
        lib.define("Chat", "say(text)", "Send a plain chat message.", 1, 1, a -> {
            String text = a.string(0);
            onClient(() -> {
                requireConnection(a);
                DihCommands.sendPlainChat(mc().getConnection(), text);
                return null;
            });
            return null;
        });
    }

    private static void runCommand(String raw, Library.Args a) {
        if (raw.isEmpty()) return;
        onClient(() -> {
            if (raw.startsWith("#")) {
                baritoneExecute(raw.substring(1), a);
            } else if (DihCommands.isDihCommandMessage(raw)) {
                DihCommands.dispatch(DihCommands.commandBody(raw));
            } else {
                requireConnection(a);
                mc().getConnection().sendCommand(raw.startsWith("/") ? raw.substring(1) : raw);
            }
            return null;
        });
    }

    private static void requireConnection(Library.Args a) {
        if (mc().getConnection() == null) throw a.error("not connected to a server");
    }

    // ---------------------------------------------------------------- baritone

    private static void defineBaritone(Library lib) {
        lib.define("Baritone", "baritone(command)", "Run a Baritone command without the #, e.g. baritone(\"mine gold_ore\").", 1, 1, a -> {
            String cmd = a.string(0).trim();
            onClient(() -> baritoneExecute(cmd.startsWith("#") ? cmd.substring(1) : cmd, a));
            return null;
        });
        lib.define("Baritone", "stopBaritone()", "Cancel whatever Baritone is doing.", 0, 0, a -> {
            onClient(() -> {
                baritone.api.IBaritone b = primaryBaritone();
                if (b != null) b.getPathingBehavior().cancelEverything();
                return null;
            });
            return null;
        });
        lib.define("Baritone", "baritoneBusy()", "True while Baritone is mining, pathing, farming...", 0, 0, a -> onClient(GameLibrary::baritoneBusy));
        lib.define("Baritone", "waitForBaritone(timeoutTicks)", "Wait until Baritone is idle. Returns false if it timed out (default: no timeout).", 0, 1,
            a -> waitForBaritone(a.interpreter(), a.integer(0, -1)));
        lib.define("Baritone", "goto(target, ...)", "Walk with Baritone and wait until it arrives: goto(\"chest\") or goto(x, y, z).", 1, 3, a -> {
            String target;
            if (a.size() == 3) target = (int) Math.floor(a.number(0)) + " " + (int) Math.floor(a.number(1)) + " " + (int) Math.floor(a.number(2));
            else if (a.size() == 1) target = a.get(0) instanceof Double ? a.integer(0) + "" : a.string(0);
            else throw a.error("use goto(\"block\") or goto(x, y, z)");
            onClient(() -> baritoneExecute("goto " + target, a));
            return waitForBaritone(a.interpreter(), -1);
        });
    }

    private static boolean waitForBaritone(Interpreter it, int timeoutTicks) {
        // give Baritone a moment to pick the job up before deciding it's idle
        it.waitTicks(5);
        int waited = 5;
        while (onClient(GameLibrary::baritoneBusy)) {
            if (timeoutTicks >= 0 && waited >= timeoutTicks) return false;
            it.waitTicks(2);
            waited += 2;
        }
        return true;
    }

    private static Void baritoneExecute(String command, Library.Args a) {
        baritone.api.IBaritone b = primaryBaritone();
        if (b == null) throw a.error("Baritone is not available");
        b.getCommandManager().execute(command);
        return null;
    }

    private static baritone.api.IBaritone primaryBaritone() {
        try {
            return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean baritoneBusy() {
        baritone.api.IBaritone b = primaryBaritone();
        if (b == null) return false;
        try {
            return b.getPathingBehavior().isPathing() || b.getPathingControlManager().mostRecentInControl().isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- inventory

    private static void defineInventory(Library lib) {
        lib.define("Inventory", "count(item)", "How many of an item you carry, e.g. count(\"raw_gold\").", 1, 1, a -> {
            String item = a.string(0);
            return onPlayer(p -> {
                int n = 0;
                for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (matches(s, item)) n += s.getCount();
                if (matches(p.getOffhandItem(), item)) n += p.getOffhandItem().getCount();
                return (double) n;
            });
        });
        lib.define("Inventory", "hasItem(item)", "True if you carry at least one of the item.", 1, 1, a -> {
            String item = a.string(0);
            return onPlayer(p -> {
                for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (matches(s, item)) return true;
                return matches(p.getOffhandItem(), item);
            });
        });
        lib.define("Inventory", "freeSlots()", "Number of empty main-inventory slots (0-36).", 0, 0, a -> onPlayer(p -> {
            int n = 0;
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.isEmpty()) n++;
            return (double) n;
        }));
        lib.define("Inventory", "inventoryFull()", "True when there is no empty slot left.", 0, 0, a -> onPlayer(p -> {
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.isEmpty()) return false;
            return true;
        }));
        lib.define("Inventory", "heldItem()", "Id of the item in your main hand, e.g. \"diamond_pickaxe\" (\"air\" if empty).", 0, 0,
            a -> onPlayer(p -> BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).getPath()));
        lib.define("Inventory", "selectSlot(slot)", "Select hotbar slot 1-9.", 1, 1, a -> {
            int slot = a.integer(0);
            if (slot < 1 || slot > 9) throw a.error("slot must be 1-9");
            onClient(() -> {
                DihInventoryHelper.selectHotbarSlot(mc(), slot - 1);
                return null;
            });
            return null;
        });
        lib.define("Inventory", "selectItem(item)", "Select the hotbar slot holding item. Returns false if it isn't in the hotbar.", 1, 1, a -> {
            String item = a.string(0);
            return onPlayer(p -> {
                for (int i = 0; i < 9; i++) {
                    if (matches(p.getInventory().getItem(i), item)) {
                        DihInventoryHelper.selectHotbarSlot(mc(), i);
                        return true;
                    }
                }
                return false;
            });
        });
    }

    // ---------------------------------------------------------------- GUIs / containers

    private static void defineGui(Library lib) {
        lib.define("GUI", "guiOpen()", "True while a chest or other container GUI is open.", 0, 0, a -> onClient(GameLibrary::containerOpen));
        lib.define("GUI", "guiTitle()", "Title of the open screen, or \"\" if none.", 0, 0,
            a -> onClient(() -> mc().gui.screen() == null ? "" : mc().gui.screen().getTitle().getString()));
        lib.define("GUI", "waitForGui(timeoutTicks, title)", "Wait for a container GUI to open (optionally with a title containing text). Returns false on timeout (default 100 ticks).", 0, 2, a -> {
            int timeout = a.integer(0, 100);
            String title = a.string(1, "").toLowerCase(Locale.ROOT);
            return waitFor(a.interpreter(), timeout, () -> containerOpen()
                && (title.isEmpty() || mc().gui.screen().getTitle().getString().toLowerCase(Locale.ROOT).contains(title)));
        });
        lib.define("GUI", "waitForItem(name, timeoutTicks)", "Wait until an item with this name shows up in the open GUI. Returns false on timeout (default 100 ticks).", 1, 2, a -> {
            String name = a.string(0);
            return waitFor(a.interpreter(), a.integer(1, 100), () -> findGuiSlot(name, false) >= 0);
        });
        lib.define("GUI", "clickItem(name, button)", "Click the item in the open GUI whose name (or id) matches, e.g. clickItem(\"Badlands\"). button: 0 left (default), 1 right. Returns false if not found.", 1, 2, a -> {
            String name = a.string(0);
            int button = a.integer(1, 0);
            boolean clicked = onClient(() -> {
                int slot = findGuiSlot(name, false);
                return slot >= 0 && DihInventoryClickHelper.click(mc(), slot, button, ContainerInput.PICKUP);
            });
            if (clicked) a.interpreter().waitTicks(2);
            return clicked;
        });
        lib.define("GUI", "clickSlot(slot, button, shift)", "Click a GUI slot by number: 0 is the top-left slot of the menu (0-26 in a 27-slot menu), your inventory comes after guiSize(). button 0 left, 1 right; shift=true shift-clicks.", 1, 3, a -> {
            int slot = a.integer(0);
            int button = a.integer(1, 0);
            boolean shift = a.has(2) && dihclient.script.lang.Values.truthy(a.get(2));
            boolean clicked = onClient(() -> {
                AbstractContainerMenu menu = menu();
                if (menu == null || slot < 0 || slot >= menu.slots.size()) return false;
                return DihInventoryClickHelper.click(mc(), slot, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            });
            if (clicked) a.interpreter().waitTicks(1);
            return clicked;
        });
        lib.define("GUI", "guiSize()", "Number of the open GUI's own slots (27 for a chest or most server menus, 54 for a double chest; 0 if none). Your inventory comes after these.", 0, 0,
            a -> onClient(() -> (double) containerSlotCount()));
        lib.define("GUI", "slotItem(slot)", "Item id in a GUI slot, e.g. \"raw_gold\", or null if empty.", 1, 1, a -> {
            int slot = a.integer(0);
            return onClient(() -> {
                ItemStack stack = stackAt(slot);
                return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            });
        });
        lib.define("GUI", "slotName(slot)", "Name shown on the item in a GUI slot, or null if empty.", 1, 1, a -> {
            int slot = a.integer(0);
            return onClient(() -> {
                ItemStack stack = stackAt(slot);
                return stack.isEmpty() ? null : stack.getHoverName().getString();
            });
        });
        lib.define("GUI", "slotCount(slot)", "How many items are in a GUI slot (0 if empty).", 1, 1, a -> {
            int slot = a.integer(0);
            return onClient(() -> (double) stackAt(slot).getCount());
        });
        lib.define("GUI", "findSlot(item)", "First of the GUI's own slots holding item (by id or name), or -1.", 1, 1, a -> {
            String item = a.string(0);
            return onClient(() -> {
                AbstractContainerMenu menu = menu();
                if (menu == null) return -1.0;
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot s = menu.slots.get(i);
                    if (containerOpen() && s.container instanceof Inventory) continue;
                    if (matches(s.getItem(), item)) return (double) i;
                }
                return -1.0;
            });
        });
        lib.define("GUI", "inventorySlot(n)", "GUI slot number of your own inventory slot n (1-9 hotbar, 10-36 the rows above), for clickSlot(). -1 if not shown.", 1, 1, a -> {
            int n = a.integer(0);
            if (n < 1 || n > 36) throw a.error("inventory slots are 1-36 (1-9 = hotbar)");
            return onClient(() -> (double) inventoryMenuSlot(n - 1));
        });
        lib.define("GUI", "clickInventory(n, button, shift)", "Click your own inventory slot n (1-9 hotbar, 10-36 the rows above). Works with a chest open too.", 1, 3, a -> {
            int n = a.integer(0);
            if (n < 1 || n > 36) throw a.error("inventory slots are 1-36 (1-9 = hotbar)");
            int button = a.integer(1, 0);
            boolean shift = a.has(2) && dihclient.script.lang.Values.truthy(a.get(2));
            boolean clicked = onClient(() -> {
                int slot = inventoryMenuSlot(n - 1);
                return slot >= 0 && DihInventoryClickHelper.click(mc(), slot, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            });
            if (clicked) a.interpreter().waitTicks(1);
            return clicked;
        });
        lib.define("GUI", "fillSlots(item, count)", "Take item from the open container and put exactly 1 in each of count empty inventory slots. Returns how many slots were filled.", 2, 2,
            a -> fillSlots(a, a.string(0), a.integer(1)));
        lib.define("GUI", "deposit(item)", "Shift-click every stack of item from your inventory into the open container. \"*\" moves everything. Returns the number of items moved.", 1, 1,
            a -> transfer(a, a.string(0), true, Integer.MAX_VALUE));
        lib.define("GUI", "withdraw(item, amount)", "Shift-click stacks of item from the open container into your inventory until you have taken at least amount (default: all). Returns the number moved.", 1, 2,
            a -> transfer(a, a.string(0), false, a.integer(1, Integer.MAX_VALUE)));
        lib.define("GUI", "closeGui()", "Close the open screen.", 0, 0, a -> {
            onClient(() -> {
                if (mc().gui.screen() != null) DihGuiActions.closeCurrentScreen(mc(), true, false);
                return null;
            });
            a.interpreter().waitTicks(2);
            return null;
        });
    }

    private static double transfer(Library.Args a, String item, boolean toContainer, int limit) {
        if (!onClient(GameLibrary::containerOpen)) throw a.error("no container is open — use openNearest(\"chest\") or waitForGui() first");
        int moved = 0;
        // re-scan after every click: shift-clicking shuffles slots
        while (moved < limit) {
            int count = onClient(() -> {
                AbstractContainerMenu menu = menu();
                if (menu == null) return -1;
                for (int i = 0; i < menu.slots.size(); i++) {
                    Slot slot = menu.slots.get(i);
                    boolean playerSide = slot.container instanceof Inventory;
                    if (playerSide != toContainer) continue;
                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty() || !matches(stack, item)) continue;
                    int n = stack.getCount();
                    if (!DihInventoryClickHelper.click(mc(), i, 0, ContainerInput.QUICK_MOVE)) return -1;
                    // nothing moved (destination full): stop instead of spinning
                    return slot.getItem().getCount() == n && ItemStack.isSameItem(slot.getItem(), stack) ? 0 : n;
                }
                return -1;
            });
            if (count <= 0) break;
            moved += count;
            a.interpreter().waitTicks(1);
        }
        return moved;
    }

    /**
     * One item per empty inventory slot: pick up a stack from the container, right-click it into empty
     * slots one at a time, refill from the container when the cursor runs out, then put the rest back.
     */
    private static double fillSlots(Library.Args a, String item, int count) {
        if (count <= 0) return 0;
        if (!onClient(GameLibrary::containerOpen)) throw a.error("no container is open — use openNearest(\"chest\") or waitForGui() first");
        int filled = 0;
        int source = -1;
        try {
            while (filled < count) {
                // 0 = placed one, 1 = picked up a stack, -1 = nothing left to do
                int[] step = onClient(() -> {
                    AbstractContainerMenu menu = menu();
                    if (menu == null || !containerOpen()) return new int[] {-1, -1};
                    ItemStack carried = menu.getCarried();
                    if (carried.isEmpty() || !matches(carried, item)) {
                        if (!carried.isEmpty()) return new int[] {-1, -1};
                        for (int i = 0; i < menu.slots.size(); i++) {
                            Slot s = menu.slots.get(i);
                            if (s.container instanceof Inventory || !matches(s.getItem(), item)) continue;
                            if (!DihInventoryClickHelper.click(mc(), i, 0, ContainerInput.PICKUP)) return new int[] {-1, -1};
                            return new int[] {1, i};
                        }
                        return new int[] {-1, -1};
                    }
                    int empty = emptyInventoryMenuSlot(menu);
                    if (empty < 0) return new int[] {-1, -1};
                    if (!DihInventoryClickHelper.click(mc(), empty, 1, ContainerInput.PICKUP)) return new int[] {-1, -1};
                    return new int[] {0, -1};
                });
                if (step[0] < 0) break;
                if (step[0] == 1) source = step[1];
                else filled++;
                a.interpreter().waitTicks(1);
            }
        } finally {
            int back = source;
            onClient(() -> {
                putCarriedBack(back);
                return null;
            });
        }
        return filled;
    }

    /** Drops whatever is on the cursor into the slot it came from, or any container slot that takes it. */
    private static void putCarriedBack(int preferred) {
        AbstractContainerMenu menu = menu();
        if (menu == null || menu.getCarried().isEmpty()) return;
        List<Integer> order = new ArrayList<>();
        if (preferred >= 0) order.add(preferred);
        for (int i = 0; i < menu.slots.size(); i++) if (i != preferred) order.add(i);
        for (int i : order) {
            if (menu.getCarried().isEmpty()) return;
            if (i >= menu.slots.size()) continue;
            Slot s = menu.slots.get(i);
            ItemStack here = s.getItem();
            if (here.isEmpty() || (ItemStack.isSameItemSameComponents(here, menu.getCarried()) && here.getCount() < here.getMaxStackSize())) {
                DihInventoryClickHelper.click(mc(), i, 0, ContainerInput.PICKUP);
            }
        }
    }

    /** Menu index of an empty main-inventory/hotbar slot, main inventory first. */
    private static int emptyInventoryMenuSlot(AbstractContainerMenu menu) {
        int hotbar = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!(s.container instanceof Inventory) || !s.getItem().isEmpty()) continue;
            int idx = s.getContainerSlot();
            if (idx >= 9 && idx < 36) return i;
            if (idx < 9 && hotbar < 0) hotbar = i;
        }
        return hotbar;
    }

    /** Menu index of player inventory slot {@code index} (0-8 hotbar, 9-35 main), or -1. */
    private static int inventoryMenuSlot(int index) {
        AbstractContainerMenu menu = menu();
        if (menu == null) return -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container instanceof Inventory && s.getContainerSlot() == index) return i;
        }
        return -1;
    }

    private static int containerSlotCount() {
        if (!containerOpen()) return 0;
        int n = 0;
        for (Slot s : menu().slots) if (!(s.container instanceof Inventory)) n++;
        return n;
    }

    private static ItemStack stackAt(int slot) {
        AbstractContainerMenu menu = menu();
        if (menu == null || slot < 0 || slot >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.slots.get(slot).getItem();
    }

    private static boolean containerOpen() {
        Minecraft mc = mc();
        return mc.player != null && mc.gui.screen() != null && mc.player.containerMenu != null
            && mc.player.containerMenu != mc.player.inventoryMenu;
    }

    private static AbstractContainerMenu menu() {
        Minecraft mc = mc();
        return mc.player == null ? null : mc.player.containerMenu;
    }

    /** Handler slot of the item named {@code name}: exact name first, then id, then partial name. */
    private static int findGuiSlot(String name, boolean playerSide) {
        AbstractContainerMenu menu = menu();
        if (menu == null) return -1;
        String target = ItemTarget.normalize(name);
        if (target.isEmpty()) return -1;
        int partial = -1;
        int byId = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            if (!playerSide && menu.slots.get(i).container instanceof Inventory && menu != mc().player.inventoryMenu) continue;
            String shown = ItemTarget.normalize(stack.getHoverName().getString());
            if (shown.equals(target)) return i;
            if (byId < 0 && idMatches(stack, name)) byId = i;
            if (partial < 0 && shown.contains(target)) partial = i;
        }
        return byId >= 0 ? byId : partial;
    }

    // ---------------------------------------------------------------- world

    private static void defineWorld(Library lib) {
        lib.define("World", "findBlock(block, radius)", "Closest block of this type within radius (default 16, max 32) as [x, y, z], or null.", 1, 2, a -> {
            String block = a.string(0);
            int radius = Math.max(1, Math.min(MAX_SCAN_RADIUS, a.integer(1, 16)));
            BlockPos pos = onPlayer(p -> nearestBlock(p, block, radius));
            if (pos == null) return null;
            List<Object> out = new ArrayList<>();
            out.add((double) pos.getX());
            out.add((double) pos.getY());
            out.add((double) pos.getZ());
            return out;
        });
        lib.define("World", "lookAt(x, y, z)", "Turn to face a position.", 3, 3, a -> {
            Vec3 target = new Vec3(a.number(0), a.number(1), a.number(2));
            onPlayer(p -> {
                face(p, target);
                return null;
            });
            return null;
        });
        lib.define("World", "openNearest(block, radius)", "Look at and right-click the closest block of this type within reach (radius default 5), then wait for its GUI. Returns true if a GUI opened.", 1, 2, a -> {
            String block = a.string(0);
            int radius = Math.max(1, Math.min(6, a.integer(1, 5)));
            Boolean used = onPlayer(p -> {
                BlockPos pos = nearestBlock(p, block, radius);
                if (pos == null || mc().gameMode == null) return false;
                Vec3 center = Vec3.atCenterOf(pos);
                if (p.getEyePosition().distanceTo(center) > REACH + 0.5) return false;
                face(p, center);
                mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(center, Direction.UP, pos, false));
                p.swing(InteractionHand.MAIN_HAND);
                return true;
            });
            if (!used) return false;
            return waitFor(a.interpreter(), 40, GameLibrary::containerOpen);
        });
        lib.define("World", "macro(name)", "Run one of your saved DIH macros and wait for it to finish. Returns false if it doesn't exist.", 1, 1, a -> {
            String name = a.string(0);
            Long id = onClient(() -> {
                DihMacro macro = DihMacroManager.get().get(name);
                return macro == null ? null : MacroExecutor.executeTracked(macro);
            });
            if (id == null || id <= 0) return false;
            try {
                a.interpreter().waitTicks(1);
                while (MacroExecutor.isRunActive(id)) a.interpreter().waitTicks(2);
            } finally {
                if (MacroExecutor.isRunActive(id)) MacroExecutor.stopRun(id);
            }
            return true;
        });
    }

    private static BlockPos nearestBlock(LocalPlayer p, String block, int radius) {
        String want = stripNamespace(block);
        BlockPos origin = p.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= bestD) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(cursor).getBlock()).getPath();
                    if (id.equals(want)) {
                        bestD = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static void face(LocalPlayer p, Vec3 target) {
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setXRot((float) -Math.toDegrees(Math.atan2(dy, flat)));
    }

    // ---------------------------------------------------------------- helpers

    /** {@code "raw_gold"}, {@code "minecraft:raw_gold"}, a display name like {@code "Raw Gold"}, or {@code "*"} for anything. */
    static boolean matches(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty()) return false;
        if ("*".equals(query.trim())) return true;
        if (idMatches(stack, query)) return true;
        String target = ItemTarget.normalize(query);
        return !target.isEmpty() && target.equals(ItemTarget.normalize(stack.getHoverName().getString()));
    }

    private static boolean idMatches(ItemStack stack, String query) {
        String want = stripNamespace(query);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(want);
    }

    private static String stripNamespace(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    private static boolean waitFor(Interpreter it, int timeoutTicks, Supplier<Boolean> condition) {
        for (int waited = 0; ; waited++) {
            if (onClient(condition)) return true;
            if (waited >= timeoutTicks) return false;
            it.waitTicks(1);
        }
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static <T> T onPlayer(java.util.function.Function<LocalPlayer, T> fn) {
        return onClient(() -> {
            LocalPlayer p = mc().player;
            if (p == null || mc().level == null) throw new ScriptException("Not in a world", 0);
            return fn.apply(p);
        });
    }

    /** Runs {@code task} on the client thread and returns its result (waits up to 5 s). */
    static <T> T onClient(Supplier<T> task) {
        Minecraft mc = mc();
        if (mc.isSameThread()) return task.get();
        try {
            return mc.submit(task).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Interpreter.StopSignal();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new ScriptException("Game error: " + cause, 0);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new ScriptException("The game did not respond (client thread busy)", 0);
        }
    }
}
