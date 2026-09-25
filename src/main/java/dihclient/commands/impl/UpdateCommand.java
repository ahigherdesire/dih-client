package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihConfig;
import dihclient.util.DihLinks;
import dihclient.util.DihUpdateChecker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class UpdateCommand extends Command {
    public UpdateCommand() {
        super("update", "Check GitHub for a newer DIH build.", "updates");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            report();
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("check").executes(ctx -> {
            if (!DihConfig.getGlobal().updateCheck) {
                DihClientMessaging.sendPrefixed("§7Update checks are off. §f.update on§7 to turn them back on.");
                return SUCCESS;
            }
            DihUpdateChecker.check();
            DihClientMessaging.sendPrefixed("§7Checking GitHub... run §f.update§7 in a moment.");
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("download").executes(ctx -> {
            DihUpdateChecker.Result r = DihUpdateChecker.result();
            String url = r.downloadUrl().isEmpty() ? r.pageUrl() : r.downloadUrl();
            DihLinks.open(url);
            DihClientMessaging.sendPrefixed("§7Opened §f" + url);
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("on").executes(ctx -> setEnabled(true)));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("off").executes(ctx -> setEnabled(false)));
    }

    private static int setEnabled(boolean on) {
        DihConfig cfg = DihConfig.getGlobal();
        cfg.updateCheck = on;
        cfg.save();
        DihClientMessaging.sendPrefixed(on ? "§aUpdate checks on." : "§7Update checks off. DIH won't contact GitHub.");
        if (on) DihUpdateChecker.check();
        return SUCCESS;
    }

    private static void report() {
        DihUpdateChecker.Result r = DihUpdateChecker.result();
        String color = r.updateAvailable() ? "§e" : "§7";
        DihClientMessaging.sendPrefixed(color + DihUpdateChecker.summary(r).getString());
        if (r.updateAvailable()) {
            DihClientMessaging.sendPrefixed("§f.update download§7 opens the new jar. Replace the old one in your mods folder and restart.");
        } else {
            DihClientMessaging.sendPrefixed("§7Running §f" + r.current() + "§7. §f.update check§7 asks again, §f.update off§7 stops checking.");
        }
    }
}
