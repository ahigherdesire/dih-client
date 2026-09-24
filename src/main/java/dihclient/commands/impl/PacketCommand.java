package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.commands.DihCommands;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihSharedState;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Full CLI control over the runtime packet queue: the "packet manager".
 *
 * <p>Mirrors the on-screen Send / Delay / Flush / Clear controls and the
 * {@code keybindToggleSend} / {@code keybindToggleDelay} / {@code keybindFlushQueue}
 * / {@code keybindClearQueue} keybinds so the whole subsystem is scriptable from chat.
 */
public class PacketCommand extends Command {
    public PacketCommand() {
        super("packet", "Control the runtime packet queue: send/delay toggles, flush, clear, status.", "pkt");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("status")
            .executes(ctx -> status()));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("flush")
            .executes(ctx -> flush()));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear")
            .executes(ctx -> clear()));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("send")
            .executes(ctx -> send(null))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("state", StringArgumentType.word())
                .suggests(dihclient.commands.CommandSuggest::state)
                .executes(ctx -> send(StringArgumentType.getString(ctx, "state")))));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("delay")
            .executes(ctx -> delay(null))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("state", StringArgumentType.word())
                .suggests(dihclient.commands.CommandSuggest::state)
                .executes(ctx -> delay(StringArgumentType.getString(ctx, "state")))));
    }

    private static int usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage:");
        DihClientMessaging.sendPrefixed("§f" + p + "packet status §7- show send/delay flags and queue size");
        DihClientMessaging.sendPrefixed("§f" + p + "packet send <on|off|toggle> §7- toggle sending outgoing packets");
        DihClientMessaging.sendPrefixed("§f" + p + "packet delay <on|off|toggle> §7- toggle the delay queue");
        DihClientMessaging.sendPrefixed("§f" + p + "packet flush §7- release queued packets to the server");
        DihClientMessaging.sendPrefixed("§f" + p + "packet clear §7- discard queued packets");
        return SUCCESS;
    }

    private static int status() {
        DihSharedState s = DihSharedState.get();
        int queued = s.getDelayedPackets().size() + s.getStaggeredQueue().size();
        DihClientMessaging.sendPrefixed("§7Send: " + onOff(s.shouldSendGuiPackets())
            + " §7| Delay: " + onOff(s.shouldDelayGuiPackets())
            + " §7| Queued: §f" + queued);
        return SUCCESS;
    }

    private static int flush() {
        int flushed = DihModule.get().flushQueuedPacketsUiBehavior();
        DihClientMessaging.sendPrefixed("§aFlushed §f" + flushed + " §apacket" + (flushed == 1 ? "" : "s") + ".");
        return SUCCESS;
    }

    private static int clear() {
        int cleared = DihModule.get().clearQueuedPacketsUiBehavior();
        DihClientMessaging.sendPrefixed("§cCleared §f" + cleared + " §cqueued packet" + (cleared == 1 ? "" : "s") + ".");
        return SUCCESS;
    }

    private static int send(String state) {
        Boolean target = resolve(state, DihSharedState.get().shouldSendGuiPackets());
        if (target == null) return unknownState(state);
        DihModule.get().applySendGuiPacketsUiBehavior(target);
        DihClientMessaging.sendPrefixed("§7Packet send: " + onOff(target));
        return SUCCESS;
    }

    private static int delay(String state) {
        Boolean target = resolve(state, DihSharedState.get().shouldDelayGuiPackets());
        if (target == null) return unknownState(state);
        DihModule module = DihModule.get();
        int flushed = module.applyDelayGuiPacketsUiBehavior(target);
        module.notifyDelayPacketsUiResult(target, flushed);
        DihClientMessaging.sendPrefixed("§7Packet delay: " + onOff(target)
            + (flushed > 0 ? " §7(flushed §f" + flushed + "§7)" : ""));
        return SUCCESS;
    }

    private static Boolean resolve(String state, boolean current) {
        if (state == null) return !current;
        return switch (state.toLowerCase()) {
            case "on", "enable", "true", "1" -> true;
            case "off", "disable", "false", "0" -> false;
            case "toggle" -> !current;
            default -> null;
        };
    }

    private static int unknownState(String state) {
        DihClientMessaging.sendPrefixed("§cUnknown state: §f" + state);
        DihClientMessaging.sendPrefixed("§7Use §fon§7, §foff§7, or §ftoggle§7.");
        return SUCCESS;
    }

    private static String onOff(boolean value) {
        return value ? "§aon" : "§coff";
    }
}
