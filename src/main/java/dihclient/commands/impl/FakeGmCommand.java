package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihFakeGamemode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.world.level.GameType;

public class FakeGmCommand extends Command {
    public FakeGmCommand() {
        super("fakegm", "Set a fake client-side game mode.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix()
                + "fakegm <survival|creative|adventure|spectator|reset>");
            DihClientMessaging.sendPrefixed("§7Real change: " + DihCommands.effectivePrefix() + "gamemode");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("mode", StringArgumentType.word())
            .suggests(CommandSuggest::gamemodes)
            .executes(ctx -> {
                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(java.util.Locale.ROOT);
                DihFakeGamemode.Result result;
                if ("reset".equals(mode) || "r".equals(mode)) {
                    result = DihFakeGamemode.reset();
                } else {
                    GameType resolved = DihFakeGamemode.parseMode(mode);
                    if (resolved == null) {
                        DihClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
                        return SUCCESS;
                    }
                    result = DihFakeGamemode.apply(resolved);
                }
                DihClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
                return SUCCESS;
            }));
    }
}
