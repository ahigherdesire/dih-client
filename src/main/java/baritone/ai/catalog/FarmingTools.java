package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolSchema;

import java.util.ArrayList;
import java.util.List;

/** Picking things up and sleeping. Farming crops is the farm job. */
final class FarmingTools {

    private FarmingTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(Cmd.start(AiTool.builder("collect_drops", ToolCategory.FARMING)
                .summary("Pick up dropped items nearby: all, or only some kinds.")
                .schema(ToolSchema.builder()
                        .string("items", "Item ids to pick up, separated by spaces (default: everything).")
                        .build()), "pickup", args -> {
                    if (!args.has("items") || args.string("items").isBlank()) return List.of("pickup");
                    List<String> ids = new ArrayList<>();
                    for (String item : args.string("items").trim().split("[\\s,]+")) {
                        ids.add(baritone.ai.tool.CommandTool.id(item));
                    }
                    return List.of("pickup " + String.join(" ", ids));
                }));

        registry.register(Cmd.start(AiTool.builder("sleep_in_bed", ToolCategory.FARMING)
                .summary("Walk to the nearest bed and sleep (waits for night if it's day).")
                .schema(ToolSchema.EMPTY), "sleep", args -> List.of("sleep")));
    }
}
