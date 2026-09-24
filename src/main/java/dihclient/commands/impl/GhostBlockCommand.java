package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.modules.GhostBlockModule;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class GhostBlockCommand extends Command {
    public GhostBlockCommand() {
        super("ghostblock", "Client-side ghost blocks: place, remove and manage blocks only you can see.", "gb");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("§eUsage: " + prefix + "ghostblock <toggle|reset|creative|block>");
            DihClientMessaging.sendPrefixed("§7" + prefix + "ghostblock toggle §8- §7toggle the module");
            DihClientMessaging.sendPrefixed("§7" + prefix + "ghostblock reset §8- §7remove all ghost blocks");
            DihClientMessaging.sendPrefixed("§7" + prefix + "ghostblock creative [on|off] §8- §7fake creative gamemode");
            DihClientMessaging.sendPrefixed("§7" + prefix + "ghostblock block [id|off] §8- §7arm a block for placement");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("toggle")
            .executes(ctx -> {
                GhostBlockModule module = module();
                if (module == null) return SUCCESS;
                module.toggle();
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("reset")
            .executes(ctx -> {
                GhostBlockModule module = module();
                if (module == null) return SUCCESS;
                int removed = module.restoreAll();
                DihClientMessaging.sendPrefixed(removed == 0
                    ? "§7No ghost blocks to remove."
                    : "§aRemoved §f" + removed + "§a ghost block" + (removed == 1 ? "" : "s")
                        + " and restored the original states.");
                return SUCCESS;
            }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("creative")
            .executes(ctx -> setCreative(null))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("state", StringArgumentType.word())
                .suggests(CommandSuggest::state)
                .executes(ctx -> setCreative(StringArgumentType.getString(ctx, "state")))));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("block")
            .executes(ctx -> {
                GhostBlockModule module = module();
                if (module == null) return SUCCESS;
                Block armed = module.armedBlock();
                DihClientMessaging.sendPrefixed(armed == null
                    ? "§7No ghost block armed."
                    : "§7Armed ghost block: §f" + BuiltInRegistries.BLOCK.getKey(armed));
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("block", StringArgumentType.word())
                .suggests(CommandSuggest::blockIds)
                .executes(ctx -> arm(StringArgumentType.getString(ctx, "block")))));
    }

    private static int setCreative(String state) {
        GhostBlockModule module = module();
        if (module == null) return SUCCESS;
        boolean target;
        if (state == null || "toggle".equalsIgnoreCase(state)) {
            target = !module.isCreative();
        } else if ("on".equalsIgnoreCase(state) || "enable".equalsIgnoreCase(state)) {
            target = true;
        } else if ("off".equalsIgnoreCase(state) || "disable".equalsIgnoreCase(state)) {
            target = false;
        } else {
            DihClientMessaging.sendPrefixed("§cUnknown state: §f" + state);
            return SUCCESS;
        }
        module.setCreative(target);
        if (target && !module.isEnabled()) module.setEnabled(true);
        DihClientMessaging.sendPrefixed(target
            ? "§aFake creative on. §7Whatever you place becomes a ghost block."
            : "§7Fake creative off. §7Back to your real gamemode.");
        return SUCCESS;
    }

    private static int arm(String input) {
        GhostBlockModule module = module();
        if (module == null) return SUCCESS;
        String value = input.toLowerCase(Locale.ROOT);
        if ("off".equals(value) || "none".equals(value) || "reset".equals(value)) {
            module.setArmedBlock(null, false);
            DihClientMessaging.sendPrefixed("§7Ghost block disarmed; held blocks place ghosts again.");
            return SUCCESS;
        }
        Identifier id = Identifier.tryParse(value.contains(":") ? value : "minecraft:" + value);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            DihClientMessaging.sendPrefixed("§cUnknown block: §f" + input);
            return SUCCESS;
        }
        module.setArmedBlock(block, false);
        if (!module.isEnabled()) module.setEnabled(true);
        DihClientMessaging.sendPrefixed("§aGhost block: §f" + BuiltInRegistries.BLOCK.getKey(block)
            + (module.isCreative() ? "" : " §7(switch Mode to Creative to place it)"));
        return SUCCESS;
    }

    private static GhostBlockModule module() {
        Module module = ModuleRegistry.get("ghostblock");
        if (module instanceof GhostBlockModule ghostBlock) return ghostBlock;
        DihClientMessaging.sendPrefixed("§cGhostBlock module is not available.");
        return null;
    }
}
