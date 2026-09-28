package dihclient.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.trade.EnchantPlanner;
import dihclient.trade.TradeCaches;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;

import java.util.Arrays;

/**
 * {@code .enchant plan mending unbreaking3}: the cheapest route to those enchanted books across the villagers whose
 * trades you have opened in this world, and a librarian to reroll for any nobody offers.
 */
public class EnchantCommand extends Command {
    public EnchantCommand() {
        super("enchant", "Plan the cheapest villagers to buy enchanted books from. plan <enchantments>, e.g. plan mending unbreaking3.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());
        root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<DihCommandSource>literal("plan")
            .executes(ctx -> usage())
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("enchantments", StringArgumentType.greedyString())
                .executes(ctx -> {
                    plan(StringArgumentType.getString(ctx, "enchantments"));
                    return SUCCESS;
                })));
    }

    private int usage() {
        DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "enchant plan <enchantments>, e.g. mending unbreaking3");
        return SUCCESS;
    }

    private static void plan(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            DihClientMessaging.sendPrefixed("§cJoin a world first.");
            return;
        }
        try {
            var wants = EnchantPlanner.parseWants(Arrays.asList(text.trim().split("\\s+")));
            var villagers = TradeCaches.current().all();
            if (villagers.isEmpty()) {
                DihClientMessaging.sendPrefixed("§7No villager trades seen in this world yet. Open some villagers' trades first.");
                return;
            }
            var plan = EnchantPlanner.plan(wants, villagers, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            DihClientMessaging.sendPrefixed("§eEnchant plan §7(" + villagers.size() + " villagers seen):");
            for (String line : EnchantPlanner.explain(plan)) DihClientMessaging.sendPrefixed("§f" + line);
        } catch (IllegalArgumentException e) {
            DihClientMessaging.sendPrefixed("§c" + e.getMessage() + ".");
        }
    }
}
