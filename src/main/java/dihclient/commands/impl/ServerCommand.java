package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.gui.screen.DihOverlayHostScreen;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihServerInfoOverlay;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class ServerCommand extends Command {
    public ServerCommand() { super("server", "Open the server info panel, or the plugin scanner."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> { openInfo(); return SUCCESS; });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("info").executes(ctx -> { openInfo(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("plugins").executes(ctx -> { openPlugins(); return SUCCESS; }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("tps").executes(ctx -> { tps(); return SUCCESS; }));
    }

    static void openInfo() {
        openOverlay(false);
    }

    static void openPlugins() {
        openOverlay(true);
    }

    private static void openOverlay(boolean pluginsTab) {
        DihServerInfoOverlay overlay = DihModule.get().getServerDataOverlay();
        if (overlay == null) { DihClientMessaging.sendPrefixed("§cServer overlay unavailable."); return; }
        DihOverlayManager.get().register(overlay);
        if (pluginsTab) overlay.openPluginsTab();
        else overlay.openInfoTab();

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new DihOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void tps() {
        double tps = dihclient.util.macro.ServerTickTracker.getEstimatedTps();
        DihClientMessaging.sendPrefixed(String.format("§eTPS: §f%.2f §7(estimated)", tps));
    }
}
