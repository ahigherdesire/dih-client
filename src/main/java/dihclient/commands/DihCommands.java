package dihclient.commands;

import dihclient.DihClientAddon;
import dihclient.api.AddonRegistrationResult;
import dihclient.modules.PackHideState;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihCompatManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class DihCommands {
    private static final CommandDispatcher<DihCommandSource> DISPATCHER = new CommandDispatcher<>();
    private static final List<Command> ALL = new ArrayList<>();
    private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, String> ADDON_COMMAND_OWNERS = new LinkedHashMap<>();
    private static final Map<Command, String> ADDON_COMMAND_OBJECT_OWNERS = new IdentityHashMap<>();
    private static final Set<String> DISABLED_ADDON_COMMAND_NAMES = new HashSet<>();
    private static final List<String> PANIC_PREFIX_FALLBACKS = List.of(".", ",", ";", ":", "'", "\"", "\\", "|", "-", "_", "+", "=", "*", "#", "@", "!", "$", "%", "&", "~");
    private static boolean initialized = false;
    private static int revision;

    private DihCommands() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        register(new dihclient.commands.impl.MacroCommand());
        register(new dihclient.commands.impl.SyncCommand());
        register(new dihclient.commands.impl.DelayCommand());
        register(new dihclient.commands.impl.SendCommand());
        register(new dihclient.commands.impl.VClipCommand());
        register(new dihclient.commands.impl.HClipCommand());
        register(new dihclient.commands.impl.TpCommand());
        register(new dihclient.commands.impl.CopyPosCommand());
        register(new dihclient.commands.impl.NbtCommand());
        register(new dihclient.commands.impl.ServerCommand());
        register(new dihclient.commands.impl.PluginsCommand());
        register(new dihclient.commands.impl.PrefixCommand());
        register(new dihclient.commands.impl.CommandsCommand());
        register(new dihclient.commands.impl.HelpCommand());
        register(new dihclient.commands.impl.BindsCommand());
        register(new dihclient.commands.impl.BindCommand());
        register(new dihclient.commands.impl.ClearCommand());
        register(new dihclient.commands.impl.DismountCommand());
        register(new dihclient.commands.impl.DisconnectCommand());
        register(new dihclient.commands.impl.SayCommand());
        register(new dihclient.commands.impl.DropCommand());
        register(new dihclient.commands.impl.ChangeSlotCommand());
        register(new dihclient.commands.impl.ClickSlotCommand());
        register(new dihclient.commands.impl.ClickItemCommand());
        register(new dihclient.commands.impl.GiveCommand());
        register(new dihclient.commands.impl.DamageCommand());
        register(new dihclient.commands.impl.PacketCommand());
        register(new dihclient.commands.impl.GuiCommand());
        register(new dihclient.commands.impl.GateCommand());
        register(new dihclient.commands.impl.UpdateCommand());

        if (!dihclient.util.DihLiteVariant.enabled()) {
            register(new dihclient.commands.impl.SettingCommand());
            register(new dihclient.commands.impl.IrcCommand());
            register(new dihclient.commands.impl.MatchmakingCommand());
            register(new dihclient.commands.impl.MultiCommand());
            register(new dihclient.commands.impl.ToggleCommand());
            register(new dihclient.commands.impl.ModulesCommand());
            register(new dihclient.commands.impl.OreSimCommand());
            register(new dihclient.commands.impl.GhostBlockCommand());
            register(new dihclient.commands.impl.FriendCommand());
            register(new dihclient.commands.impl.WaypointsCommand());
            register(new dihclient.commands.impl.GamemodeCommand());
            register(new dihclient.commands.impl.FakeGmCommand());
            register(new dihclient.commands.impl.XCarryCommand());
            register(new dihclient.commands.impl.RemoteViewCommand());
            register(new dihclient.commands.impl.NameScrapeCommand());
        }
    }

    private static void register(Command command) {
        registerWithName(command, command.name(), true);
    }

    private static List<String> registerWithName(Command command, String primaryName, boolean registerAliases) {
        List<String> registeredNames = new ArrayList<>();
        revision++;
        ALL.add(command);
        LiteralArgumentBuilder<DihCommandSource> primary = LiteralArgumentBuilder.literal(primaryName);
        command.build(primary);
        DISPATCHER.register(primary);
        BY_NAME.put(primaryName.toLowerCase(Locale.ROOT), command);
        DISABLED_ADDON_COMMAND_NAMES.remove(primaryName.toLowerCase(Locale.ROOT));
        registeredNames.add(primaryName.toLowerCase(Locale.ROOT));

        if (!registerAliases) return registeredNames;
        for (String alias : command.aliases()) {
            if (alias == null || alias.isBlank()) continue;
            if (BY_NAME.containsKey(alias.toLowerCase(Locale.ROOT))) continue;
            LiteralArgumentBuilder<DihCommandSource> aliasBuilder = LiteralArgumentBuilder.literal(alias);
            command.build(aliasBuilder);
            DISPATCHER.register(aliasBuilder);
            BY_NAME.put(alias.toLowerCase(Locale.ROOT), command);
            DISABLED_ADDON_COMMAND_NAMES.remove(alias.toLowerCase(Locale.ROOT));
            registeredNames.add(alias.toLowerCase(Locale.ROOT));
        }
        return registeredNames;
    }

    public static synchronized void registerAddonCommand(Command command, String addonId) {
        registerAddonCommandDetailed(command, addonId);
    }

    public static synchronized AddonRegistrationResult registerAddonCommandDetailed(Command command, String addonId) {
        if (command == null) return AddonRegistrationResult.rejected("command", "", "command was null");
        if (addonId == null || addonId.isBlank()) {
            return rejectAddonCommand(addonId, "", "registration outside an addon lifecycle - register from onInitialize() or onRegisterCategories()");
        }
        String name = command.name();
        if (name == null || name.isBlank()) {
            return rejectAddonCommand(addonId, "", "blank command name");
        }
        List<String> registered;
        boolean collision = BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
        if (collision) {
            String namespaced = (addonId == null || addonId.isBlank() ? "addon" : addonId) + ":" + name;
            DihClientAddon.LOG.warn("[Commands] Command name '{}' from addon '{}' collides with an existing "
                    + "command; registering it as '{}' instead", name, addonId, namespaced);
            registered = registerWithName(command, namespaced, false);
        } else {
            registered = registerWithName(command, name, true);
        }
        ADDON_COMMAND_OBJECT_OWNERS.put(command, addonId);
        for (String registeredName : registered) {
            ADDON_COMMAND_OWNERS.put(registeredName, addonId);
        }
        String id = registered.isEmpty() ? name : registered.get(0);
        dihclient.addons.AddonManager.recordAcceptedRegistration("command", id);
        return AddonRegistrationResult.accepted("command", id);
    }

    private static AddonRegistrationResult rejectAddonCommand(String addonId, String id, String reason) {
        DihClientAddon.LOG.warn("[Commands] Rejecting addon command '{}': {}", id, reason);
        dihclient.addons.AddonManager.recordRejectedRegistration(addonId, "command", id, reason);
        return AddonRegistrationResult.rejected("command", id, reason);
    }

    public static synchronized void unregisterAddonCommands(String addonId) {
        if (addonId == null || addonId.isBlank()) return;
        List<String> removeNames = new ArrayList<>();
        for (Map.Entry<String, String> owner : ADDON_COMMAND_OWNERS.entrySet()) {
            if (addonId.equals(owner.getValue())) removeNames.add(owner.getKey());
        }
        for (String name : removeNames) {
            BY_NAME.remove(name);
            ADDON_COMMAND_OWNERS.remove(name);
            DISABLED_ADDON_COMMAND_NAMES.add(name);
        }
        ALL.removeIf(command -> addonId.equals(ADDON_COMMAND_OBJECT_OWNERS.get(command)));
        ADDON_COMMAND_OBJECT_OWNERS.entrySet().removeIf(entry -> addonId.equals(entry.getValue()));
        if (!removeNames.isEmpty()) revision++;
    }

    public static CommandDispatcher<DihCommandSource> dispatcher() { return DISPATCHER; }

    public static int revision() { return revision; }

    public static List<Command> all() { return Collections.unmodifiableList(ALL); }

    public static Command find(String nameOrAlias) {
        if (nameOrAlias == null) return null;
        return BY_NAME.get(nameOrAlias.trim().toLowerCase(Locale.ROOT));
    }

    public static String effectivePrefix() { return DihCompatManager.effectiveCommandPrefix(); }

    public static boolean isDihCommandMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String trimmed = message.trim();
        String prefix = effectivePrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    public static boolean commandsBlockedByPanic() {
        return PackHideState.isHardLocked();
    }

    public static boolean isBlockedPanicCommandMessage(String message) {
        return commandsBlockedByPanic() && isPanicBlockedCommandMessage(message);
    }

    private static boolean isPanicBlockedCommandMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String trimmed = message.trim();
        if ("^toggledih".equalsIgnoreCase(trimmed)) return true;

        String prefix = effectivePrefix();
        if (!prefix.isEmpty() && trimmed.startsWith(prefix)) return true;

        for (String fallback : PANIC_PREFIX_FALLBACKS) {
            if (fallback == null || fallback.isEmpty() || fallback.equals(prefix)) continue;
            if (!trimmed.startsWith(fallback)) continue;
            String body = trimmed.substring(fallback.length()).trim();
            if (body.isEmpty()) continue;
            String first = firstToken(body).toLowerCase(Locale.ROOT);
            if (BY_NAME.containsKey(first) || DISABLED_ADDON_COMMAND_NAMES.contains(first)) return true;
        }
        return false;
    }

    public static String commandBody(String message) {
        if (!isDihCommandMessage(message)) return "";
        String trimmed = message.trim();
        int prefixLength = effectivePrefix().length();
        return trimmed.length() <= prefixLength ? "" : trimmed.substring(prefixLength).trim();
    }

    private static final ThreadLocal<Boolean> PLAIN_CHAT_BYPASS = ThreadLocal.withInitial(() -> false);

    public static boolean plainChatBypass() {
        return PLAIN_CHAT_BYPASS.get();
    }

    public static void sendPlainChat(net.minecraft.client.multiplayer.ClientPacketListener conn, String message) {
        PLAIN_CHAT_BYPASS.set(true);
        try {
            conn.sendChat(message);
        } finally {
            PLAIN_CHAT_BYPASS.remove();
        }
    }

    public static boolean dispatch(String body) {
        if (commandsBlockedByPanic()) return true;
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return false;
        if (DISABLED_ADDON_COMMAND_NAMES.contains(firstToken(trimmed).toLowerCase(Locale.ROOT))) {
            sendSyntaxError(trimmed, CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create());
            return true;
        }
        try {

            if (!dihclient.util.DihLiteVariant.enabled()
                && dihclient.util.multi.MultiPovCommandRouter.route(trimmed)) return true;
            DISPATCHER.execute(trimmed, DihCommandSource.INSTANCE);
            return true;
        } catch (CommandSyntaxException e) {
            sendSyntaxError(trimmed, e);
            return true;
        } catch (Throwable t) {
            DihClientAddon.LOG.warn("[Commands] dispatch failed for '{}'", trimmed, t);
            DihClientMessaging.sendPrefixed("§cCommand error: "
                    + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            return true;
        }
    }

    private static void sendSyntaxError(String body, CommandSyntaxException e) {
        String first = firstToken(body);
        Command command = find(first);
        String prefix = effectivePrefix();
        if (command == null) {
            DihClientMessaging.sendPrefixed("§cUnknown DIH command: §f" + first);
            DihClientMessaging.sendPrefixed("§7Use §f" + prefix + "commands §7or §f" + prefix + "help§7.");
            return;
        }

        String message = e.getMessage();
        if (message == null || message.isBlank()) message = "Incomplete or invalid command.";
        DihClientMessaging.sendPrefixed("§c" + message);
        DihClientMessaging.sendPrefixed("§7Use §f" + prefix + "help " + command.name() + "§7.");
    }

    private static String firstToken(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
