package baritone.ai.catalog;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.model.BarterSource;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolInput;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Answers from the running game's own recipe, loot and tool data, so they are exact for this version. Read-only.
 */
final class KnowledgeTools {

    /** Most lines a lookup lists before summing up the rest. */
    static final int MAX_LINES = 12;
    private static final String[] TIER_NAMES = {"any", "wooden", "stone", "iron", "diamond", "netherite"};

    private KnowledgeTools() {
    }

    static void register(ToolRegistry registry, Supplier<Knowledge> knowledge) {
        registry.register(AiTool.builder("recipe_of", ToolCategory.KNOWLEDGE)
                .summary("How to make an item: crafting and smelting recipes.")
                .schema(item())
                .handler((ctx, args) -> recipeOf(knowledge.get(), args))
                .build());

        registry.register(AiTool.builder("uses_of", ToolCategory.KNOWLEDGE)
                .summary("What an item is used to make.")
                .schema(item())
                .handler((ctx, args) -> usesOf(knowledge.get(), args))
                .build());

        registry.register(AiTool.builder("drops_of", ToolCategory.KNOWLEDGE)
                .summary("What a mob or block drops, and what mining it takes.")
                .schema(ToolSchema.builder()
                        .string("thing", "A mob id (zombie) or block id (iron_ore).").required()
                        .build())
                .handler((ctx, args) -> dropsOf(knowledge.get(), args.string("thing")))
                .build());

        registry.register(AiTool.builder("where_from", ToolCategory.KNOWLEDGE)
                .summary("Every way to get an item: blocks to mine, mobs to kill, recipes.")
                .schema(item())
                .handler((ctx, args) -> whereFrom(knowledge.get(), args))
                .build());

        registry.register(AiTool.builder("item_lookup", ToolCategory.KNOWLEDGE)
                .summary("Turn a name into an item id, with close matches.")
                .schema(ToolSchema.builder()
                        .string("name", "What the item is called, e.g. \"iron pick\".").required()
                        .build())
                .handler((ctx, args) -> itemLookup(knowledge.get(), args.string("name")))
                .build());

        registry.register(AiTool.builder("fuels", ToolCategory.KNOWLEDGE)
                .summary("Furnace fuels and how many items each smelts.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> fuels(knowledge.get()))
                .build());
    }

    private static ToolSchema item() {
        return ToolSchema.builder().string("item", "The item, as an id or plain words.").required().build();
    }

    /** The item id for what was typed, or a failed result saying what was meant. */
    private static Object resolve(Knowledge k, String typed) {
        if (k == null) return ToolResult.failed("Game data isn't loaded yet.");
        Optional<String> id = k.resolveItem(typed);
        if (id.isPresent()) return id.get();
        List<String> close = k.suggest(typed, 3);
        return ToolResult.failed("No item called \"" + typed + "\"."
                + (close.isEmpty() ? "" : " Did you mean " + String.join(", ", close.stream().map(KnowledgeTools::plain).toList()) + "?"));
    }

    static ToolResult recipeOf(Knowledge k, ToolInput args) {
        Object resolved = resolve(k, args.string("item"));
        if (resolved instanceof ToolResult failed) return failed;
        String id = (String) resolved;
        List<String> lines = new ArrayList<>();
        for (Source source : k.sourcesFor(id)) {
            if (source instanceof CraftSource craft) {
                lines.add("craft " + craft.outputCount() + " from " + ingredients(craft.ingredients())
                        + (craft.needsTable() ? " (crafting table)" : " (2x2 grid)"));
            } else if (source instanceof SmeltSource smelt) {
                lines.add("smelt " + options(smelt.input()) + " in a " + plain(smelt.station()) + " (" + smelt.cookTicks() / 20 + " s)");
            }
        }
        if (lines.isEmpty()) {
            return ToolResult.ok(plain(id) + " can't be crafted or smelted; where_from lists how to get it.").fact("item", plain(id))
                    .fact("recipes", 0);
        }
        return ToolResult.ok(plain(id) + ": " + join(lines)).fact("item", plain(id)).fact("recipes", lines.size());
    }

    static ToolResult usesOf(Knowledge k, ToolInput args) {
        Object resolved = resolve(k, args.string("item"));
        if (resolved instanceof ToolResult failed) return failed;
        String id = (String) resolved;
        Set<String> made = new LinkedHashSet<>();
        for (Source source : k.allSources()) {
            if (source instanceof CraftSource craft && craft.ingredients().stream().anyMatch(i -> i.accepts(id))) {
                made.add(plain(craft.output()));
            } else if (source instanceof SmeltSource smelt && smelt.input().accepts(id)) {
                made.add(plain(smelt.output()) + " (smelted)");
            }
        }
        List<String> list = new ArrayList<>(made);
        if (list.isEmpty()) return ToolResult.ok(plain(id) + " isn't used in any recipe.").fact("item", plain(id)).fact("uses", 0);
        return ToolResult.ok(plain(id) + " makes: " + join(list)).fact("item", plain(id)).fact("uses", list.size());
    }

    static ToolResult dropsOf(Knowledge k, String typed) {
        if (k == null) return ToolResult.failed("Game data isn't loaded yet.");
        String id = baritone.ai.tool.CommandTool.id(typed);
        String full = "minecraft:" + id;
        List<String> lines = new ArrayList<>();
        ToolReq tool = null;
        boolean isBlock = false;
        for (Source source : k.allSources()) {
            if (source instanceof KillSource kill && (kill.entity().equals(full) || kill.entity().equals(id))) {
                lines.add(String.format("%.2f %s per kill%s", kill.dropsPerKill(), plain(kill.output()),
                        kill.needsPlayerKill() ? " (player kill)" : ""));
            } else if (source instanceof BarterSource barter && (barter.entity().equals(full) || barter.entity().equals(id))) {
                lines.add(String.format("%.3f %s per %s bartered", barter.perTrade(), plain(barter.output()), plain(barter.currency())));
            } else if (source instanceof MineSource mine && (mine.block().equals(full) || mine.block().equals(id))) {
                isBlock = true;
                tool = mine.tool();
                lines.add(String.format("%.2f %s per block%s", mine.dropsPerBlock(), plain(mine.output()),
                        mine.needsSilkTouch() ? " (silk touch)" : ""));
            }
        }
        if (lines.isEmpty()) return ToolResult.failed("Nothing known drops from \"" + typed + "\".");
        ToolResult result = ToolResult.ok(id + " drops " + join(lines) + (isBlock ? " " + toolText(tool) + "." : ""))
                .fact("thing", id).fact("drops", lines.size());
        if (isBlock && tool != null) {
            result.fact("tool", tool.type() == null ? "any" : tool.type()).fact("min_tier", tierName(tool.minTier()))
                    .fact("tool_required", tool.required());
        }
        return result;
    }

    static ToolResult whereFrom(Knowledge k, ToolInput args) {
        Object resolved = resolve(k, args.string("item"));
        if (resolved instanceof ToolResult failed) return failed;
        String id = (String) resolved;
        List<String> lines = new ArrayList<>();
        for (Source source : k.sourcesFor(id)) {
            switch (source) {
                case MineSource mine -> lines.add("mine " + plain(mine.block()) + " (" + toolText(mine.tool()).toLowerCase() + ")");
                case KillSource kill -> lines.add("kill " + plain(kill.entity()));
                case BarterSource barter -> lines.add("barter " + plain(barter.currency()) + " with " + plain(barter.entity()) + "s");
                case CraftSource craft -> lines.add("craft from " + ingredients(craft.ingredients()));
                case SmeltSource smelt -> lines.add("smelt " + options(smelt.input()));
            }
        }
        if (lines.isEmpty()) return ToolResult.ok("No known way to get " + plain(id) + ".").fact("item", plain(id)).fact("ways", 0);
        return ToolResult.ok(plain(id) + ": " + join(lines)).fact("item", plain(id)).fact("ways", lines.size());
    }

    static ToolResult itemLookup(Knowledge k, String typed) {
        if (k == null) return ToolResult.failed("Game data isn't loaded yet.");
        Optional<String> id = k.resolveItem(typed);
        List<String> close = k.suggest(typed, 5).stream().map(KnowledgeTools::plain).toList();
        if (id.isPresent()) return ToolResult.ok("\"" + typed + "\" is " + plain(id.get()) + ".").fact("item", plain(id.get()))
                .fact("similar", close);
        return ToolResult.failed("No item called \"" + typed + "\"." + (close.isEmpty() ? "" : " Close: " + String.join(", ", close) + "."))
                .fact("similar", close);
    }

    static ToolResult fuels(Knowledge k) {
        if (k == null) return ToolResult.failed("Game data isn't loaded yet.");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(k.fuels().entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sorted) {
            lines.add(plain(e.getKey()) + " " + trim(e.getValue() / 200.0));
        }
        return ToolResult.ok("Items smelted per fuel: " + join(lines)).fact("fuels", sorted.size());
    }

    private static String ingredients(List<Ingredient> ingredients) {
        List<String> parts = new ArrayList<>();
        for (Ingredient i : ingredients) parts.add(i.count() + " " + options(i));
        return String.join(", ", parts);
    }

    private static String options(Ingredient i) {
        List<String> names = i.anyOf().stream().map(KnowledgeTools::plain).toList();
        return names.size() <= 3 ? String.join(" or ", names) : names.get(0) + " (or " + (names.size() - 1) + " others)";
    }

    private static String toolText(ToolReq tool) {
        if (tool == null || tool.type() == null || tool.minTier() <= 0) return "Any tool or hand";
        return "Needs a " + tierName(tool.minTier()) + " " + tool.type() + (tool.required() ? "" : " for speed") + " or better";
    }

    private static String tierName(int tier) {
        return tier >= 0 && tier < TIER_NAMES.length ? TIER_NAMES[tier] : "tier " + tier;
    }

    /** Lines joined with "; ", the rest summed up past {@link #MAX_LINES}. */
    private static String join(List<String> lines) {
        if (lines.size() <= MAX_LINES) return String.join("; ", lines) + ".";
        return String.join("; ", lines.subList(0, MAX_LINES)) + "; and " + (lines.size() - MAX_LINES) + " more.";
    }

    static String plain(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format("%.1f", v);
    }
}
