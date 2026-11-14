package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.List;

/** Places: waypoints, structures, coordinates, player sightings, remembered chests, the biome here. */
final class WorldTools {

    private WorldTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(Cmd.run(AiTool.builder("waypoint_add", ToolCategory.WORLD)
                .summary("Save a named waypoint here, or at x y z.")
                .schema(ToolSchema.builder()
                        .string("name", "The waypoint's name, one word.").required()
                        .integer("x", "X (default: here).")
                        .integer("y", "Y.")
                        .integer("z", "Z.")
                        .build()), args -> {
                    String name = MovementTools.name(args.string("name"));
                    boolean any = args.has("x") || args.has("y") || args.has("z");
                    boolean all = args.has("x") && args.has("y") && args.has("z");
                    if (any && !all) throw new IllegalArgumentException("Give all of x, y and z, or none.");
                    return List.of("wp save user " + name + (all ? " " + Cmd.xyz(args, "") : ""));
                }));

        registry.register(Cmd.run(AiTool.builder("waypoint_list", ToolCategory.WORLD)
                .summary("List saved waypoints.")
                .schema(ToolSchema.EMPTY), args -> List.of("wp list")));

        registry.register(Cmd.run(AiTool.builder("waypoint_remove", ToolCategory.WORLD)
                .summary("Delete a waypoint by name.")
                .schema(ToolSchema.builder()
                        .string("name", "The waypoint's name.").required()
                        .build()), args -> List.of("wp delete " + MovementTools.name(args.string("name")))));

        registry.register(Cmd.start(AiTool.builder("waypoint_goto", ToolCategory.WORLD)
                .summary("Walk to a waypoint by name.")
                .schema(ToolSchema.builder()
                        .string("name", "The waypoint's name.").required()
                        .build()), "goto", args -> List.of("wp goto " + MovementTools.name(args.string("name")))));

        registry.register(Cmd.run(AiTool.builder("nearest_structure", ToolCategory.WORLD)
                .summary("Coordinates of the nearest structure of a kind, without going there.")
                .description("Coordinates of the nearest structure of a kind, from the seed map when the seed is known. "
                        + "Changes nothing; goto_structure goes there.")
                .schema(ToolSchema.builder()
                        .enumOf("structure", "Which structure.", JobTools.STRUCTURES).required()
                        .build()), args -> List.of("where " + args.string("structure"))));

        registry.register(Cmd.run(AiTool.builder("nether_coords", ToolCategory.WORLD)
                .summary("Convert coordinates between the Overworld and the Nether (divide or multiply by 8).")
                .schema(ToolSchema.builder()
                        .integer("x", "X.").required()
                        .integer("z", "Z.").required()
                        .enumOf("from", "Which dimension the coordinates are in.", "overworld", "nether").required()
                        .build()), args -> List.of("nether " + args.string("from") + " " + args.integer("x") + " 64 "
                        + args.integer("z"))));

        registry.register(Cmd.run(AiTool.builder("players_seen", ToolCategory.WORLD)
                .summary("Players seen before, or one player's sighting history.")
                .schema(ToolSchema.builder()
                        .string("player", "A player's name (default: everyone).")
                        .build()), args -> List.of(args.has("player")
                        ? "players " + MovementTools.name(args.string("player")) : "players")));

        registry.register(Cmd.run(AiTool.builder("find_in_chests", ToolCategory.WORLD)
                .summary("Which remembered chests hold an item.")
                .description("Search the contents of every container opened before for an item, nearest first. Only "
                        + "containers the player has opened are known.")
                .schema(ToolSchema.builder()
                        .string("item", "The item id.").required()
                        .build()), args -> List.of("chest " + Cmd.id(args, "item"))));

        registry.register(AiTool.builder("biome_here", ToolCategory.WORLD)
                .gameThread()
                .summary("The biome, dimension, height and light level where the player stands.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> biomeHere())
                .build());
    }

    private static ToolResult biomeHere() {
        LocalPlayer player = Minecraft.getInstance().player;
        Level level = player == null ? null : player.level();
        if (level == null) return ToolResult.failed("Not in a world.");
        BlockPos feet = player.blockPosition();
        String biome = level.getBiome(feet).unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
        String dimension = level.dimension().identifier().getPath();
        int block = level.getBrightness(LightLayer.BLOCK, feet);
        int sky = level.getBrightness(LightLayer.SKY, feet);
        return ToolResult.ok(biome + " in the " + dimension + " at Y " + feet.getY() + "; light: block " + block + ", sky " + sky + ".")
                .fact("biome", biome).fact("dimension", dimension).fact("y", feet.getY())
                .fact("block_light", block).fact("sky_light", sky);
    }
}
