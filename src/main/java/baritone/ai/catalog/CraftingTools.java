package baritone.ai.catalog;

import baritone.acquire.exec.InventoryReader;
import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.VanillaKnowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlannerOptions;
import baritone.ai.BuiltinTools;
import baritone.ai.ItemRequest;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolInput;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Optional;

/** Crafting and smelting from what is carried. Getting the materials too is acquire's job. */
final class CraftingTools {

    /** Crafting only: no kill steps, no gear detours; stations may be placed from the inventory. */
    private static final PlannerOptions CRAFT_ONLY = new PlannerOptions(true, false, 24, 200, false);

    private CraftingTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("craft", ToolCategory.CRAFTING)
                .summary("Craft or smelt an item from what is carried, never gathering.")
                .description("Craft or smelt count of an item using only what is carried, placing a crafting table or "
                        + "furnace from the inventory if none is near. If anything would have to be mined or hunted first, "
                        + "it changes nothing and says what is needed (acquire gets that too).")
                .schema(ToolSchema.builder()
                        .string("item", "The item, as an id or plain words.").required()
                        .integer("count", "How many to have.").range(1, ItemRequest.MAX_COUNT).defaultsTo(1)
                        .build())
                .handler(CraftingTools::craft)
                .build());
    }

    private static ToolResult craft(ToolContext ctx, ToolInput args) {
        Knowledge knowledge = VanillaKnowledge.get();
        Optional<String> id = knowledge.resolveItem(args.string("item"));
        if (id.isEmpty()) return ToolResult.failed("No item called \"" + args.string("item") + "\".");
        int count = args.integer("count");
        Plan plan = ctx.onGameThread(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return null;
            return new AcquirePlanner(knowledge, WorldView.UNKNOWN, CRAFT_ONLY).plan(id.get(), count, InventoryReader.snapshot(player));
        }, null);
        if (plan == null) return ToolResult.failed("Not in a world.");
        ToolResult blocked = blocked(plan);
        if (blocked != null) return blocked;
        ToolResult started = BuiltinTools.startAcquire(ctx, id.get(), count);
        return started.fact("craft_only", true);
    }

    /**
     * Why a plan can't be done by crafting alone (the first thing it would have to gather, as {@code needs}), or null
     * when every step is crafting, smelting or handling a station.
     */
    static ToolResult blocked(Plan plan) {
        String goal = KnowledgeTools.plain(plan.goal());
        if (plan.alreadyDone()) return ToolResult.ok("Already have " + plan.count() + " " + goal + ".").fact("item", goal);
        if (!plan.complete()) return ToolResult.failed("Can't make " + goal + ": " + String.join("; ", plan.missing()) + ".");
        for (Step step : plan.steps()) {
            if (step instanceof Step.Mine mine) {
                return ToolResult.needs(KnowledgeTools.plain(mine.item()), mine.untilCount())
                        .fact("gather", "mine").fact("goal", goal);
            }
            if (step instanceof Step.Kill kill) {
                return ToolResult.needs(KnowledgeTools.plain(kill.item()), kill.untilCount())
                        .fact("gather", "kill").fact("goal", goal);
            }
            if (step instanceof Step.Barter barter) {
                return ToolResult.needs(KnowledgeTools.plain(barter.item()), barter.untilCount())
                        .fact("gather", "barter").fact("goal", goal);
            }
        }
        return null;
    }
}
