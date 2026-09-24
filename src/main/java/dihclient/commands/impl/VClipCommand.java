package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import dihclient.util.macro.VClipAction;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class VClipCommand extends Command {
    public VClipCommand() {
        super("vclip", "Lets you clip through blocks vertically with movement packets.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("\u00a7eUsage: \u00a7f" + prefix + "vclip <blocks>");
            DihClientMessaging.sendPrefixed("\u00a77Modes: \u00a7fdefault, top, bottom, paper, single, custom");
            DihClientMessaging.sendPrefixed("\u00a77Examples: \u00a7f" + prefix + "vclip top \u00a77or \u00a7f" + prefix + "vclip custom -25 10 20 true true");
            return SUCCESS;
        });

        root.then(blocksArgument("blocks", options -> options));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("top")
            .executes(ctx -> {
                VClipAction.Options options = VClipAction.Options.defaults(0.0);
                options.mode = VClipAction.Mode.TOP;
                run(options);
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("bottom")
            .executes(ctx -> {
                VClipAction.Options options = VClipAction.Options.defaults(0.0);
                options.mode = VClipAction.Mode.BOTTOM;
                run(options);
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("default")
            .then(blocksArgument("blocks", options -> options)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("paper")
            .then(blocksArgument("blocks", options -> options)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("single")
            .then(blocksArgument("blocks", VClipAction.Options::singlePacket)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("normal")
            .then(blocksArgument("blocks", VClipAction.Options::singlePacket)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("custom")
            .then(RequiredArgumentBuilder.<DihCommandSource, Double>argument("blocks", DoubleArgumentType.doubleArg())
                .suggests(CommandSuggest::offsets)
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("segment", IntegerArgumentType.integer(1, 50))
                    .suggests(CommandSuggest::vclipSegments)
                    .executes(ctx -> {
                        VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                        options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                        run(options);
                        return SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("maxPackets", IntegerArgumentType.integer(1, 100))
                        .suggests(CommandSuggest::vclipPacketLimits)
                        .executes(ctx -> {
                            VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
                            options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
                            options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
                            run(options);
                            return SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.<DihCommandSource, Boolean>argument("updateLocal", BoolArgumentType.bool())
                            .executes(ctx -> {
                                VClipAction.Options options = customOptions(ctx);
                                options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                run(options);
                                return SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<DihCommandSource, Boolean>argument("vehicle", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    VClipAction.Options options = customOptions(ctx);
                                    options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                    options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                    run(options);
                                    return SUCCESS;
                                })
                                .then(RequiredArgumentBuilder.<DihCommandSource, Boolean>argument("forceGround", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        VClipAction.Options options = customOptions(ctx);
                                        options.updateLocalPosition = BoolArgumentType.getBool(ctx, "updateLocal");
                                        options.tryVehicleFirst = BoolArgumentType.getBool(ctx, "vehicle");
                                        options.forceGrounded = BoolArgumentType.getBool(ctx, "forceGround");
                                        run(options);
                                        return SUCCESS;
                                    }))))))));
    }

    private interface OptionsCustomizer {
        VClipAction.Options apply(VClipAction.Options options);
    }

    private static RequiredArgumentBuilder<DihCommandSource, Double> blocksArgument(String name, OptionsCustomizer customizer) {
        return RequiredArgumentBuilder.<DihCommandSource, Double>argument(name, DoubleArgumentType.doubleArg())
            .suggests(CommandSuggest::offsets)
            .executes(ctx -> {
                VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, name));
                run(customizer.apply(options));
                return SUCCESS;
            });
    }

    private static VClipAction.Options customOptions(com.mojang.brigadier.context.CommandContext<DihCommandSource> ctx) {
        VClipAction.Options options = VClipAction.Options.defaults(DoubleArgumentType.getDouble(ctx, "blocks"));
        options.segmentBlocks = IntegerArgumentType.getInteger(ctx, "segment");
        options.maxPackets = IntegerArgumentType.getInteger(ctx, "maxPackets");
        return options;
    }

    public static void vclip(double blocks) {
        run(VClipAction.Options.defaults(blocks));
    }

    private static void run(VClipAction.Options options) {
        options.showMessage = true;
        VClipAction.perform(Minecraft.getInstance(), options);
    }
}
