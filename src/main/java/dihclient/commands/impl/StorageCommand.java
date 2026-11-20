package dihclient.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.modules.StorageSearchRenderer;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihStorageSearch;

public final class StorageCommand extends Command {
    public StorageCommand() {
        super("storage", "Find remembered items and highlight their containers.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix()
                + "storage search <item name or ID> | clear. Open storage first to remember its contents.");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear").executes(ctx -> {
            StorageSearchRenderer.clear();
            DihClientMessaging.sendPrefixed("§aStorage highlights cleared. Remembered contents are kept.");
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("search")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("query", StringArgumentType.greedyString())
                .executes(ctx -> {
                    var mc = ctx.getSource().mc();
                    if (mc == null || mc.player == null || mc.level == null) {
                        DihClientMessaging.sendPrefixed("§cNot in a world.");
                        return SUCCESS;
                    }
                    String query = StringArgumentType.getString(ctx, "query").strip();
                    if (query.isEmpty()) {
                        DihClientMessaging.sendPrefixed("§eEnter an item name or ID.");
                        return SUCCESS;
                    }
                    var hits = StorageSearchRenderer.search(mc, query);
                    DihClientMessaging.sendPrefixed("§aStorage search: §f" + hits.size() + "§a nearby remembered matches for §f"
                        + query + "§a. Green outlines: up to " + DihStorageSearch.MAX_MARKERS + " containers within "
                        + (int) DihStorageSearch.RADIUS + " blocks, in this dimension.");
                    long now = System.currentTimeMillis();
                    for (var hit : hits.stream().limit(10).toList()) {
                        var p = hit.record.pos;
                        int distance = (int) Math.sqrt(p.distToCenterSqr(mc.player.position()));
                        DihClientMessaging.sendPrefixed("§7" + hit.matchedCount() + " items at §f" + p.getX() + " "
                            + p.getY() + " " + p.getZ() + "§7, " + distance + "m away; last seen "
                            + DihStorageSearch.age(hit.record.timestamp, now) + ".");
                    }
                    DihClientMessaging.sendPrefixed("§7Contents are snapshots from your last visit. Open and close containers to refresh. "
                        + DihCommands.effectivePrefix() + "storage clear hides the outlines.");
                    return SUCCESS;
                })));
    }
}
