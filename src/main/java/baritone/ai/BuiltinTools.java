package baritone.ai;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Goal;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandRunner;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;

import java.util.List;
import java.util.Locale;

/**
 * The AI's first eight tools. The mod's own command set is the real action space, so {@code run_command} does most
 * of the work. {@code acquire} and {@code plan_item} front the {@code #acquire} planner, {@code find} locates things,
 * and the rest is looking, waiting, talking and remembering. All eight are job tools: always in the model's list.
 */
public final class BuiltinTools {

    private BuiltinTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("run_command", ToolCategory.RAW)
                .job()
                .summary("Run one # command, e.g. goto 100 64 -200.")
                .description("Run one DIH Client command, e.g. \"goto 100 64 -200\", \"mine diamond_ore\", "
                        + "\"follow player Steve\", \"stop\". Omit the # prefix. Commands start a job and return "
                        + "immediately; the result includes what the command printed. Check progress with "
                        + "look_around. To get or make items use the acquire tool instead.")
                .schema(ToolSchema.builder()
                        .string("command", "The command with its arguments, without the # prefix.").required()
                        .build())
                .handler((ctx, args) -> runCommand(ctx, args.string("command")))
                .build());

        registry.register(AiTool.builder("acquire", ToolCategory.JOB)
                .summary("Get an item by any means: mine, craft, smelt, kill.")
                .description("Get an item by any means: plans and runs the whole chain itself (mining, crafting, "
                        + "smelting, killing mobs) from the current inventory. Use it for any \"get me X\", "
                        + "\"make me X\" or \"craft X\" request instead of chaining mine and craft commands by "
                        + "hand. One acquire runs at a time; the stop command cancels it.")
                .schema(ToolSchema.builder()
                        .string("item", "The item, as an id or plain words, e.g. \"iron_pickaxe\" or \"torch\"; "
                                + "\"food\" gets the cheapest food.").required()
                        .integer("count", "How many to have in the inventory. Default 1.").range(1, ItemRequest.MAX_COUNT).defaultsTo(1)
                        .build())
                .handler((ctx, args) -> acquire(ctx, ItemRequest.parse(args.json())))
                .build());

        registry.register(AiTool.builder("plan_item", ToolCategory.JOB)
                .summary("Dry run of acquire: the steps to get an item.")
                .description("Dry run of acquire: returns the numbered steps needed to get an item, or why it can't "
                        + "be done, and changes nothing. Use it to answer \"how do I make X\" or \"what do I "
                        + "need for X\", and to check feasibility before committing to an acquire.")
                .schema(ToolSchema.builder()
                        .string("item", "The item, as an id or plain words, e.g. \"diamond_pickaxe\".").required()
                        .integer("count", "How many. Default 1.").range(1, ItemRequest.MAX_COUNT).defaultsTo(1)
                        .build())
                .handler((ctx, args) -> planItem(ctx, ItemRequest.parse(args.json())))
                .build());

        registry.register(AiTool.builder("find", ToolCategory.JOB)
                .gameThread()
                .summary("Find the nearest block, mob or player of a kind.")
                .description("Find the nearest known block, mob or player of a kind, with distance, compass direction "
                        + "and coordinates. Searches loaded chunks and Baritone's block cache; read-only.")
                .schema(ToolSchema.builder()
                        .string("target", "A block id (diamond_ore, chest, oak_log), a mob id (cow, zombie) or a player name.").required()
                        .build())
                .handler((ctx, args) -> {
                    if (ctx.baritone() == null) {
                        return ToolResult.failed("Not in a world.");
                    }
                    return ToolResult.ok(WorldFinder.find(ctx.baritone(), args.string("target")));
                })
                .build());

        registry.register(AiTool.builder("say", ToolCategory.CHAT)
                .job()
                .summary("Say something in server chat.")
                .description("Say something in server chat. Use it to answer players and to report what you are doing.")
                .schema(ToolSchema.builder()
                        .string("message", "Plain text, under 200 characters. Cannot start with /.").required()
                        .build())
                .handler((ctx, args) -> {
                    if (ctx.brain() == null) {
                        return ToolResult.failed("Not in a game.");
                    }
                    String result = ctx.brain().speak(args.string("message"));
                    return result.startsWith("Refused") || result.startsWith("Could not") || result.startsWith("Nothing")
                            ? ToolResult.failed(result) : ToolResult.ok(result);
                })
                .build());

        registry.register(AiTool.builder("look_around", ToolCategory.JOB)
                .gameThread()
                .summary("Report position, health, inventory, mobs and the running job.")
                .description("Get a fresh report of your position, health, food, inventory, nearby players and mobs, "
                        + "and what job is currently running, including acquire progress.")
                .handler((ctx, args) -> ctx.baritone() == null
                        ? ToolResult.failed("Cannot see anything right now.")
                        : ToolResult.ok(WorldSnapshot.describe(ctx.baritone())))
                .build());

        registry.register(AiTool.builder("wait", ToolCategory.JOB)
                .summary("Wait a few seconds, then report.")
                .description("Do nothing for a few seconds while a job runs, then get a fresh status report.")
                .schema(ToolSchema.builder()
                        .integer("seconds", "How long to wait, 1 to 30.").required().range(1, 30)
                        .build())
                .handler((ctx, args) -> {
                    int seconds = args.integer("seconds");
                    AiBrain.sleepQuietly(seconds * 1000L);
                    return ToolResult.ok("Waited " + seconds + "s. You are now " + ctx.brief() + ".");
                })
                .build());

        registry.register(AiTool.builder("remember", ToolCategory.JOB)
                .summary("Store one short fact for later sessions.")
                .description("Store one short fact for later sessions. Facts about this world (a base coordinate, where "
                        + "the village is) are kept per world; facts about the player (their habits, what they like) everywhere.")
                .schema(ToolSchema.builder()
                        .string("fact", "One sentence.").required()
                        .enumOf("about", "What the fact is about.", "world", "player").defaultsTo("world")
                        .build())
                .handler((ctx, args) -> {
                    if (ctx.memory() == null) return ToolResult.failed("Memory isn't loaded.");
                    AiMemory memory = "player".equals(args.string("about")) && ctx.brain() != null
                            ? ctx.brain().getMemories().global() : ctx.memory();
                    return ToolResult.ok(memory.remember(args.string("fact"))).fact("about", args.string("about"));
                })
                .build());
    }

    private static ToolResult runCommand(ToolContext ctx, String rawInput) {
        String raw = rawInput == null ? "" : rawInput.trim();
        while (raw.startsWith("#")) {
            raw = raw.substring(1).trim();
        }
        if (raw.isEmpty()) {
            return ToolResult.failed("No command given.");
        }
        if (raw.startsWith("/")) {
            return ToolResult.failed("Refused: those are server commands, and you may only run DIH Client commands.");
        }

        String name = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        CommandRunner commands = ctx.commands();
        if (ctx.source() == ToolContext.Source.AI) {
            // The deny list limits the AI; a player or their macro can type # commands anyway.
            String refusal = refusal(ctx, name, commands.baritoneNames(name));
            if (refusal != null) {
                return ToolResult.failed(refusal);
            }
        }

        CommandRunner.Outcome outcome = commands.baritone(raw);
        if (!outcome.handled()) {
            return ToolResult.failed("\"" + name + "\" is not a command. Check the command list before trying again.");
        }
        String state = ctx.brief();
        return ToolResult.ok("Ran #" + raw + "."
                + (outcome.output().isEmpty() ? "" : "\nIt printed: " + outcome.output())
                + "\nYou are now " + state + ".").fact("command", "#" + raw);
    }

    /**
     * Why the AI may not run the {@code #} command called {@code name} (which also goes by {@code names}), or null.
     * Shared by run_command and the per-command adapters.
     */
    static String refusal(ToolContext ctx, String name, List<String> names) {
        AiConfig config = ctx.config() == null ? new AiConfig() : ctx.config();
        if (!config.allowsCommand(name, names)) {
            return "Refused: \"" + name + "\" is on the deny list and cannot be run by the AI.";
        }
        if (name.equals("acquire") || names.contains("acquire")) {
            // Routed through the tools so the AI's acquires are tracked and report back.
            return "Use the acquire tool to start an acquire, or plan_item for a dry run. Its progress shows "
                    + "in look_around, and run_command \"stop\" cancels it.";
        }
        return null;
    }

    /** Starts an acquire of {@code count} {@code item} as the acquire tool would, for other tools (gear_up). */
    public static ToolResult startAcquire(ToolContext ctx, String item, int count) {
        com.google.gson.JsonObject args = new com.google.gson.JsonObject();
        args.addProperty("item", item);
        args.addProperty("count", count);
        return acquire(ctx, ItemRequest.parse(args));
    }

    private static ToolResult acquire(ToolContext ctx, ItemRequest request) {
        if (!request.ok()) {
            return ToolResult.failed(request.error());
        }
        String what = "acquiring " + request.count() + " " + request.item();
        ToolResult result = begin(ctx, control -> () -> control.start(request.item(), request.count()), what,
                " plan_item shows what is missing; a different item name may help.");
        return result.ok() ? result.fact("item", request.item()).fact("count", request.count()) : result;
    }

    /**
     * Starts an acquire of a goal that isn't an item (being in the Nether, at a fortress) as the acquire tool would,
     * for the nether_end tools.
     */
    public static ToolResult startGoal(ToolContext ctx, Goal goal) {
        return begin(ctx, control -> () -> control.startGoal(goal), "getting to " + goal.label(), "");
    }

    /** Starts bartering with piglins for {@code count} of {@code item}, as the acquire tool would. */
    public static ToolResult startBarter(ToolContext ctx, String item, int count) {
        return begin(ctx, control -> () -> control.startBarter(item, count), "bartering for " + item, "");
    }

    /** The acquire tool's start: refusals, the game thread, and whose acquire it is. */
    private static ToolResult begin(ToolContext ctx, java.util.function.Function<AcquireControl, java.util.function.Supplier<String>> start,
                                    String what, String hint) {
        AiBrain brain = ctx.brain();
        if (brain == null) {
            return ToolResult.failed("Not in a game.");
        }
        String refusal = ctx.source() == ToolContext.Source.AI ? AiTools.acquireRefusal(brain.getConfig()) : null;
        if (refusal != null) {
            return ToolResult.failed(refusal);
        }
        AcquireControl control = AcquireControl.get();
        if (control == null) {
            return ToolResult.failed(AiTools.ACQUIRE_UNAVAILABLE);
        }
        String result;
        if (ctx.source() == ToolContext.Source.AI && !ctx.isDirected()) {
            boolean eventsOn = brain.getConfig().followUpsActive();
            result = brain.onGameThread(() -> {
                brain.attachAcquireListener(control);
                return AiTools.startAsAi(start.apply(control), what, brain.getFollowUps(), eventsOn, hint);
            }, "Timed out while starting. Check look_around before trying again.");
        } else {
            // Started by a player, a macro or a director's plan: the chat AI mustn't treat its end as its own follow-up.
            result = brain.onGameThread(() -> AiTools.startByHand(start.apply(control), what), "Timed out while starting.");
        }
        String text = result + "\nYou are now " + ctx.brief() + ".";
        return result.startsWith("Started") ? ToolResult.running("acquire", text) : ToolResult.failed(text);
    }

    private static ToolResult planItem(ToolContext ctx, ItemRequest request) {
        if (!request.ok()) {
            return ToolResult.failed(request.error());
        }
        AiBrain brain = ctx.brain();
        if (brain == null) {
            return ToolResult.failed("Not in a game.");
        }
        String refusal = ctx.source() == ToolContext.Source.AI ? AiTools.acquireRefusal(brain.getConfig()) : null;
        if (refusal != null) {
            return ToolResult.failed(refusal);
        }
        AcquireControl control = AcquireControl.get();
        if (control == null) {
            return ToolResult.failed(AiTools.ACQUIRE_UNAVAILABLE);
        }
        String plan = brain.onGameThread(() -> AiTools.planAcquire(control, request), "Planning timed out.");
        return plan.startsWith("Plan for") ? ToolResult.ok(plan) : ToolResult.failed(plan);
    }
}
