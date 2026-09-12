package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.PacketClassArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.macro.PacketGateAction;
import autismclient.util.macro.PacketGateManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI for the packet gate manager. A gate is a named rule that cancels, delays, or
 * allow-only's specific packet types until it is ended. Backed by {@link PacketGateManager}.
 *
 * <p>Gates created here use the id {@code cli} unless one is given, so {@code gate end}
 * with no id tears down the last CLI gate.
 */
public class GateCommand extends Command {
    private static final String DEFAULT_ID = "cli";

    public GateCommand() {
        super("gate", "Packet gate control: cancel/delay/allow packets, then end or clear.", "packetgate");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(mode("cancel", PacketGateAction.GateMode.CANCEL));
        root.then(mode("delay", PacketGateAction.GateMode.DELAY));
        root.then(mode("allow", PacketGateAction.GateMode.ALLOW_ONLY));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("status")
            .executes(ctx -> status()));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear")
            .executes(ctx -> {
                PacketGateManager.clearAllAndFlushConfigured(connection());
                AutismClientMessaging.sendPrefixed("§cCleared all packet gates.");
                return SUCCESS;
            }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("end")
            .executes(ctx -> end(DEFAULT_ID))
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("id", StringArgumentType.word())
                .executes(ctx -> end(StringArgumentType.getString(ctx, "id")))));
    }

    private LiteralArgumentBuilder<AutismCommandSource> mode(String literal, PacketGateAction.GateMode gateMode) {
        return LiteralArgumentBuilder.<AutismCommandSource>literal(literal)
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("packets", StringArgumentType.greedyString())
                .suggests(new PacketClassArgumentType()::listSuggestions)
                .executes(ctx -> install(gateMode, StringArgumentType.getString(ctx, "packets"))));
    }

    private static int install(PacketGateAction.GateMode mode, String raw) {
        List<String> packets = split(raw);
        if (packets.isEmpty()) {
            AutismClientMessaging.sendPrefixed("§cName at least one packet, e.g. §fServerboundContainerClosePacket§c.");
            return SUCCESS;
        }
        PacketGateAction action = new PacketGateAction();
        action.mode = mode;
        action.direction = PacketGateAction.Direction.ANY;
        action.durationMode = PacketGateAction.DurationMode.UNTIL_DISABLED;
        action.gateId = DEFAULT_ID;
        action.packetNames = new ArrayList<>(packets);
        action.flushOnDisable = true;
        PacketGateManager.install(action);
        AutismClientMessaging.sendPrefixed("§aGate §f" + DEFAULT_ID + " §a[" + mode + "] §7-> §f" + String.join(", ", packets));
        AutismClientMessaging.sendPrefixed("§7End it with §f" + AutismCommands.effectivePrefix() + "gate end§7.");
        return SUCCESS;
    }

    private static int end(String id) {
        PacketGateManager.disableAndFlushConfigured(id, connection(), true);
        AutismClientMessaging.sendPrefixed("§7Ended gate §f" + id + "§7.");
        return SUCCESS;
    }

    private static int status() {
        int count = PacketGateManager.activeGateCount();
        if (count == 0) {
            AutismClientMessaging.sendPrefixed("§7No active packet gates.");
            return SUCCESS;
        }
        AutismClientMessaging.sendPrefixed("§7Active gates (§f" + count + "§7):");
        for (String line : PacketGateManager.describeActiveGates()) {
            AutismClientMessaging.sendPrefixed("§7- §f" + line);
        }
        return SUCCESS;
    }

    private static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String token : raw.split("[\\s,]+")) {
            if (!token.isBlank()) out.add(token.trim());
        }
        return out;
    }

    private static int usage() {
        String p = AutismCommands.effectivePrefix();
        AutismClientMessaging.sendPrefixed("§eUsage:");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate cancel <packet...> §7- drop matching packets");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate delay <packet...> §7- hold matching packets in the queue");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate allow <packet...> §7- drop everything except these");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate end [id] §7- end a gate (default id: cli)");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate clear §7- end all gates");
        AutismClientMessaging.sendPrefixed("§f" + p + "gate status §7- list active gates");
        return SUCCESS;
    }

    private static net.minecraft.client.multiplayer.ClientPacketListener connection() {
        return Minecraft.getInstance().getConnection();
    }
}
