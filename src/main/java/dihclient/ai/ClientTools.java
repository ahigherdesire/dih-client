package dihclient.ai;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolInput;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import dihclient.modules.Module;
import dihclient.modules.ModuleCategory;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.TeamsModule;
import dihclient.trade.EnchantPlanner;
import dihclient.trade.TradeCaches;
import dihclient.trade.TradeOffer;
import dihclient.trade.TradeRule;
import dihclient.trade.VillagerOffers;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The client's own tools: modules, macros, servers, friends and villager trading. */
public final class ClientTools {

    private ClientTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("module_list", ToolCategory.CLIENT)
                .gameThread()
                .summary("List the client's modules and which are on.")
                .schema(ToolSchema.builder()
                        .string("category", "Only this category: combat, movement, player, misc or render.")
                        .build())
                .handler((ctx, args) -> moduleList(args))
                .build());

        registry.register(AiTool.builder("module_toggle", ToolCategory.CLIENT)
                .gameThread()
                .summary("Turn a module on or off. Combat modules ask first.")
                .schema(ToolSchema.builder()
                        .string("module", "The module's name, e.g. Fullbright or AutoTrade.").required()
                        .enumOf("state", "on, off, or toggle.", "on", "off", "toggle").defaultsTo("toggle")
                        .build())
                .handler(ClientTools::moduleToggle)
                .build());

        registry.register(AiTool.builder("module_setting", ToolCategory.CLIENT)
                .gameThread()
                .summary("List a module's settings, read one, or change one.")
                .schema(ToolSchema.builder()
                        .string("module", "The module's name.").required()
                        .string("key", "The setting's id (default: list them all).")
                        .string("value", "A new value, or \"reset\" for the default (default: just read it).")
                        .build())
                .handler((ctx, args) -> moduleSetting(args))
                .build());

        registry.register(AiTool.builder("macro_list", ToolCategory.CLIENT)
                .gameThread()
                .summary("List saved macros by folder.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> {
                    List<String> names = new ArrayList<>();
                    for (DihMacro macro : DihMacroManager.get().getAll()) {
                        names.add((macro.folder == null || macro.folder.isBlank() ? "" : macro.folder + "/") + macro.name
                                + " (" + macro.actions.size() + " steps)");
                    }
                    names.sort(String.CASE_INSENSITIVE_ORDER);
                    return ToolResult.ok(names.isEmpty() ? "No macros saved." : String.join(", ", names)).fact("macros", names.size());
                })
                .build());

        registry.register(AiTool.builder("macro_run", ToolCategory.CLIENT)
                .gameThread()
                .summary("Run a saved macro by name.")
                .schema(ToolSchema.builder()
                        .string("name", "The macro's name.").required()
                        .build())
                .handler((ctx, args) -> {
                    DihMacro macro = DihMacroManager.get().get(args.string("name").trim());
                    if (macro == null) return ToolResult.failed("No macro called \"" + args.string("name") + "\"; macro_list shows them.");
                    macro.execute();
                    return ToolResult.running("macro", "Running macro " + macro.name + ".").fact("macro", macro.name);
                })
                .build());

        registry.register(AiTool.builder("macro_stop", ToolCategory.CLIENT)
                .gameThread()
                .summary("Stop the running macro.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> {
                    DihMacroManager.get().stopMacro();
                    return ToolResult.ok("Stopped the macro.");
                })
                .build());

        registry.register(AiTool.builder("macro_write", ToolCategory.CLIENT)
                .gameThread()
                .summary("Create a macro from a list of steps.")
                .description("Create a macro from steps given as a JSON array, e.g. [{\"type\":\"SEND_CHAT\",\"fields\":"
                        + "{\"message\":\"hi\"}},{\"type\":\"WAIT\",\"fields\":{\"ticks\":20}}]. Each type is a macro step type "
                        + "and each field one of its settings. Never replaces an existing macro unless overwrite is true.")
                .schema(ToolSchema.builder()
                        .string("name", "The new macro's name.").required()
                        .string("steps", "The steps, as a JSON array.").required()
                        .bool("overwrite", "Replace a macro that already has this name.").defaultsTo(false)
                        .string("folder", "A folder to put it in.")
                        .build())
                .handler((ctx, args) -> macroWrite(args))
                .build());

        registry.register(AiTool.builder("server_connect", ToolCategory.CLIENT)
                .dangerous()
                .gameThread()
                .summary("Connect to a server by address (from the menus, not in a world).")
                .schema(ToolSchema.builder()
                        .string("address", "The server address, e.g. play.example.net or 1.2.3.4:25565.").required()
                        .build())
                .handler((ctx, args) -> connect(args.string("address")))
                .build());

        registry.register(AiTool.builder("disconnect", ToolCategory.CLIENT)
                .dangerous()
                .summary("Leave the current server or world.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> CommandTool.client(ctx, "disconnect"))
                .build());

        registry.register(AiTool.builder("friend_add", ToolCategory.CLIENT)
                .gameThread()
                .summary("Add a player to the friends list (never attacked, never fled from).")
                .schema(ToolSchema.builder()
                        .string("player", "The player's name.").required()
                        .build())
                .handler((ctx, args) -> {
                    String name = playerName(args.string("player"));
                    boolean added = TeamsModule.addFriend(name);
                    return ToolResult.ok(added ? "Added " + name + " as a friend." : name + " was already a friend.").fact("player", name);
                })
                .build());

        registry.register(AiTool.builder("friend_remove", ToolCategory.CLIENT)
                .gameThread()
                .summary("Remove a player from the friends list.")
                .schema(ToolSchema.builder()
                        .string("player", "The player's name.").required()
                        .build())
                .handler((ctx, args) -> {
                    String name = playerName(args.string("player"));
                    boolean removed = TeamsModule.removeFriend(name);
                    return removed ? ToolResult.ok("Removed " + name + " from friends.").fact("player", name)
                            : ToolResult.failed(name + " isn't a friend.");
                })
                .build());

        registry.register(AiTool.builder("trade", ToolCategory.JOB)
                .gameThread()
                .summary("Buy villager trades matching a rule, or reroll a villager until one matches.")
                .description("Start AutoTrade. mode trade walks to nearby villagers and buys offers matching the rule; mode "
                        + "reroll rerolls the villager being looked at (one nobody has traded with) until it offers a match, "
                        + "then locks it. Rules: mending, unbreaking3, \"sharpness 4-5\", book, item:bread, slot 2, any.")
                .schema(ToolSchema.builder()
                        .string("rule", "What to buy.").required()
                        .enumOf("mode", "trade or reroll.", "trade", "reroll").defaultsTo("trade")
                        .integer("max_price", "Most emeralds per purchase (default: any).").range(1, 64)
                        .integer("budget", "Most emeralds to spend in total (default: no limit).").range(1, 2304)
                        .string("profession", "Only villagers with this job, e.g. librarian.")
                        .build())
                .handler((ctx, args) -> trade(args))
                .build());

        registry.register(AiTool.builder("read_offers", ToolCategory.TRADING)
                .gameThread()
                .summary("Villager offers seen so far, nearest villager first.")
                .schema(ToolSchema.builder()
                        .string("rule", "Only offers matching this rule, e.g. mending or item:bread (default: all).")
                        .build())
                .handler((ctx, args) -> readOffers(args))
                .build());

        registry.register(AiTool.builder("enchant_plan", ToolCategory.TRADING)
                .gameThread()
                .summary("The cheapest villagers to buy enchanted books from.")
                .schema(ToolSchema.builder()
                        .string("enchantments", "The books wanted, e.g. \"mending unbreaking3 efficiency5\".").required()
                        .build())
                .handler((ctx, args) -> enchantPlan(args.string("enchantments")))
                .build());
    }

    private static ToolResult moduleList(ToolInput args) {
        String category = args.has("category") ? args.string("category").trim().toLowerCase(Locale.ROOT) : "";
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Module module : ModuleRegistry.all()) {
            if (!category.isEmpty() && !module.category().label().toLowerCase(Locale.ROOT).equals(category)) continue;
            (module.isEnabled() ? on : off).add(module.name());
        }
        on.sort(String.CASE_INSENSITIVE_ORDER);
        off.sort(String.CASE_INSENSITIVE_ORDER);
        if (on.isEmpty() && off.isEmpty()) return ToolResult.failed("No modules in \"" + category + "\".");
        return ToolResult.ok("On: " + (on.isEmpty() ? "none" : String.join(", ", on)) + ". Off: " + String.join(", ", off) + ".")
                .fact("on", on).fact("off", off.size());
    }

    private static ToolResult moduleToggle(ToolContext ctx, ToolInput args) {
        Module module = module(args.string("module"));
        if (module == null) return ToolResult.failed("No module called \"" + args.string("module") + "\"; module_list shows them.");
        String state = args.string("state");
        boolean target = switch (state) {
            case "on" -> true;
            case "off" -> false;
            default -> !module.isEnabled();
        };
        // Turning a combat module on attacks things, so it is confirmed first; turning one off never needs to be.
        if (target && module.category() == ModuleCategory.COMBAT && !ctx.isConfirmed()) {
            return ToolResult.failed(module.name() + " is a combat module: turning it on needs confirmation.")
                    .fact("needs_confirmation", true).fact("module", module.name());
        }
        if (module.isEnabled() != target) module.setEnabled(target);
        return ToolResult.ok(module.name() + " is " + (module.isEnabled() ? "on" : "off") + ".")
                .fact("module", module.name()).fact("enabled", module.isEnabled());
    }

    private static ToolResult moduleSetting(ToolInput args) {
        Module module = module(args.string("module"));
        if (module == null) return ToolResult.failed("No module called \"" + args.string("module") + "\"; module_list shows them.");
        if (!args.has("key")) {
            List<String> lines = new ArrayList<>();
            module.settings().forEach(setting -> lines.add(setting.id() + " = " + module.displayValue(setting)));
            return ToolResult.ok(lines.isEmpty() ? module.name() + " has no settings." : module.name() + ": " + String.join(", ", lines))
                    .fact("module", module.name()).fact("settings", lines.size());
        }
        String key = args.string("key").trim();
        if (module.setting(key) == null) {
            List<String> ids = module.settings().stream().map(s -> s.id()).toList();
            return ToolResult.failed(module.name() + " has no setting \"" + key + "\". It has: " + String.join(", ", ids) + ".");
        }
        if (args.has("value")) {
            String value = args.string("value").trim();
            if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) module.resetValue(key);
            else module.setValue(key, value);
        }
        return ToolResult.ok(module.name() + " " + key + " = " + module.value(key) + ".")
                .fact("module", module.name()).fact("key", key).fact("value", module.value(key));
    }

    private static Module module(String name) {
        if (name == null) return null;
        Module module = ModuleRegistry.get(name.trim());
        return module != null ? module : ModuleRegistry.get(name.trim().replace('-', ' '));
    }

    private static ToolResult macroWrite(ToolInput args) {
        String name = args.string("name").trim();
        if (name.isEmpty()) return ToolResult.failed("The macro needs a name.");
        DihMacroManager manager = DihMacroManager.get();
        DihMacro existing = manager.get(name);
        if (existing != null && !args.bool("overwrite")) {
            return ToolResult.failed("A macro called " + name + " already exists; pass overwrite true to replace it.");
        }
        MacroSteps.Built built = MacroSteps.build(name, args.string("steps"));
        if (built.error() != null) return ToolResult.failed(built.error());
        DihMacro macro = built.macro();
        if (args.has("folder")) macro.folder = args.string("folder").trim();
        if (existing != null) manager.remove(existing);
        manager.add(macro);
        manager.save();
        return ToolResult.ok("Saved macro " + name + " with " + macro.actions.size() + " steps: "
                        + String.join(", ", macro.actions.stream().map(a -> a.getDisplayName()).toList()) + ".")
                .fact("macro", name).fact("steps", macro.actions.size());
    }

    private static ToolResult connect(String address) {
        Minecraft mc = Minecraft.getInstance();
        String ip = address == null ? "" : address.trim();
        if (!ip.matches("[A-Za-z0-9.:\\[\\]_-]{1,255}")) return ToolResult.failed("Not a server address: \"" + ip + "\".");
        if (mc.level != null) return ToolResult.failed("Already in a world; disconnect first.");
        ServerData data = new ServerData(ip, ip, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(ip), data, false, null);
        return ToolResult.running("connect", "Connecting to " + ip + ".").fact("address", ip);
    }

    private static String playerName(String text) {
        String name = text == null ? "" : text.trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("\"" + name + "\" isn't a player name.");
        return name;
    }

    private static ToolResult trade(ToolInput args) {
        Module module = ModuleRegistry.get("auto-trade");
        if (module == null) return ToolResult.failed("AutoTrade isn't available.");
        String rule = args.string("rule").trim();
        try {
            TradeRule.parse(rule);
        } catch (IllegalArgumentException e) {
            return ToolResult.failed("Bad rule: " + e.getMessage());
        }
        module.setValue("rule", rule);
        module.setValue("mode", args.string("mode").equals("reroll") ? "Reroll" : "Trade");
        module.setValue("max-price", Integer.toString(args.has("max_price") ? args.integer("max_price") : 0));
        module.setValue("budget", Integer.toString(args.has("budget") ? args.integer("budget") : 0));
        module.setValue("profession", args.has("profession") ? args.string("profession").trim() : "");
        if (module.isEnabled()) module.setEnabled(false);
        module.setEnabled(true);
        return ToolResult.running("trade", "AutoTrade started: " + args.string("mode") + " for " + rule + ".")
                .fact("rule", rule).fact("mode", args.string("mode"));
    }

    private static ToolResult readOffers(ToolInput args) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ToolResult.failed("Not in a world.");
        TradeRule rule;
        try {
            rule = args.has("rule") ? TradeRule.parse(args.string("rule")) : TradeRule.any();
        } catch (IllegalArgumentException e) {
            return ToolResult.failed("Bad rule: " + e.getMessage());
        }
        return describeOffers(TradeCaches.current().all(), rule, player.getX(), player.getY(), player.getZ());
    }

    /** One line per villager with a matching offer, nearest first. */
    static ToolResult describeOffers(List<VillagerOffers> villagers, TradeRule rule, double x, double y, double z) {
        List<VillagerOffers> sorted = new ArrayList<>(villagers);
        sorted.sort(Comparator.comparingDouble(v -> v.distanceSq(x, y, z)));
        List<String> lines = new ArrayList<>();
        for (VillagerOffers villager : sorted) {
            List<String> matching = new ArrayList<>();
            for (TradeOffer offer : villager.offers()) {
                if (!offer.outOfStock() && rule.matches(offer, villager.profession())) matching.add(offer.describe());
            }
            if (matching.isEmpty()) continue;
            String job = villager.profession() == null ? "villager" : villager.profession().replace("minecraft:", "");
            lines.add(job + " at " + villager.x() + " " + villager.y() + " " + villager.z()
                    + (villager.rerollable() ? " (rerollable)" : "") + ": " + String.join(", ", matching));
            if (lines.size() >= 10) break;
        }
        if (lines.isEmpty()) return ToolResult.ok("No remembered offer matches. Offers are learned by opening a villager's trades.")
                .fact("villagers", 0);
        return ToolResult.ok(String.join("\n", lines)).fact("villagers", lines.size());
    }

    private static ToolResult enchantPlan(String enchantments) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ToolResult.failed("Not in a world.");
        List<EnchantPlanner.Want> wants;
        try {
            wants = EnchantPlanner.parseWants(Arrays.asList(enchantments.trim().split("\\s+")));
        } catch (IllegalArgumentException e) {
            return ToolResult.failed(e.getMessage());
        }
        EnchantPlanner.Plan plan = EnchantPlanner.plan(wants, TradeCaches.current().all(), player.getX(), player.getY(), player.getZ());
        return ToolResult.ok(String.join("\n", EnchantPlanner.explain(plan)))
                .fact("stops", plan.stops().size()).fact("missing", plan.missing().size());
    }
}
