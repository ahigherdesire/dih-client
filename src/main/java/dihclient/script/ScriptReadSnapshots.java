package dihclient.script;

import dihclient.script.lang.Library;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Captures public read-only APIs on the client thread, then serves detached values to workers. */
final class ScriptReadSnapshots {
    private static final ScriptReadCache CACHE = new ScriptReadCache();
    private static final Set<String> SCALARS = Set.of(
        "inWorld", "health", "maxHealth", "food", "x", "y", "z", "dimension", "playerName",
        "alive", "usingItem", "onGround", "inWater", "submerged", "inLava", "onFire", "air",
        "fallDistance", "velocity", "yaw", "pitch", "biome", "attackCooldown", "crosshairTarget",
        "freeSlots", "heldItem", "heldDurability", "cursorItem", "cursorCount", "baritoneBusy",
        "guiOpen", "guiTitle", "guiSize", "inventorySnapshot");
    private static final Set<String> READS;
    private static final Set<String> ACTIONS = Set.of(
        "command", "baritone", "stopBaritone", "goto", "gotoWaypoint", "eat", "eatUntilFull",
        "equipPickaxe", "selectSlot", "selectItem", "clickItem", "clickSlot", "clickInventory",
        "fillSlots", "deposit", "withdraw", "closeGui", "lookAt", "openNearest", "macro",
        "swapInventory", "moveInventory", "dropInventory", "clickSlotAction", "look", "attack",
        "useItem", "interactBlock", "setKey", "setModuleEnabled", "respawn", "disconnect",
        "waitForGui", "waitForItem", "waitForBaritone");
    private static volatile Library readers;
    private static volatile boolean disconnected;
    static {
        Set<String> names = new HashSet<>(SCALARS);
        names.addAll(List.of("inventoryItem", "inventoryCount", "durability", "maxDurability",
            "slotItem", "slotName", "slotCount", "slotLore", "inventorySlot", "count", "hasItem",
            "inventoryFull", "roomFor", "pickaxeCount", "playersNearby", "playerNearby", "nearestPlayer",
            "nearestThreat", "groundDistance", "moduleEnabled", "hasEnchantment"));
        READS = Set.copyOf(names);
    }
    private ScriptReadSnapshots() { }

    static Library decorate(Library library) {
        library.define("Inventory", "inventorySnapshot()",
            "Detached tick snapshot: 36 main slots followed by offhand, with slot, id, count and durability.",
            0, 0, a -> GameLibrary.onClient(ScriptReadSnapshots::inventory));
        // Keep the original readers: capture must never read its own previous cached frame.
        readers = new Library().include(library);
        CACHE.wrap(library, READS);
        CACHE.wrapActions(library, ACTIONS, () -> GameLibrary.onClient(() -> { capture(); return null; }));
        return library;
    }

    static void joined() {
        disconnected = false;
        GameLibrary.onClient(() -> { capture(); return null; });
    }

    static void disconnected() {
        disconnected = true;
        CACHE.disconnected(READS);
    }

    /** Invoke before waking script workers and after actions/connection transitions, on the client thread. */
    static void capture() {
        Library source = readers;
        if (source == null) return;
        Object version = CACHE.version();
        List<ScriptReadCache.Call> calls = new ArrayList<>();
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("Script snapshots require the client thread");
        var player = minecraft.player;
        if (disconnected && player != null) return; // Wait for the departing player to be torn down.
        if (player == null || minecraft.level == null) {
            // Supervisors run while disconnected too. Do not construct hundreds of identical
            // Not-in-a-world exceptions every tick just to populate unavailable inventory slots.
            for (String name : List.of("inWorld", "alive", "guiOpen", "guiTitle", "guiSize", "baritoneBusy"))
                calls.add(call(name));
            CACHE.capture(source, calls, version);
            return;
        }
        for (String name : SCALARS) calls.add(call(name));
        for (int slot = 1; slot <= 37; slot++) {
            Object number = slot == 37 ? "offhand" : (double) slot;
            for (String name : List.of("inventoryItem", "inventoryCount", "durability", "maxDurability"))
                calls.add(call(name, number));
            if (slot <= 36) calls.add(call("inventorySlot", number));
        }
        for (int slot = 0; slot < player.containerMenu.slots.size(); slot++) {
            for (String name : List.of("slotItem", "slotName", "slotCount", "slotLore"))
                calls.add(call(name, (double) slot));
        }
        calls.addAll(CACHE.requested());
        CACHE.capture(source, calls, version);
    }

    private static ScriptReadCache.Call call(String name, Object... arguments) {
        return new ScriptReadCache.Call(name, Arrays.asList(arguments));
    }

    private static List<Object> inventory() {
        var player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null)
            throw new dihclient.script.lang.ScriptException("Not in a world", 0);
        List<Object> result = new ArrayList<>(37);
        for (int slot = 0; slot < 37; slot++) {
            var stack = slot == 36 ? player.getOffhandItem() : player.getInventory().getItem(slot);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slot", slot == 36 ? "offhand" : (double) (slot + 1));
            item.put("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            item.put("count", (double) stack.getCount());
            item.put("durability", stack.isDamageableItem() ? (double) (stack.getMaxDamage() - stack.getDamageValue()) : -1d);
            item.put("maxDurability", stack.isDamageableItem() ? (double) stack.getMaxDamage() : -1d);
            result.add(item);
        }
        return result;
    }
}
