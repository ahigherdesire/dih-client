package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolSchema;

import java.util.List;

/** Getting around: following, heading a way, homes, portals, elytra. */
final class MovementTools {

    private MovementTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(Cmd.start(AiTool.builder("follow_player", ToolCategory.MOVEMENT)
                .summary("Follow a player by name.")
                .schema(ToolSchema.builder()
                        .string("player", "The player's name.").required()
                        .build()), "follow", args -> List.of("follow player " + name(args.string("player")))));

        registry.register(Cmd.start(AiTool.builder("follow_mob", ToolCategory.MOVEMENT)
                .summary("Follow the nearest mob of a kind (cow, horse, ...).")
                .schema(ToolSchema.builder()
                        .string("mob", "The mob id, e.g. cow or horse.").required()
                        .build()), "follow", args -> List.of("follow entity " + Cmd.id(args, "mob"))));

        registry.register(Cmd.start(AiTool.builder("head_forward", ToolCategory.MOVEMENT)
                .summary("Travel a distance in the direction the player faces.")
                .schema(ToolSchema.builder()
                        .integer("distance", "How many blocks.").range(1, 30000000).required()
                        .build()), "goto", args -> List.of("thisway " + args.integer("distance"), "path")));

        registry.register(Cmd.start(AiTool.builder("go_to_surface", ToolCategory.MOVEMENT)
                .summary("Get out of a cave or mine to open sky.")
                .schema(ToolSchema.EMPTY), "goto", args -> List.of("surface")));

        registry.register(Cmd.start(AiTool.builder("elytra_to", ToolCategory.MOVEMENT)
                .summary("Fly with an elytra to x z (or x y z).")
                .description("Fly with the worn elytra to x z at any height, or to x y z. Needs an elytra worn and "
                        + "fireworks in the inventory; in the Nether it follows a pathfinder.")
                .schema(ToolSchema.builder()
                        .integer("x", "Target X.").required()
                        .integer("z", "Target Z.").required()
                        .integer("y", "Target Y (default: any).")
                        .build()), "elytra", args -> List.of("elytra goto " + args.integer("x")
                        + (args.has("y") ? " " + args.integer("y") : "") + " " + args.integer("z"))));

        registry.register(Cmd.start(AiTool.builder("home_go", ToolCategory.MOVEMENT)
                .summary("Walk to a saved home.")
                .schema(ToolSchema.builder()
                        .string("name", "The home's name.").required()
                        .build()), "goto", args -> List.of("home " + name(args.string("name")))));

        registry.register(Cmd.run(AiTool.builder("home_set", ToolCategory.MOVEMENT)
                .summary("Save the current spot as a named home.")
                .schema(ToolSchema.builder()
                        .string("name", "The home's name.").required()
                        .build()), args -> List.of("home set " + name(args.string("name")))));

        registry.register(Cmd.run(AiTool.builder("home_list", ToolCategory.MOVEMENT)
                .summary("List saved homes with their distances.")
                .schema(ToolSchema.EMPTY), args -> List.of("home list")));

        registry.register(Cmd.run(AiTool.builder("home_delete", ToolCategory.MOVEMENT)
                .summary("Delete a saved home.")
                .schema(ToolSchema.builder()
                        .string("name", "The home's name.").required()
                        .build()), args -> List.of("home del " + name(args.string("name")))));

        registry.register(Cmd.start(AiTool.builder("goto_portal", ToolCategory.MOVEMENT)
                .summary("Walk to the nearest nether portal (or skip it for the next).")
                .schema(ToolSchema.builder()
                        .bool("skip_nearest", "Skip the nearest portal and go to the next one.").defaultsTo(false)
                        .build()), "goto", args -> List.of(args.bool("skip_nearest") ? "portal skip" : "portal")));

        registry.register(Cmd.start(AiTool.builder("goto_axis", ToolCategory.MOVEMENT)
                .summary("Walk to the nearest axis or diagonal highway.")
                .schema(ToolSchema.EMPTY), "goto", args -> List.of("axis", "path")));

        registry.register(Cmd.run(AiTool.builder("pause", ToolCategory.MOVEMENT)
                .summary("Pause the running job (resume carries on).")
                .schema(ToolSchema.EMPTY), args -> List.of("pause")));

        registry.register(Cmd.run(AiTool.builder("resume", ToolCategory.MOVEMENT)
                .summary("Resume a paused job.")
                .schema(ToolSchema.EMPTY), args -> List.of("resume")));
    }

    /** A player, home or waypoint name: one word. */
    static String name(String text) {
        String name = text == null ? "" : text.trim();
        if (!name.matches("[A-Za-z0-9_.-]{1,40}")) {
            throw new IllegalArgumentException("Names are one word of letters, digits and _ (got \"" + name + "\").");
        }
        return name;
    }
}
