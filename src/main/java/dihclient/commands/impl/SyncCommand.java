package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.args.MacroArgumentType;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihLANSync;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public final class SyncCommand extends Command {
    public SyncCommand() {
        super("sync", "Synchronize a chat message, server command, or macro across the LAN session.",
            "lan-sync", "lansync");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(context -> usage());

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("send")
            .executes(context -> {
                DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix()
                    + "sync send <message or /command>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument(
                    "message", StringArgumentType.greedyString())
                .executes(context -> send(StringArgumentType.getString(context, "message")))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("macro")
            .executes(context -> {
                DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix()
                    + "sync macro <name>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument(
                    "name", MacroArgumentType.macroName())
                .executes(context -> macro(MacroArgumentType.get(context, "name")))));
    }

    private static int usage() {
        String prefix = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync send <message or /command>");
        DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "sync macro <name>");
        return SUCCESS;
    }

    private static int send(String message) {
        DihLANSync sync = DihLANSync.getInstance();
        if (!sync.isInSession()) {
            DihClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
            return SUCCESS;
        }
        sync.sendChatMessage(message);
        return SUCCESS;
    }

    private static int macro(String name) {
        DihLANSync sync = DihLANSync.getInstance();
        if (!sync.isInSession()) {
            DihClientMessaging.sendPrefixed("§cJoin a LAN Sync session first.");
            return SUCCESS;
        }
        DihMacro macro = DihMacroManager.get().get(name);
        if (macro == null) {
            DihClientMessaging.sendPrefixed("§cMacro not found: §f" + name);
            return SUCCESS;
        }

        sync.executeMacroSynchronized(macro.name);
        return SUCCESS;
    }
}
