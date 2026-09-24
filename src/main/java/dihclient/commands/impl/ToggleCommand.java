package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.CommandSuggest;
import dihclient.commands.args.ModuleArgumentType;
import dihclient.modules.PackHideState;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihClientMessaging;
import dihclient.util.macro.ToggleModuleAction;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class ToggleCommand extends Command {
    public ToggleCommand() { super("toggle", "Toggle a module on/off.", "t"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix() + "toggle <module> [on|off|toggle]");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("module", ModuleArgumentType.moduleName())
            .executes(ctx -> doToggle(ModuleArgumentType.get(ctx, "module"), ToggleModuleAction.ToggleMode.TOGGLE))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("state", StringArgumentType.word())
                .suggests(CommandSuggest::state)
                .executes(ctx -> {
                    String state = StringArgumentType.getString(ctx, "state").toLowerCase();
                    if (!state.equals("on") && !state.equals("enable") && !state.equals("true") && !state.equals("1")
                            && !state.equals("off") && !state.equals("disable") && !state.equals("false") && !state.equals("0")
                            && !state.equals("toggle")) {
                        DihClientMessaging.sendPrefixed("§cUnknown toggle state: §f" + state);
                        DihClientMessaging.sendPrefixed("§7Use §fon§7, §foff§7, or §ftoggle§7.");
                        return SUCCESS;
                    }
                    ToggleModuleAction.ToggleMode mode = switch (state) {
                        case "on", "enable", "true", "1" -> ToggleModuleAction.ToggleMode.ENABLE;
                        case "off", "disable", "false", "0" -> ToggleModuleAction.ToggleMode.DISABLE;
                        default -> ToggleModuleAction.ToggleMode.TOGGLE;
                    };
                    return doToggle(ModuleArgumentType.get(ctx, "module"), mode);
                })));
    }

    private static int doToggle(String moduleName, ToggleModuleAction.ToggleMode mode) {
        boolean ok = ModuleRegistry.toggle(moduleName, mode);
        if (ok && PackHideState.isHideModuleName(moduleName)) return SUCCESS;
        if (ok) DihClientMessaging.sendPrefixed("§a" + mode.name().toLowerCase() + ": §f" + moduleName);
        else    DihClientMessaging.sendPrefixed("§cModule not found: §f" + moduleName);
        return SUCCESS;
    }
}
