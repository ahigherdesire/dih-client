package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.args.MacroArgumentType;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.macro.MacroAction;
import dihclient.util.macro.MacroActionType;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.multi.MultiManager;
import dihclient.util.multi.MultiTakeoverState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

public class MacroCommand extends Command {
    private static final ConcurrentHashMap<String, AtomicBoolean> RUNNING_LOOPS = new ConcurrentHashMap<>();

    public MacroCommand() { super("macro", "Run a macro, optionally N times with a delay between starts."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "macro <name> [times] [delayTicks]");
            DihClientMessaging.sendPrefixed("§7Run/stop: §f" + prefix + "macro stop [name] §7| §f" + prefix + "macro clear");
            DihClientMessaging.sendPrefixed("§7Author: §f" + prefix + "macro new <name> §7| §fadd <name> <TYPE> [k=v…] §7| §fshow <name> §7| §fremoveaction <name> <i> §7| §fdelete <name>");
            return SUCCESS;
        });

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("stop")
            .executes(ctx -> {
                String pov = MultiTakeoverState.activeAccountId();
                if (pov != null) {
                    stopPovLoops(pov);
                    MultiManager mgr = MultiManager.getIfInitialized();
                    MultiManager.BroadcastResult result = mgr == null
                        ? new MultiManager.BroadcastResult(0, 1, 0, java.util.List.of("Multi unavailable"))
                        : mgr.stopMacroOnScope(Set.of(pov));
                    DihClientMessaging.sendPrefixed("§ePOV macro stop: §f" + result.summary());
                    return SUCCESS;
                }
                stopAllLoops();
                DihMacroManager.get().stopMacro();
                DihClientMessaging.sendPrefixed("§eStopped all macros.");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
                .executes(ctx -> {
                    String name = MacroArgumentType.get(ctx, "name");
                    String pov = MultiTakeoverState.activeAccountId();
                    if (pov != null) {
                        AtomicBoolean flag = RUNNING_LOOPS.remove(povLoopKey(pov, name));
                        if (flag != null) flag.set(false);
                        MultiManager mgr = MultiManager.getIfInitialized();
                        MultiManager.BroadcastResult result = mgr == null
                            ? new MultiManager.BroadcastResult(0, 1, 0, java.util.List.of("Multi unavailable"))
                            : mgr.stopMacroOnScope(Set.of(pov));
                        DihClientMessaging.sendPrefixed("§eStopped POV macro §f" + name + "§e: " + result.summary());
                        return SUCCESS;
                    }
                    AtomicBoolean flag = RUNNING_LOOPS.remove(name.toLowerCase());
                    if (flag != null) flag.set(false);
                    MacroExecutor.stopMacro(name);
                    DihClientMessaging.sendPrefixed("§eStopped: §f" + name);
                    return SUCCESS;
                })));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear")
            .executes(ctx -> {
                String pov = MultiTakeoverState.activeAccountId();
                if (pov != null) {
                    stopPovLoops(pov);
                    DihClientMessaging.sendPrefixed("§eCleared POV macro loops for §f" + pov + "§e.");
                } else {
                    stopAllLoops();
                    DihClientMessaging.sendPrefixed("§eCleared macro loops.");
                }
                return SUCCESS;
            }));

        // ---- authoring subcommands: build/edit macros from chat ----
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("new")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", StringArgumentType.word())
                .executes(ctx -> newMacro(StringArgumentType.getString(ctx, "name")))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("delete")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
                .executes(ctx -> deleteMacro(MacroArgumentType.get(ctx, "name")))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("show")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
                .executes(ctx -> showMacro(MacroArgumentType.get(ctx, "name")))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("add")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
                .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("type", StringArgumentType.word())
                    .suggests(MacroCommand::suggestActionTypes)
                    .executes(ctx -> addAction(MacroArgumentType.get(ctx, "name"), StringArgumentType.getString(ctx, "type"), null))
                    .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> addAction(MacroArgumentType.get(ctx, "name"),
                            StringArgumentType.getString(ctx, "type"),
                            StringArgumentType.getString(ctx, "args")))))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("removeaction")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("index", IntegerArgumentType.integer(0))
                    .executes(ctx -> removeAction(MacroArgumentType.get(ctx, "name"), IntegerArgumentType.getInteger(ctx, "index"))))));

        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("name", MacroArgumentType.macroName())
            .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), 1, 0))
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("times", IntegerArgumentType.integer(1, 100_000))
                .executes(ctx -> startLoop(MacroArgumentType.get(ctx, "name"), IntegerArgumentType.getInteger(ctx, "times"), 0))
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("delayTicks", IntegerArgumentType.integer(0, 20 * 60 * 60))
                    .executes(ctx -> startLoop(
                        MacroArgumentType.get(ctx, "name"),
                        IntegerArgumentType.getInteger(ctx, "times"),
                        IntegerArgumentType.getInteger(ctx, "delayTicks"))))));
    }

    // ---------------- authoring helpers ----------------

    private static int newMacro(String name) {
        DihMacroManager mgr = DihMacroManager.get();
        if (mgr.get(name) != null) {
            DihClientMessaging.sendPrefixed("§cMacro already exists: §f" + name);
            return SUCCESS;
        }
        mgr.add(new DihMacro(name));
        DihClientMessaging.sendPrefixed("§aCreated macro §f" + name + "§7. Add steps with §f"
            + DihCommands.effectivePrefix() + "macro add " + name + " <TYPE>");
        return SUCCESS;
    }

    private static int deleteMacro(String name) {
        DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) { DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name); return SUCCESS; }
        DihMacroManager.get().delete(macro); // prints its own confirmation
        return SUCCESS;
    }

    private static int showMacro(String name) {
        DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) { DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name); return SUCCESS; }
        if (macro.actions.isEmpty()) {
            DihClientMessaging.sendPrefixed("§7Macro §f" + name + " §7has no steps yet.");
            return SUCCESS;
        }
        DihClientMessaging.sendPrefixed("§7Macro §f" + name + " §7(" + macro.actions.size() + " steps):");
        for (int i = 0; i < macro.actions.size(); i++) {
            DihClientMessaging.sendPrefixed("§7  " + i + ": §f" + macro.actions.get(i).getDisplayName());
        }
        return SUCCESS;
    }

    private static int addAction(String name, String typeName, String kvPairs) {
        DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) { DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name); return SUCCESS; }

        MacroActionType type;
        try {
            type = MacroActionType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            DihClientMessaging.sendPrefixed("§cUnknown action type: §f" + typeName);
            DihClientMessaging.sendPrefixed("§7Tab-complete the type, or see §f" + DihCommands.effectivePrefix() + "macro add " + name + " ");
            return SUCCESS;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        MacroAction action = DihMacro.createActionFromTag(tag);
        if (action == null) {
            DihClientMessaging.sendPrefixed("§cCould not create a §f" + type.name() + " §caction.");
            return SUCCESS;
        }

        String err = applyKvPairs(action, kvPairs);
        if (err != null) { DihClientMessaging.sendPrefixed("§c" + err); return SUCCESS; }

        macro.actions.add(action);
        DihMacroManager.get().save();
        DihClientMessaging.sendPrefixed("§aAdded §f" + action.getDisplayName() + " §7-> §f" + name
            + " §7(step " + (macro.actions.size() - 1) + ")");
        return SUCCESS;
    }

    private static int removeAction(String name, int index) {
        DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) { DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name); return SUCCESS; }
        if (index < 0 || index >= macro.actions.size()) {
            DihClientMessaging.sendPrefixed("§cNo step " + index + " in §f" + name + " §7(0.." + (macro.actions.size() - 1) + ")");
            return SUCCESS;
        }
        MacroAction removed = macro.actions.remove(index);
        DihMacroManager.get().save();
        DihClientMessaging.sendPrefixed("§eRemoved step " + index + " (§f" + removed.getDisplayName() + "§e) from §f" + name);
        return SUCCESS;
    }

    /** Applies space-separated key=value pairs to an action's public fields. Returns an error string, or null on success. */
    private static String applyKvPairs(MacroAction action, String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (String pair : raw.trim().split("\\s+")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) return "Bad argument (expected key=value): " + pair;
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1);
            try {
                Field field = action.getClass().getField(key); // public fields only
                Class<?> ft = field.getType();
                Object parsed;
                if (ft.isEnum()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object e = Enum.valueOf((Class<Enum>) (Class<?>) ft, val.toUpperCase(Locale.ROOT));
                    parsed = e;
                } else if (ft == boolean.class || ft == Boolean.class) {
                    parsed = Boolean.parseBoolean(val);
                } else if (ft == int.class || ft == Integer.class) {
                    parsed = Integer.parseInt(val);
                } else if (ft == long.class || ft == Long.class) {
                    parsed = Long.parseLong(val);
                } else if (ft == double.class || ft == Double.class) {
                    parsed = Double.parseDouble(val);
                } else if (ft == float.class || ft == Float.class) {
                    parsed = Float.parseFloat(val);
                } else if (ft == String.class) {
                    parsed = val;
                } else {
                    return "Field '" + key + "' has unsupported type " + ft.getSimpleName();
                }
                field.set(action, parsed);
            } catch (NoSuchFieldException nsf) {
                return "No such field '" + key + "' on " + action.getType().name();
            } catch (IllegalArgumentException iae) {
                return "Bad value for '" + key + "': " + val;
            } catch (Exception ex) {
                return "Failed to set '" + key + "': " + ex.getMessage();
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestActionTypes(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (MacroActionType type : MacroActionType.values()) {
            if (type.name().startsWith(remaining)) builder.suggest(type.name());
        }
        return builder.buildFuture();
    }

    private static int startLoop(String name, int times, int delayTicks) {
        dihclient.util.DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) {
            DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return SUCCESS;
        }
        String pov = MultiTakeoverState.activeAccountId();
        if (pov != null) return startPovLoop(name, macro, times, delayTicks, pov);
        if (times <= 1 && delayTicks <= 0) {
            DihMacroManager.get().executeMacro(name);
            return SUCCESS;
        }
        AtomicBoolean flag = new AtomicBoolean(true);
        RUNNING_LOOPS.put(name.toLowerCase(), flag);
        long sleepMs = Math.max(0L, delayTicks * 50L);
        final int totalTimes = Math.max(1, times);
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < totalTimes && flag.get(); i++) {
                    DihMacroManager.get().executeMacro(name);

                    long start = System.nanoTime();
                    long waitTimeoutNanos = 24L * 60L * 60L * 1_000_000_000L;
                    while (MacroExecutor.isMacroRunning(name) && flag.get()
                        && System.nanoTime() - start < waitTimeoutNanos) {
                        Thread.sleep(50);
                    }
                    if (!flag.get()) break;
                    if (i + 1 < totalTimes && sleepMs > 0) Thread.sleep(sleepMs);
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { RUNNING_LOOPS.remove(name.toLowerCase(), flag); }
        }, "MacroLoop-" + name);
        t.setDaemon(true);
        t.start();
        DihClientMessaging.sendPrefixed("§aQueued " + totalTimes + "x §f" + name + "§a (delay " + delayTicks + "t)");
        return SUCCESS;
    }

    private static int startPovLoop(String name, dihclient.util.DihMacro macro, int times,
                                    int delayTicks, String accountId) {
        MultiManager mgr = MultiManager.getIfInitialized();
        if (mgr == null) {
            DihClientMessaging.sendPrefixed("§cMulti is unavailable.");
            return SUCCESS;
        }
        Set<String> scope = Set.of(accountId);
        if (times <= 1 && delayTicks <= 0) {
            MultiManager.BroadcastResult result = mgr.runMacroDirect(macro, scope);
            DihClientMessaging.sendPrefixed((result.sent() > 0 ? "§a" : "§c")
                + "POV macro §f" + name + "§a: " + result.summary());
            return SUCCESS;
        }

        String key = povLoopKey(accountId, name);
        AtomicBoolean flag = new AtomicBoolean(true);
        AtomicBoolean previous = RUNNING_LOOPS.put(key, flag);
        if (previous != null) previous.set(false);
        long sleepMs = Math.max(0L, delayTicks * 50L);
        int totalTimes = Math.max(1, times);
        Thread thread = new Thread(() -> {
            try {
                for (int i = 0; i < totalTimes && flag.get(); i++) {
                    mgr.runMacroDirect(macro, scope);
                    long start = System.nanoTime();
                    long timeout = 24L * 60L * 60L * 1_000_000_000L;
                    while (mgr.isMacroPlayingOnScope(name, scope) && flag.get()
                        && System.nanoTime() - start < timeout) {
                        Thread.sleep(50L);
                    }
                    if (!flag.get()) break;
                    if (i + 1 < totalTimes && sleepMs > 0L) Thread.sleep(sleepMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                RUNNING_LOOPS.remove(key, flag);
            }
        }, "PovMacroLoop-" + name);
        thread.setDaemon(true);
        thread.start();
        DihClientMessaging.sendPrefixed("§aQueued POV §f" + totalTimes + "x " + name
            + "§a for §f" + accountId + "§a.");
        return SUCCESS;
    }

    private static String povLoopKey(String accountId, String name) {
        return "pov:" + accountId.toLowerCase(java.util.Locale.ROOT) + ":" + name.toLowerCase(java.util.Locale.ROOT);
    }

    private static void stopPovLoops(String accountId) {
        String prefix = "pov:" + accountId.toLowerCase(java.util.Locale.ROOT) + ":";
        RUNNING_LOOPS.forEach((key, flag) -> {
            if (key.startsWith(prefix) && RUNNING_LOOPS.remove(key, flag)) flag.set(false);
        });
    }

    private static void stopAllLoops() {
        for (AtomicBoolean flag : RUNNING_LOOPS.values()) flag.set(false);
        RUNNING_LOOPS.clear();
    }
}
