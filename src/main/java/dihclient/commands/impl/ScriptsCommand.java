package dihclient.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.gui.screen.DihOverlayHostScreen;
import dihclient.script.DihScriptsOverlay;
import dihclient.script.ScriptManager;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihOverlayManager;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code .scripts} — run, stop, check and manage DihScript files.
 *
 * <pre>
 * .scripts run &lt;name&gt;      start a script
 * .scripts stop [name]     stop one script, or all of them
 * .scripts list            every script, with what's running
 * .scripts check &lt;name&gt;    syntax-check without running
 * .scripts new &lt;name&gt;      create an empty script
 * .scripts delete &lt;name&gt;   delete a script
 * .scripts edit [name]     open the Scripts editor
 * .scripts functions [category]  list built-in functions
 * .scripts folder          print where scripts are stored
 * </pre>
 */
public final class ScriptsCommand extends Command {
    public ScriptsCommand() {
        super("scripts", "Write, run and stop DihScript automation scripts.", "script");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(literal("run").then(name(ScriptsCommand::allNames).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            String error = ScriptManager.run(name);
            if (error != null) DihClientMessaging.sendPrefixed("§c" + error);
            return SUCCESS;
        })));

        root.then(literal("stop")
            .executes(ctx -> {
                int n = ScriptManager.stopAll();
                DihClientMessaging.sendPrefixed(n == 0 ? "§7No scripts are running." : "§eStopping " + n + " script(s).");
                return SUCCESS;
            })
            .then(name(ScriptsCommand::runningNames).executes(ctx -> {
                String name = StringArgumentType.getString(ctx, "name");
                if ("all".equalsIgnoreCase(name)) {
                    int n = ScriptManager.stopAll();
                    DihClientMessaging.sendPrefixed(n == 0 ? "§7No scripts are running." : "§eStopping " + n + " script(s).");
                } else if (!ScriptManager.stop(name)) {
                    DihClientMessaging.sendPrefixed("§c'" + name + "' is not running.");
                }
                return SUCCESS;
            })));

        root.then(literal("list").executes(ctx -> list()));

        root.then(literal("check").then(name(ScriptsCommand::allNames).executes(ctx -> check(StringArgumentType.getString(ctx, "name")))));

        root.then(literal("new").then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", StringArgumentType.word())
            .executes(ctx -> create(StringArgumentType.getString(ctx, "name")))));

        root.then(literal("delete").then(name(ScriptsCommand::allNames).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            try {
                DihClientMessaging.sendPrefixed(ScriptManager.delete(name) ? "§eDeleted §f" + name : "§cNo script named §f" + name);
            } catch (IOException e) {
                DihClientMessaging.sendPrefixed("§cCould not delete: " + e.getMessage());
            }
            return SUCCESS;
        })));

        root.then(literal("edit")
            .executes(ctx -> openEditor(null))
            .then(name(ScriptsCommand::allNames).executes(ctx -> openEditor(StringArgumentType.getString(ctx, "name")))));
        root.then(literal("gui").executes(ctx -> openEditor(null)));

        root.then(literal("functions")
            .executes(ctx -> functions(null))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("category", StringArgumentType.word())
                .suggests((ctx, b) -> suggest(b, ScriptManager.library().categories()))
                .executes(ctx -> functions(StringArgumentType.getString(ctx, "category")))));

        root.then(literal("folder").executes(ctx -> {
            DihClientMessaging.sendPrefixed("§7Scripts folder: §f" + ScriptManager.directory().getAbsolutePath());
            return SUCCESS;
        }));
    }

    private static LiteralArgumentBuilder<DihCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private interface NameSource {
        List<String> names();
    }

    private static RequiredArgumentBuilder<DihCommandSource, String> name(NameSource source) {
        return RequiredArgumentBuilder.<DihCommandSource, String>argument("name", StringArgumentType.word())
            .suggests((ctx, builder) -> suggest(builder, source.names()));
    }

    private static List<String> allNames() {
        return ScriptManager.names();
    }

    private static List<String> runningNames() {
        List<String> names = new java.util.ArrayList<>(ScriptManager.runningNames());
        if (!names.isEmpty()) names.add("all");
        return names;
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, List<String> values) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String v : values) if (v.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(v);
        return builder.buildFuture();
    }

    private static int usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eScripts §7— automation you write yourself.");
        DihClientMessaging.sendPrefixed("§f" + p + "scripts run <name> §7| §f" + p + "scripts stop [name] §7| §f" + p + "scripts list");
        DihClientMessaging.sendPrefixed("§f" + p + "scripts edit [name] §7| §fcheck <name> §7| §fnew <name> §7| §fdelete <name>");
        DihClientMessaging.sendPrefixed("§f" + p + "scripts functions [category] §7| §ffolder");
        return SUCCESS;
    }

    private static int list() {
        List<String> names = ScriptManager.names();
        if (names.isEmpty()) {
            DihClientMessaging.sendPrefixed("§7No scripts yet. Create one with §f" + DihCommands.effectivePrefix() + "scripts new <name>");
            return SUCCESS;
        }
        DihClientMessaging.sendPrefixed("§eScripts (" + names.size() + "):");
        for (String n : names) {
            String status = ScriptManager.status(n);
            DihClientMessaging.sendPrefixed((status != null ? "§a● §f" : "§8○ §7") + n + (status != null ? " §7" + status : ""));
        }
        return SUCCESS;
    }

    private static int check(String name) {
        String source;
        try {
            source = ScriptManager.read(name);
        } catch (IOException e) {
            DihClientMessaging.sendPrefixed("§c" + e.getMessage());
            return SUCCESS;
        }
        List<ScriptException> problems = ScriptManager.check(source);
        if (problems.isEmpty()) {
            DihClientMessaging.sendPrefixed("§a" + ScriptManager.resolve(name) + " looks good.");
            return SUCCESS;
        }
        DihClientMessaging.sendPrefixed("§c" + ScriptManager.resolve(name) + ": " + problems.size() + " problem(s)");
        for (int i = 0; i < Math.min(8, problems.size()); i++) DihClientMessaging.sendPrefixed("§7  " + problems.get(i).describe());
        return SUCCESS;
    }

    private static int create(String name) {
        if (!ScriptManager.isValidName(name)) {
            DihClientMessaging.sendPrefixed("§cUse letters, digits, _ or - (max 32 characters).");
            return SUCCESS;
        }
        if (ScriptManager.exists(name)) {
            DihClientMessaging.sendPrefixed("§c'" + name + "' already exists.");
            return SUCCESS;
        }
        try {
            ScriptManager.write(name, DihScriptsOverlay.TEMPLATE);
            DihClientMessaging.sendPrefixed("§aCreated §f" + name + "§a. Edit it with §f" + DihCommands.effectivePrefix() + "scripts edit " + name);
        } catch (IOException e) {
            DihClientMessaging.sendPrefixed("§cCould not create: " + e.getMessage());
        }
        return SUCCESS;
    }

    private static int functions(String category) {
        Library lib = ScriptManager.library();
        if (category == null) {
            DihClientMessaging.sendPrefixed("§eBuilt-in function categories: §f" + String.join(", ", lib.categories()));
            DihClientMessaging.sendPrefixed("§7Show one with §f" + DihCommands.effectivePrefix() + "scripts functions <category>");
            return SUCCESS;
        }
        List<Library.Entry> entries = lib.byCategory(category);
        if (entries.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cNo category '" + category + "'. Try: " + String.join(", ", lib.categories()));
            return SUCCESS;
        }
        for (Library.Entry e : entries) DihClientMessaging.sendPrefixed("§b" + e.signature() + " §7" + e.description());
        return SUCCESS;
    }

    private static int openEditor(String name) {
        if (name != null && !ScriptManager.exists(name)) {
            DihClientMessaging.sendPrefixed("§cNo script named §f" + name);
            return SUCCESS;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DihScriptsOverlay overlay = DihScriptsOverlay.shared();
            DihOverlayManager.get().register(overlay);
            if (name != null) overlay.select(ScriptManager.resolve(name));
            overlay.setVisible(true);
            DihOverlayManager.get().bringToFront(overlay);
            if (mc.gui.screen() == null) mc.gui.setScreen(new DihOverlayHostScreen(overlay));
        });
        return SUCCESS;
    }
}
