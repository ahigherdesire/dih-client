package dihclient.script;

import dihclient.script.lang.Interpreter;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import dihclient.util.DihInventoryClickHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Server-verified inventory operations for scripts. Never mutates client stacks directly. */
public final class ScriptInventory {
    private static final ScriptInventoryOperations.Ownership OWNERSHIP = new ScriptInventoryOperations.Ownership();
    // Accessed exclusively on the Minecraft thread, with weak menu keys after screens close.
    private static final Map<AbstractContainerMenu, Long> SYNCS = new WeakHashMap<>();
    private ScriptInventory() { }

    public static Library create() {
        Library lib = new Library();
        guardLegacyOperations(lib);
        lib.define("Inventory", "inventoryItem(slot)", "Namespaced item ID in inventory slot 1-36 or offhand; minecraft:air if empty.",1,1,
            a -> read(slotIndex(a.get(0)), s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
        lib.define("Inventory", "inventoryCount(slot)", "Count in inventory slot 1-36 or offhand.",1,1,
            a -> read(slotIndex(a.get(0)), s -> (double)s.getCount()));
        lib.define("Inventory", "durability(slot)", "Uses remaining in slot 1-36 or offhand; -1 when not damageable.",1,1,
            a -> read(slotIndex(a.get(0)), s -> s.isDamageableItem() ? (double)(s.getMaxDamage()-s.getDamageValue()) : -1.0));
        lib.define("Inventory", "maxDurability(slot)", "Maximum uses in slot 1-36 or offhand; -1 when not damageable.",1,1,
            a -> read(slotIndex(a.get(0)), s -> s.isDamageableItem() ? (double)s.getMaxDamage() : -1.0));
        lib.define("Inventory", "hasEnchantment(slot, id)", "Whether this slot has the specified enchantment, e.g. minecraft:mending.",2,2, a -> {
            int slot=slotIndex(a.get(0)); String id=a.string(1); String wanted=id.contains(":") ? id : "minecraft:"+id;
            return read(slot,s -> s.getEnchantments().entrySet().stream().anyMatch(e -> e.getIntValue()>0
                && e.getKey().unwrapKey().map(k -> k.identifier().toString().equals(wanted)).orElse(false)));
        });
        lib.define("Inventory", "cursorItem()", "Namespaced ID carried by the mouse cursor; minecraft:air if empty.",0,0,
            a -> GameLibrary.onClient(() -> BuiltInRegistries.ITEM.getKey(menu().getCarried().getItem()).toString()));
        lib.define("Inventory", "cursorCount()", "Number of items carried by the mouse cursor.",0,0,
            a -> GameLibrary.onClient(() -> (double)menu().getCarried().getCount()));
        lib.define("Inventory", "swapInventory(a, b)", "Swap inventory slots including offhand; false on busy cursor, unavailable slot, rejection or timeout.",2,2,
            a -> transaction(a.interpreter(), access -> ScriptInventoryOperations.swap(access, slotIndex(a.get(0)), slotIndex(a.get(1))),false));
        lib.define("Inventory", "moveInventory(from, to, amount)", "Move up to an exact amount into a compatible slot; returns the server-verified amount moved.",3,3,
            a -> (double)transaction(a.interpreter(), access -> ScriptInventoryOperations.move(access,slotIndex(a.get(0)),slotIndex(a.get(1)),amount(a,2)),0));
        lib.define("Inventory", "dropInventory(slot, amount)", "Drop exactly up to amount from that inventory slot, preserving its remainder.",2,2,
            a -> (double)transaction(a.interpreter(), access -> ScriptInventoryOperations.drop(access,slotIndex(a.get(0)),amount(a,1)),0));
        lib.define("GUI", "slotLore(slot)", "List of plain-text lore lines in a raw menu slot.",1,1,
            a -> GameLibrary.onClient(() -> lore(guiStack(a.integer(0)))));
        lib.define("GUI", "clickSlotAction(slot, action, button)", "Raw menu slot action: pickup, quick_move, swap (0-8 or 40), throw (0 one, 1 stack). Server-verified.",2,3,
            a -> clickAction(a,a.integer(0),a.string(1),a.integer(2,0)));
        return lib;
    }

    /** True throughout a click sequence and recovery, including while cooperative callbacks run. */
    public static boolean busy() { return OWNERSHIP.busy(); }

    /** Requests that the active transaction recover its cursor and yield to a safety action.
     * Returns true while the caller must retry after ownership has actually been released. */
    public static boolean yieldToSafety() { return OWNERSHIP.requestAbort(); }

    private static void guardLegacyOperations(Library lib) {
        Library legacy=GameLibrary.create();
        for(String name:List.of("clickSlot","clickItem","clickInventory","equipPickaxe","selectItem",
            "selectSlot","fillSlots","deposit","withdraw","closeGui")) {
            Library.Entry entry=legacy.get(name);
            if(entry==null) continue;
            Object failure=switch(name) {
                case "fillSlots","deposit","withdraw" -> 0.0;
                case "selectSlot","closeGui" -> null;
                default -> false;
            };
            lib.define(entry.category(),entry.signature(),entry.description(),entry.minArgs(),entry.maxArgs(),
                args -> OWNERSHIP.run(() -> entry.fn().call(args),failure));
        }
    }

    static int slotIndex(Object value) {
        if (value instanceof String name && name.equalsIgnoreCase("offhand")) return 40;
        if (value instanceof Double n && Double.isFinite(n) && n == Math.rint(n) && n>=1 && n<=36) return n.intValue()-1;
        throw new ScriptException("Inventory slot must be 1-36 or \"offhand\"",0);
    }
    private static int amount(Library.Args a,int index) {
        double n=a.number(index);
        if (!Double.isFinite(n) || n<0 || n!=Math.rint(n) || n>Integer.MAX_VALUE) throw a.error("amount must be a nonnegative whole number");
        return (int)n;
    }
    private static LocalPlayer player() {
        LocalPlayer p=Minecraft.getInstance().player;
        if(p==null) throw new ScriptException("Not in a world",0);
        return p;
    }
    private static AbstractContainerMenu menu() { return player().containerMenu; }
    private static ItemStack inventoryStack(int index) { return index==40 ? player().getOffhandItem() : player().getInventory().getItem(index); }
    private static <T> T read(int index,Function<ItemStack,T> fn) { return GameLibrary.onClient(() -> fn.apply(inventoryStack(index))); }
    static List<Object> lore(ItemStack stack) {
        List<Object> out=new ArrayList<>();
        var lore=stack.get(DataComponents.LORE);
        if(lore!=null) for(var line:lore.lines()) out.add(line.getString());
        return out;
    }
    private static ItemStack guiStack(int slot) {
        var menu=menu();
        return slot<0 || slot>=menu.slots.size() ? ItemStack.EMPTY : menu.slots.get(slot).getItem();
    }
    private static int handlerSlot(AbstractContainerMenu menu,int index) {
        if(index==40) return menu==player().inventoryMenu ? 45 : -1;
        for(int i=0;i<menu.slots.size();i++) {
            var slot=menu.slots.get(i);
            if(slot.container==player().getInventory() && slot.getContainerSlot()==index) return i;
        }
        return -1;
    }
    private static <T> T transaction(Interpreter it,Function<NativeAccess,T> fn,T failure) {
        return OWNERSHIP.run(() -> {
            NativeAccess access=new NativeAccess(it);
            try {
                if(!access.valid()) return failure;
                return fn.apply(access);
            } finally {
                access.recover();
            }
        },failure);
    }
    /** Called only after vanilla applied a full server container snapshot. */
    public static void onContent(int containerId) {
        var mc=Minecraft.getInstance();
        if(mc.player==null) return;
        var menu=mc.player.containerMenu;
        if(menu.containerId==containerId) SYNCS.put(menu,SYNCS.getOrDefault(menu,0L)+1);
    }
    private static boolean clickAction(Library.Args a,int slot,String action,int button) {
        ContainerInput input=switch(action.toLowerCase(java.util.Locale.ROOT)) {
            case "pickup" -> ContainerInput.PICKUP;
            case "quick_move" -> ContainerInput.QUICK_MOVE;
            case "swap" -> ContainerInput.SWAP;
            case "throw" -> ContainerInput.THROW;
            default -> throw a.error("action must be pickup, quick_move, swap or throw");
        };
        if(input==ContainerInput.SWAP ? !(button>=0 && button<=8 || button==40) : button<0 || button>1)
            throw a.error("invalid button for action");
        return transaction(a.interpreter(),access -> {
            if(slot<0 || slot>=access.menu.slots.size()) return false;
            return access.rawClick(slot,button,input);
        },false);
    }
    private record ItemKey(ItemStack stack) {
        @Override public boolean equals(Object o) { return o instanceof ItemKey k && ItemStack.isSameItemSameComponents(stack,k.stack); }
        @Override public int hashCode() { return System.identityHashCode(stack.getItem()); }
    }
    private static final class NativeAccess implements ScriptInventoryOperations.Access {
        final Interpreter interpreter;
        final AbstractContainerMenu menu;
        int recoverySlot=-1;
        NativeAccess(Interpreter it) { interpreter=it; menu=GameLibrary.onClient(ScriptInventory::menu); }
        boolean valid() { return GameLibrary.onClient(() -> Minecraft.getInstance().player!=null && menu==ScriptInventory.menu() && Minecraft.getInstance().getConnection()!=null); }
        public ScriptInventoryOperations.Stack stack(int slot) {
            return GameLibrary.onClient(() -> {
                int handler=handlerSlot(menu,slot);
                if(handler<0) return new ScriptInventoryOperations.Stack(null,0,0);
                ItemStack stack=menu.slots.get(handler).getItem();
                return new ScriptInventoryOperations.Stack(new ItemKey(stack.copy()),stack.getCount(),menu.slots.get(handler).getMaxStackSize(stack));
            });
        }
        public ScriptInventoryOperations.Stack cursor() {
            return GameLibrary.onClient(() -> {
                var stack=menu.getCarried();
                return new ScriptInventoryOperations.Stack(new ItemKey(stack.copy()),stack.getCount(),stack.getMaxStackSize());
            });
        }
        public boolean click(int slot,int button,boolean throwing) {
            int handler=GameLibrary.onClient(() -> handlerSlot(menu,slot));
            if(handler<0) return false;
            if(recoverySlot<0 && !throwing) recoverySlot=slot;
            return rawClick(handler,button,throwing ? ContainerInput.THROW : ContainerInput.PICKUP);
        }
        boolean rawClick(int slot,int button,ContainerInput input) {
            List<ItemStack> predicted=GameLibrary.onClient(() -> {
                if(OWNERSHIP.aborted() || !valid() || !DihInventoryClickHelper.click(Minecraft.getInstance(),slot,button,input)) return null;
                List<ItemStack> snapshot=new ArrayList<>();
                for(var s:menu.slots) snapshot.add(s.getItem().copy());
                snapshot.add(menu.getCarried().copy());
                return snapshot;
            });
            if(predicted==null || !synchronise()) return false;
            return GameLibrary.onClient(() -> {
                if(!valid() || predicted.size()!=menu.slots.size()+1) return false;
                for(int i=0;i<menu.slots.size();i++) if(!ItemStack.matches(predicted.get(i),menu.slots.get(i).getItem())) return false;
                return ItemStack.matches(predicted.getLast(),menu.getCarried());
            });
        }
        boolean synchronise() {
            Long before=GameLibrary.onClient(() -> {
                if(OWNERSHIP.aborted() || !valid()) return null;
                long revision=SYNCS.getOrDefault(menu,0L);
                // QUICK_MOVE at -1 is a vanilla no-op, even with items on the cursor. A stale
                // state ID requests the full authoritative snapshot without moving/dropping items.
                Minecraft.getInstance().getConnection().send(new ServerboundContainerClickPacket(menu.containerId,-1,(short)-1,(byte)0,
                    ContainerInput.QUICK_MOVE,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));
                return revision;
            });
            if(before==null) return false;
            for(int tick=0;tick<100;tick++) {
                if(OWNERSHIP.aborted() || !valid()) return false;
                if(GameLibrary.onClient(() -> SYNCS.getOrDefault(menu,0L)>before)) return true;
                interpreter.waitTicks(1);
            }
            return false;
        }
        void recover() {
            if(recoverySlot<0) return;
            GameLibrary.onClient(() -> {
                if(!valid() || menu.getCarried().isEmpty()) return null;
                int handler=handlerSlot(menu,recoverySlot);
                if(handler<0) return null;
                var target=menu.slots.get(handler).getItem(); var carried=menu.getCarried();
                if(target.isEmpty() || ItemStack.isSameItemSameComponents(target,carried) && target.getCount()+carried.getCount()<=target.getMaxStackSize())
                    DihInventoryClickHelper.click(Minecraft.getInstance(),handler,0,ContainerInput.PICKUP);
                return null;
            });
        }
    }
}
