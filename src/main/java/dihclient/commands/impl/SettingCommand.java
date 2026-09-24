package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.commands.DihCommands;
import dihclient.commands.args.ModuleArgumentType;
import dihclient.api.module.Setting;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Read and write any module setting from chat, so every module is fully
 * configurable without opening the ClickGUI.
 *
 * <ul>
 *   <li>{@code setting <module>} - list the module's settings and current values</li>
 *   <li>{@code setting <module> <key>} - print one setting's value</li>
 *   <li>{@code setting <module> <key> <value...>} - set it (use {@code reset} to restore default)</li>
 * </ul>
 */
public class SettingCommand extends Command {
    public SettingCommand() {
        super("setting", "Get or set any module setting from chat.", "set");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("module", ModuleArgumentType.moduleName())
            .executes(ctx -> list(ModuleArgumentType.get(ctx, "module")))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("key", StringArgumentType.word())
                .suggests((c, b) -> suggestKeys(c.getArgument("module", String.class), b))
                .executes(ctx -> get(ModuleArgumentType.get(ctx, "module"), StringArgumentType.getString(ctx, "key")))
                .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("value", StringArgumentType.greedyString())
                    .executes(ctx -> set(
                        ModuleArgumentType.get(ctx, "module"),
                        StringArgumentType.getString(ctx, "key"),
                        StringArgumentType.getString(ctx, "value"))))));
    }

    private static int list(String moduleName) {
        Module module = resolve(moduleName);
        if (module == null) return notFound(moduleName);
        List<Setting<?, ?>> settings = module.settings();
        if (settings.isEmpty()) {
            DihClientMessaging.sendPrefixed("§7" + module.name() + " has no settings.");
            return SUCCESS;
        }
        DihClientMessaging.sendPrefixed("§7Settings for §f" + module.name() + "§7:");
        for (Setting<?, ?> setting : settings) {
            DihClientMessaging.sendPrefixed("§7- §f" + setting.id() + " §7= §b" + module.displayValue(setting));
        }
        return SUCCESS;
    }

    private static int get(String moduleName, String key) {
        Module module = resolve(moduleName);
        if (module == null) return notFound(moduleName);
        if (module.setting(key) == null) return noKey(module, key);
        DihClientMessaging.sendPrefixed("§f" + module.name() + " §7" + key + " §7= §b" + module.value(key));
        return SUCCESS;
    }

    private static int set(String moduleName, String key, String value) {
        Module module = resolve(moduleName);
        if (module == null) return notFound(moduleName);
        if (module.setting(key) == null) return noKey(module, key);

        if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
            module.resetValue(key);
        } else {
            module.setValue(key, value);
        }
        DihClientMessaging.sendPrefixed("§a" + module.name() + " §7" + key + " §7-> §b" + module.value(key));
        return SUCCESS;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKeys(
            String moduleName, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Module module = resolve(moduleName);
        if (module != null) {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Setting<?, ?> setting : module.settings()) {
                if (setting.id().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(setting.id());
            }
        }
        return builder.buildFuture();
    }

    private static Module resolve(String moduleName) {
        if (moduleName == null) return null;
        Module module = ModuleRegistry.get(moduleName);
        if (module == null) module = ModuleRegistry.get(moduleName.replace('-', ' '));
        return module;
    }

    private static int notFound(String moduleName) {
        DihClientMessaging.sendPrefixed("§cModule not found: §f" + moduleName);
        return SUCCESS;
    }

    private static int noKey(Module module, String key) {
        DihClientMessaging.sendPrefixed("§c" + module.name() + " has no setting §f" + key);
        DihClientMessaging.sendPrefixed("§7List them with §f" + DihCommands.effectivePrefix() + "setting " + module.name().replace(' ', '-'));
        return SUCCESS;
    }

    private static int usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage:");
        DihClientMessaging.sendPrefixed("§f" + p + "setting <module> §7- list settings + values");
        DihClientMessaging.sendPrefixed("§f" + p + "setting <module> <key> §7- show one value");
        DihClientMessaging.sendPrefixed("§f" + p + "setting <module> <key> <value> §7- set it (value 'reset' = default)");
        return SUCCESS;
    }
}
