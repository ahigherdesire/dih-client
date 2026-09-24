package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class PluginsCommand extends Command {
    public PluginsCommand() { super("plugins", "Open the plugin scanner (alias of `server plugins`)."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> { ServerCommand.openPlugins(); return SUCCESS; });
    }
}
