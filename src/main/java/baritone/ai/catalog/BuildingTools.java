package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolSchema;

import java.util.List;

/** Building shapes in a box, through Baritone's selection builder. Each needs the blocks in the inventory. */
final class BuildingTools {

    private BuildingTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(shape("fill_region", "Fill a box solid with a block.", "set"));
        registry.register(shape("build_walls", "Build the four walls of a box with a block.", "walls"));
        registry.register(shape("build_shell", "Build walls, floor and ceiling of a box with a block (a hollow room).", "shell"));
        registry.register(shape("build_sphere", "Build a sphere filling a box with a block.", "sphere"));
        registry.register(shape("build_hollow_sphere", "Build a hollow sphere filling a box with a block.", "hsphere"));

        registry.register(Cmd.start(AiTool.builder("replace_blocks", ToolCategory.BUILDING)
                .summary("Replace every block of one kind in a box with another.")
                .schema(Cmd.region(ToolSchema.builder())
                        .string("from", "The block to replace.").required()
                        .string("to", "The block to put instead.").required()
                        .build()), "build", args -> Cmd.onRegion(args, "replace " + Cmd.id(args, "from") + " " + Cmd.id(args, "to"))));

        registry.register(Cmd.run(AiTool.builder("clear_selection", ToolCategory.BUILDING)
                .summary("Clear the building selection (the box shown in the world).")
                .schema(ToolSchema.EMPTY), args -> List.of("sel clear")));
    }

    private static AiTool shape(String name, String summary, String action) {
        return Cmd.start(AiTool.builder(name, ToolCategory.BUILDING)
                .summary(summary)
                .schema(Cmd.region(ToolSchema.builder())
                        .string("block", "The block to build with, e.g. cobblestone.").required()
                        .build()), "build", args -> Cmd.onRegion(args, action + " " + Cmd.id(args, "block")));
    }
}
