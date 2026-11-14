package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolSchema;

import java.util.List;

/** Mining blocks, tunnels and areas. For "get me N of an item", acquire is the better tool. */
final class MiningTools {

    private MiningTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(Cmd.start(AiTool.builder("mine_block", ToolCategory.MINING)
                .summary("Mine every block of a kind it can find, or count of them.")
                .description("Mine blocks of a kind (stone and deepslate ore variants count as one), stopping after count "
                        + "blocks, or never. For items, prefer acquire: it plans tools and smelting too.")
                .schema(ToolSchema.builder()
                        .string("block", "The block id, e.g. iron_ore or oak_log.").required()
                        .integer("count", "How many to mine (default: until stopped).").range(1, 100000)
                        .build()), "mine", args -> List.of("mine " + (args.has("count") ? args.integer("count") + " " : "")
                        + Cmd.id(args, "block"))));

        registry.register(Cmd.start(AiTool.builder("tunnel", ToolCategory.MINING)
                .summary("Dig a tunnel forward: 1x2 by default, or height x width x depth.")
                .schema(ToolSchema.builder()
                        .integer("height", "Tunnel height.").range(1, 16)
                        .integer("width", "Tunnel width.").range(1, 16)
                        .integer("depth", "How far forward.").range(1, 100000)
                        .build()), "tunnel", args -> {
                    boolean any = args.has("height") || args.has("width") || args.has("depth");
                    boolean all = args.has("height") && args.has("width") && args.has("depth");
                    if (any && !all) throw new IllegalArgumentException("Give height, width and depth together, or none.");
                    return List.of(all ? "tunnel " + args.integer("height") + " " + args.integer("width") + " "
                            + args.integer("depth") : "tunnel");
                }));

        registry.register(Cmd.start(AiTool.builder("clear_area", ToolCategory.MINING)
                .summary("Dig out every block in a box.")
                .schema(Cmd.region(ToolSchema.builder()).build()), "build", args -> Cmd.onRegion(args, "cleararea")));
    }
}
