package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihGuiActions;
import dihclient.util.macro.RestoreGuiAction;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

/**
 * CLI for the container-screen packet primitives (Save / Desync / Load / Close).
 *
 * <p>These are the building blocks of the desync workflow: {@code gui save} stores the
 * live {@code ScreenHandler}, {@code gui desync} sends a {@code ServerboundContainerClosePacket}
 * while keeping the screen open, and {@code gui load} re-injects the stored handler.
 * Backed by {@link DihGuiActions} and {@link RestoreGuiAction} - the exact code paths
 * the on-screen buttons use.
 */
public class GuiCommand extends Command {
    public GuiCommand() {
        super("gui", "Container-screen actions: save, desync, load, close.", "screen");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("save")
            .executes(ctx -> { DihGuiActions.saveCurrentGui(mc(), true); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("desync")
            .executes(ctx -> {
                if (!DihGuiActions.desyncCurrentScreen(mc(), true)) {
                    DihClientMessaging.sendPrefixed("§cFailed to desync: no open networked GUI.");
                }
                return SUCCESS;
            }));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("load")
            .executes(ctx -> { new RestoreGuiAction().execute(mc()); return SUCCESS; }));

        // gui close            -> close and tell the server (default)
        // gui close nopacket   -> close locally only (suppress the close packet)
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("close")
            .executes(ctx -> close(true))
            .then(LiteralArgumentBuilder.<DihCommandSource>literal("packet").executes(ctx -> close(true)))
            .then(LiteralArgumentBuilder.<DihCommandSource>literal("nopacket").executes(ctx -> close(false))));
    }

    private static int close(boolean sendPacket) {
        if (!DihGuiActions.closeCurrentScreen(mc(), sendPacket, true)) {
            DihClientMessaging.sendPrefixed("§cNo screen to close.");
        }
        return SUCCESS;
    }

    private static int usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage:");
        DihClientMessaging.sendPrefixed("§f" + p + "gui save §7- store the current screen handler (no packet)");
        DihClientMessaging.sendPrefixed("§f" + p + "gui desync §7- send close packet, keep screen open");
        DihClientMessaging.sendPrefixed("§f" + p + "gui load §7- re-open the stored screen handler");
        DihClientMessaging.sendPrefixed("§f" + p + "gui close [packet|nopacket] §7- close the screen");
        return SUCCESS;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }
}
