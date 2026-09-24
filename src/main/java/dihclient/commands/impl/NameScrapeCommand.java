package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.util.DihNameScrape;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public final class NameScrapeCommand extends Command {
    public NameScrapeCommand() {
        super("namescrape", "Scrape player names to clipboard. [count] limits it; deep = exhaustive; pause/resume/stop.",
            "scrapenames");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> apply(0, false));
        root.then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("count", IntegerArgumentType.integer(1))
            .executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), false)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("deep")
            .executes(ctx -> apply(0, true))
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("count", IntegerArgumentType.integer(1))
                .executes(ctx -> apply(IntegerArgumentType.getInteger(ctx, "count"), true))));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("pause").executes(ctx -> {
            DihNameScrape.pause();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("resume").executes(ctx -> {
            DihNameScrape.resume();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("stop").executes(ctx -> {
            DihNameScrape.stop();
            return SUCCESS;
        }));
    }

    private static int apply(int count, boolean deep) {
        DihNameScrape.start(count, deep);
        return SUCCESS;
    }
}
