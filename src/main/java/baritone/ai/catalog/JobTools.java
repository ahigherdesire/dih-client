package baritone.ai.catalog;

import baritone.ai.BuiltinTools;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The big deterministic jobs, always in the model's list next to acquire. */
final class JobTools {

    /** Structures {@code #structure} and {@code #where} know, by their plain names. */
    static final String[] STRUCTURES = {"village", "stronghold", "nether_fortress", "bastion", "mansion", "monument",
            "ancient_city", "end_city", "mineshaft", "buried_treasure", "desert_pyramid", "jungle_pyramid", "igloo",
            "ocean_ruin", "pillager_outpost", "ruined_portal", "shipwreck", "swamp_hut", "trail_ruins", "trial_chamber"};

    /** Gear tiers, worst first. Wood and stone have no armour. */
    static final List<String> TIERS = List.of("wooden", "stone", "iron", "diamond", "netherite");
    private static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");

    private JobTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(Cmd.start(AiTool.builder("goto", ToolCategory.JOB)
                .summary("Walk to coordinates, a Y level, or the nearest block of a kind.")
                .description("Walk (pathfinding, digging and bridging as needed) to \"x y z\", \"x z\", a Y level \"y\", "
                        + "or the nearest known block of a kind such as \"diamond_ore\" or \"crafting_table\". Returns at "
                        + "once; check progress with look_around, cancel with stop.")
                .schema(ToolSchema.builder()
                        .string("target", "\"x y z\", \"x z\", a Y level, or a block id.").required()
                        .build()), "goto", args -> List.of(gotoCommand(args.string("target")))));

        registry.register(Cmd.start(AiTool.builder("goto_structure", ToolCategory.JOB)
                .summary("Travel to the nearest structure of a kind.")
                .description("Travel to the nearest structure of a kind (a village, a nether_fortress, a stronghold, ...), "
                        + "using the seed map when the seed is known and exploring otherwise.")
                .schema(ToolSchema.builder()
                        .enumOf("structure", "Which structure.", STRUCTURES).required()
                        .build()), "goto_structure", args -> List.of("structure " + args.string("structure"))));

        registry.register(Cmd.start(AiTool.builder("explore", ToolCategory.JOB)
                .summary("Explore new chunks, from here or from x z.")
                .description("Walk out into chunks not seen yet, spiralling from here or from x z. Use it to find new "
                        + "terrain or structures. Runs until stopped.")
                .schema(ToolSchema.builder()
                        .integer("x", "Centre X (default: here).")
                        .integer("z", "Centre Z (default: here).")
                        .build()), "explore", args -> {
                    if (args.has("x") != args.has("z")) throw new IllegalArgumentException("Give both x and z, or neither.");
                    return List.of(args.has("x") ? "explore " + args.integer("x") + " " + args.integer("z") : "explore");
                }));

        registry.register(Cmd.start(AiTool.builder("farm", ToolCategory.JOB)
                .summary("Harvest and replant nearby crops.")
                .description("Harvest ripe crops and replant them, within range blocks of here (or everywhere loaded). "
                        + "Runs until stopped.")
                .schema(ToolSchema.builder()
                        .integer("range", "How far from here to farm (default: everything loaded).").range(1, 256)
                        .build()), "farm", args -> List.of(args.has("range") ? "farm " + args.integer("range") : "farm")));

        registry.register(Cmd.start(AiTool.builder("flee_to_safety", ToolCategory.JOB)
                .summary("Run a distance away from here.")
                .description("Run away from the current spot, distance blocks (default 32). For getting clear of danger "
                        + "the Guardian doesn't handle, such as a raid or a griefer.")
                .schema(ToolSchema.builder()
                        .integer("distance", "How far to run.").range(8, 512).defaultsTo(32)
                        .build()), "flee_to_safety", args -> List.of("runaway " + args.integer("distance"))));

        registry.register(Cmd.run(AiTool.builder("stop", ToolCategory.JOB)
                .summary("Stop whatever is running: walking, mining, building, acquiring.")
                .schema(ToolSchema.EMPTY), args -> List.of("stop")));

        registry.register(Cmd.start(AiTool.builder("build_schematic", ToolCategory.JOB)
                .summary("Build a schematic file, here or at x y z.")
                .description("Build a schematic from the schematics folder, at the player's position or at x y z. "
                        + "Gathers the blocks it has; missing blocks stop it.")
                .schema(ToolSchema.builder()
                        .string("file", "The schematic's file name, without .schematic.").required()
                        .integer("x", "Where to build: X (default: here).")
                        .integer("y", "Where to build: Y.")
                        .integer("z", "Where to build: Z.")
                        .build()), "build", args -> {
                    String file = args.string("file").trim();
                    if (file.isEmpty() || file.contains(" ")) throw new IllegalArgumentException("file must be one word.");
                    boolean any = args.has("x") || args.has("y") || args.has("z");
                    boolean all = args.has("x") && args.has("y") && args.has("z");
                    if (any && !all) throw new IllegalArgumentException("Give all of x, y and z, or none.");
                    return List.of("build " + file + (all ? " " + Cmd.xyz(args, "") : ""));
                }));

        registry.register(AiTool.builder("gear_up", ToolCategory.JOB)
                .summary("Get a full set of tools (and armour) of a tier, one piece at a time.")
                .description("Get the sword, pickaxe and axe of a tier, and for iron and up the four armour pieces, "
                        + "skipping what is already held at that tier or better. Starts an acquire for the first missing "
                        + "piece and says what else is missing; call it again when that acquire finishes.")
                .schema(ToolSchema.builder()
                        .enumOf("tier", "Which tier.", TIERS.toArray(String[]::new)).required()
                        .bool("armor", "Also get armour (iron and up).").defaultsTo(true)
                        .build())
                .handler(JobTools::gearUp)
                .build());

        registry.register(AiTool.builder("beat_stage", ToolCategory.JOB)
                .summary("Beat the game (#beat): start or resume the campaign, stop it, or see its phase and plan.")
                .description("Beat the game as a saved campaign of 9 phases: gear, a nether portal, blaze rods, pearls, "
                        + "home to the Overworld, eyes of ender, the stronghold, the End, the dragon. start begins (or carries on with this world's "
                        + "saved campaign) and runs until a phase finishes the game or stops with a reason; status gives "
                        + "\"Phase 3/9: blaze rods 4/7\"; plan lists every phase's steps; resume continues after a stop.")
                .schema(ToolSchema.builder()
                        .enumOf("action", "What to do.", "start", "resume", "stop", "status", "plan").defaultsTo("status")
                        .build())
                .handler((ctx, args) -> switch (args.string("action")) {
                    case "start" -> CommandTool.start(ctx, "beat", "beat");
                    case "resume" -> CommandTool.start(ctx, "beat", "beat resume");
                    default -> CommandTool.run(ctx, "beat " + args.string("action"));
                })
                .build());
    }

    /** {@code #goto} for "x y z", "x z", a Y level or a block id. */
    static String gotoCommand(String target) {
        String[] parts = target == null ? new String[0] : target.trim().split("\\s+");
        if (parts.length == 1 && !parts[0].isEmpty() && !coordinate(parts[0])) {
            String id = CommandTool.id(parts[0]);
            if (id.matches("[a-z0-9_:./-]+")) return "goto " + id;
        }
        if (parts.length >= 1 && parts.length <= 3 && !parts[0].isEmpty()) {
            boolean allCoordinates = true;
            for (String part : parts) allCoordinates &= coordinate(part);
            if (allCoordinates) return "goto " + String.join(" ", parts);
        }
        throw new IllegalArgumentException("target must be \"x y z\", \"x z\", a Y level, or a block id like diamond_ore.");
    }

    /** A whole number, or a relative ~ / ~n. */
    private static boolean coordinate(String s) {
        return s.matches("-?\\d+") || s.matches("~(-?\\d+)?");
    }

    /**
     * The pieces of {@code tier} still missing, in the order to get them: tools first (a pickaxe makes the rest
     * possible), then armour. A piece counts as held when the same kind is held at that tier or better.
     */
    static List<String> missingGear(String tier, boolean armor, Predicate<String> held) {
        int rank = TIERS.indexOf(tier);
        if (rank < 0) throw new IllegalArgumentException("Unknown tier " + tier + ".");
        List<String> kinds = new ArrayList<>(List.of("pickaxe", "sword", "axe"));
        if (armor && rank >= TIERS.indexOf("iron")) kinds.addAll(ARMOR);
        List<String> missing = new ArrayList<>();
        for (String kind : kinds) {
            boolean have = false;
            for (int better = rank; better < TIERS.size() && !have; better++) {
                have = held.test("minecraft:" + TIERS.get(better) + "_" + kind);
            }
            if (!have) missing.add("minecraft:" + tier + "_" + kind);
        }
        return missing;
    }

    private static ToolResult gearUp(ToolContext ctx, baritone.ai.tool.ToolInput args) {
        String tier = args.string("tier");
        Set<String> held = ctx.onGameThread(JobTools::heldItems, null);
        if (held == null) return ToolResult.failed("Not in a world.");
        List<String> missing = missingGear(tier, args.bool("armor"), held::contains);
        if (missing.isEmpty()) {
            return ToolResult.ok("Already have " + tier + " gear.").fact("tier", tier).fact("missing", List.of());
        }
        String first = missing.get(0);
        ToolResult started = BuiltinTools.startAcquire(ctx, first, 1);
        if (started.status() == ToolResult.Status.FAILED) return started;
        List<String> later = missing.subList(1, missing.size());
        return ToolResult.running("acquire", "Getting " + first.substring("minecraft:".length()) + " first"
                        + (later.isEmpty() ? "." : "; then still missing: " + String.join(", ", later.stream()
                        .map(id -> id.substring("minecraft:".length())).toList()) + ". Call gear_up again when it finishes."))
                .fact("tier", tier).fact("item", first).fact("missing", missing);
    }

    /** Item ids in the inventory and worn. Game thread. */
    private static Set<String> heldItems() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }
}
