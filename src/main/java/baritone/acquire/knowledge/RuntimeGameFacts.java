package baritone.acquire.knowledge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link GameFacts} from the built-in registries. Everything is copied once in the constructor, so
 * nothing touches the game afterwards. Only the static registries are read, which exist as soon as
 * the game has bootstrapped: no level or server connection is needed.
 */
final class RuntimeGameFacts implements GameFacts {
    private static final Logger LOGGER = LoggerFactory.getLogger("Baritone/Acquire");

    private final Set<String> items = new HashSet<>();
    private final Map<String, String> descriptionIds = new HashMap<>();
    private final Map<String, Boolean> requiresTool = new HashMap<>();
    private final Map<String, List<String>> blocksByTable = new HashMap<>();

    private RuntimeGameFacts() {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || id.getPath().equals("air")) continue;
            items.add(id.toString());
            descriptionIds.put(id.toString(), item.getDescriptionId());
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            requiresTool.put(id.toString(), block.defaultBlockState().requiresCorrectToolForDrops());
            block.getLootTable().ifPresent(key ->
                    blocksByTable.computeIfAbsent(key.identifier().toString(), k -> new ArrayList<>()).add(id.toString()));
        }
    }

    /** The registry-backed facts, or {@link GameFacts#NONE} before bootstrap or if anything goes wrong. */
    static GameFacts tryCreate() {
        try {
            if (!bootstrapped()) return GameFacts.NONE;
            RuntimeGameFacts facts = new RuntimeGameFacts();
            return facts.items.isEmpty() ? GameFacts.NONE : facts;
        } catch (Throwable t) {
            LOGGER.warn("Acquire knowledge: game registries unavailable, using data-only fallbacks", t);
            return GameFacts.NONE;
        }
    }

    private static boolean bootstrapped() {
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true; // field renamed: in the game we are always bootstrapped by the time anyone asks
        }
    }

    @Override
    public Set<String> itemIds() {
        return items;
    }

    @Override
    public Boolean requiresCorrectTool(String block) {
        return requiresTool.get(block);
    }

    @Override
    public List<String> blocksWithLootTable(String table) {
        return blocksByTable.getOrDefault(table, List.of());
    }

    @Override
    public String descriptionId(String item) {
        return descriptionIds.get(item);
    }
}
