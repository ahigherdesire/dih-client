package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihCompatManager;
import dihclient.util.DihNotifications;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Change the DIH command prefix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("Current prefix: " + DihCompatManager.effectiveCommandPrefix());
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("reset").executes(ctx -> {
            String prefix = DihCompatManager.environmentDefaultCommandPrefix();
            DihModule.get().setCommandPrefix(prefix);
            DihClientMessaging.sendPrefixed("Prefix reset to " + prefix);
            return SUCCESS;
        }));
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("new", StringArgumentType.word())
            .suggests(CommandSuggest::prefixes)
            .executes(ctx -> {
                String requested = StringArgumentType.getString(ctx, "new");
                if (!DihCompatManager.COMMAND_PREFIX_CHOICES.contains(requested)) {
                    DihClientMessaging.sendPrefixed("Prefix must be one of: . % - _ * # @ & =");
                    return SUCCESS;
                }
                String selected = DihCompatManager.normalizeStoredCommandPrefix(requested);
                DihModule.get().setCommandPrefix(selected);
                if (!selected.equals(requested)) {
                    DihNotifications.warning("Meteor uses '.'. Prefix kept as '%'.");
                }
                DihClientMessaging.sendPrefixed("Prefix set to " + selected);
                return SUCCESS;
            }));
    }
}
