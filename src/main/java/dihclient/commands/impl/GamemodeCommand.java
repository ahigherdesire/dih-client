package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihFakeGamemode;
import dihclient.util.DihGamemode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.world.level.GameType;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Set your real game mode (no chat command sent).");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix()
                + "gamemode <survival|creative|adventure|spectator>");
            DihClientMessaging.sendPrefixed("§7Client-side only: " + DihCommands.effectivePrefix() + "fakegm");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("mode", StringArgumentType.word())
            .suggests(CommandSuggest::realGamemodes)
            .executes(ctx -> {
                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(java.util.Locale.ROOT);
                GameType resolved = DihFakeGamemode.parseMode(mode);
                if (resolved == null) {
                    DihClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
                    if ("reset".equals(mode) || "r".equals(mode)) {
                        DihClientMessaging.sendPrefixed("§7Reset only applies to the fake mode: §f"
                            + DihCommands.effectivePrefix() + "fakegm reset");
                    }
                    return SUCCESS;
                }
                DihFakeGamemode.Result result = DihGamemode.real(resolved);
                DihClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
                return SUCCESS;
            }));
    }
}
