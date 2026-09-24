package com.example.addon.commands;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

// A Brigadier command. Users run it with the command prefix, e.g. .example or .example hi.
public final class ExampleCommand extends Command {
    public ExampleCommand() {
        super("example", "Example addon command.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("\u00a7dExample addon command works!");
            return SUCCESS;
        });

        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("text", StringArgumentType.greedyString())
            .executes(ctx -> {
                DihClientMessaging.sendPrefixed("\u00a7dYou said: \u00a7f" + StringArgumentType.getString(ctx, "text"));
                return SUCCESS;
            }));
    }
}
