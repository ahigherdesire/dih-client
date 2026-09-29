package baritone.ai.catalog;

import baritone.acquire.knowledge.VanillaKnowledge;
import baritone.ai.tool.ToolRegistry;

/**
 * The hand-written tools that live in Baritone, by category. The client adds its own (modules, macros, servers) and
 * the command adapters through {@link ToolRegistry#contribute}.
 */
public final class CatalogTools {

    private CatalogTools() {
    }

    /** The structures goto_structure knows, as ids ("village", "nether_fortress"). */
    public static java.util.List<String> structures() {
        return java.util.List.of(JobTools.STRUCTURES);
    }

    public static void register(ToolRegistry registry) {
        JobTools.register(registry);
        MovementTools.register(registry);
        MiningTools.register(registry);
        BuildingTools.register(registry);
        CraftingTools.register(registry);
        InventoryTools.register(registry);
        CombatTools.register(registry);
        FarmingTools.register(registry);
        WorldTools.register(registry);
        KnowledgeTools.register(registry, VanillaKnowledge::get);
        WebTools.register(registry);
        ChatTools.register(registry);
    }
}
