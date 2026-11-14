package dihclient.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihRegionHighlight;
import net.minecraft.client.Minecraft;

public final class ShowRegionCommand extends Command {
    public ShowRegionCommand() {
        super("showregion", "Highlight a cuboid: x y z x1 y1 z1, or clear.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix()
                + "showregion <x> <y> <z> <x1> <y1> <z1> | clear");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear").executes(ctx -> {
            DihRegionHighlight.clear();
            DihClientMessaging.sendPrefixed("§aRegion highlight cleared.");
            return SUCCESS;
        }));

        String[] names = {"x", "y", "z", "x1", "y1", "z1"};
        RequiredArgumentBuilder<DihCommandSource, Integer> coordinates =
            RequiredArgumentBuilder.<DihCommandSource, Integer>argument("z1", IntegerArgumentType.integer())
                .executes(this::show);
        for (int i = names.length - 2; i >= 0; i--) {
            coordinates = RequiredArgumentBuilder.<DihCommandSource, Integer>argument(names[i], IntegerArgumentType.integer())
                .then(coordinates);
        }
        root.then(coordinates);
    }

    private int show(CommandContext<DihCommandSource> ctx) {
        Minecraft mc = ctx.getSource().mc();
        if (mc == null || mc.level == null || mc.player == null) {
            DihClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
        int y1 = IntegerArgumentType.getInteger(ctx, "y1");
        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
        DihRegionHighlight.show(mc.level, x, y, z, x1, y1, z1);
        DihClientMessaging.sendPrefixed("§aHighlighting region §f(" + x + " " + y + " " + z
            + ") §ato §f(" + x1 + " " + y1 + " " + z1 + ")§a.");
        return SUCCESS;
    }
}
