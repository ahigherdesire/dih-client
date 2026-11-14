package dihclient.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.gui.screen.DihInvSeeScreen;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** {@code .invsee [player]}: a view-only look at a loaded player's armour and held items; no argument means the one you look at. */
public final class InvSeeCommand extends Command {
    public InvSeeCommand() {
        super("invsee", "View a loaded player's armour and held items, with durability and enchantments. View only.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> openLookedAt());
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("player", StringArgumentType.greedyString())
            .suggests(InvSeeCommand::suggestPlayers)
            .executes(ctx -> openNamed(StringArgumentType.getString(ctx, "player"))));
    }

    private static int openLookedAt() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.crosshairPickEntity;
        if (!(entity instanceof Player) && mc.hitResult instanceof EntityHitResult hit) entity = hit.getEntity();
        if (!(entity instanceof Player player)) {
            DihClientMessaging.sendPrefixed("§cLook at a player or name one: §f.invsee <player>");
            return SUCCESS;
        }
        open(mc, player);
        return SUCCESS;
    }

    private static int openNamed(String requested) {
        Minecraft mc = Minecraft.getInstance();
        Player player = RemoteViewCommand.findLoadedPlayer(mc, requested);
        if (player == null) {
            DihClientMessaging.sendPrefixed("§cPlayer is not loaded: §f" + requested.trim());
            return SUCCESS;
        }
        open(mc, player);
        return SUCCESS;
    }

    private static void open(Minecraft mc, Player player) {
        mc.execute(() -> mc.gui.setScreen(new DihInvSeeScreen(player)));
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<DihCommandSource> ignored,
                                                                  SuggestionsBuilder builder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            String name = player.getName().getString();
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }
}
