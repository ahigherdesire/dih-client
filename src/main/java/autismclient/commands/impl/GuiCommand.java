package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.commands.AutismCommands;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismGuiActions;
import autismclient.util.macro.RestoreGuiAction;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

/**
 * CLI for the container-screen packet primitives (Save / Desync / Load / Close).
 *
 * <p>These are the building blocks of the desync workflow: {@code gui save} stores the
 * live {@code ScreenHandler}, {@code gui desync} sends a {@code ServerboundContainerClosePacket}
 * while keeping the screen open, and {@code gui load} re-injects the stored handler.
 * Backed by {@link AutismGuiActions} and {@link RestoreGuiAction} - the exact code paths
 * the on-screen buttons use.
 */
public class GuiCommand extends Command {
    public GuiCommand() {
        super("gui", "Container-screen actions: save, desync, load, close.", "screen");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("save")
            .executes(ctx -> { AutismGuiActions.saveCurrentGui(mc(), true); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("desync")
            .executes(ctx -> {
                if (!AutismGuiActions.desyncCurrentScreen(mc(), true)) {
                    AutismClientMessaging.sendPrefixed("§cFailed to desync: no open networked GUI.");
                }
                return SUCCESS;
            }));

        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("load")
            .executes(ctx -> { new RestoreGuiAction().execute(mc()); return SUCCESS; }));

        // gui close            -> close and tell the server (default)
        // gui close nopacket   -> close locally only (suppress the close packet)
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("close")
            .executes(ctx -> close(true))
            .then(LiteralArgumentBuilder.<AutismCommandSource>literal("packet").executes(ctx -> close(true)))
            .then(LiteralArgumentBuilder.<AutismCommandSource>literal("nopacket").executes(ctx -> close(false))));
    }

    private static int close(boolean sendPacket) {
        if (!AutismGuiActions.closeCurrentScreen(mc(), sendPacket, true)) {
            AutismClientMessaging.sendPrefixed("§cNo screen to close.");
        }
        return SUCCESS;
    }

    private static int usage() {
        String p = AutismCommands.effectivePrefix();
        AutismClientMessaging.sendPrefixed("§eUsage:");
        AutismClientMessaging.sendPrefixed("§f" + p + "gui save §7- store the current screen handler (no packet)");
        AutismClientMessaging.sendPrefixed("§f" + p + "gui desync §7- send close packet, keep screen open");
        AutismClientMessaging.sendPrefixed("§f" + p + "gui load §7- re-open the stored screen handler");
        AutismClientMessaging.sendPrefixed("§f" + p + "gui close [packet|nopacket] §7- close the screen");
        return SUCCESS;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }
}
