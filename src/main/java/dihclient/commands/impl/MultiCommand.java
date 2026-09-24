package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.args.MacroArgumentType;
import dihclient.commands.args.MultiBotArgumentType;
import dihclient.commands.args.MultiProfileArgumentType;
import dihclient.gui.screen.DihMultiConsoleScreen;
import dihclient.gui.screen.DihMultiScreen;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.multi.MultiManager;
import dihclient.util.multi.MultiProfile;
import dihclient.util.multi.MultiProfileManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public class MultiCommand extends Command {
    public MultiCommand() {
        super("multi", "Open the Multi menu, or launch/stop a profile.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> { openMenu(); return SUCCESS; });

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("open")
            .executes(ctx -> { openMenu(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("stop")
            .executes(ctx -> { stopBatch(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("status")
            .executes(ctx -> { status(); return SUCCESS; }));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("launch")
            .executes(ctx -> {
                DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix() + "multi launch <profile>");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("profile", MultiProfileArgumentType.profileName())
                .executes(ctx -> { launch(MultiProfileArgumentType.get(ctx, "profile")); return SUCCESS; })));

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("macro")
            .executes(ctx -> {
                DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix()
                    + "multi macro <macro> [bot] §7- no bot = all bots");
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("macro", MacroArgumentType.macroName())
                .executes(ctx -> { runMacro(MacroArgumentType.get(ctx, "macro"), ""); return SUCCESS; })
                .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("bot", MultiBotArgumentType.botName())
                    .executes(ctx -> {
                        runMacro(MacroArgumentType.get(ctx, "macro"), MultiBotArgumentType.get(ctx, "bot"));
                        return SUCCESS;
                    }))));
    }

    private static void runMacro(String macroName, String botName) {
        MultiManager manager = MultiManager.get();
        if (!manager.isActive()) {
            DihClientMessaging.sendPrefixed("§7No Multi batch is active.");
            return;
        }
        DihMacro macro = DihMacroManager.get().get(macroName);
        if (macro == null) {
            DihClientMessaging.sendPrefixed("§cNo macro named: §f" + macroName);
            return;
        }
        Set<String> scope = manager.scopeForBot(botName);
        if (scope == null) {
            DihClientMessaging.sendPrefixed("§cNo bot named: §f" + botName);
            String live = liveBotNames(manager);
            if (!live.isEmpty()) DihClientMessaging.sendPrefixed("§7Bots: §f" + live);
            return;
        }
        MultiManager.BroadcastResult result = manager.runMacroDirect(macro, scope);
        String target = botName == null || botName.isBlank() ? "all bots" : botName;
        DihClientMessaging.sendPrefixed("§aRunning §f" + macro.name + " §aon §f" + target
            + " §7- " + result.summary());
    }

    private static String liveBotNames(MultiManager manager) {
        StringBuilder sb = new StringBuilder();
        for (MultiManager.BotHandle bot : manager.liveBots()) {
            if (sb.length() > 0) sb.append("§7, §f");
            sb.append(bot.username());
        }
        return sb.toString();
    }

    private static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.gui.screen() != null) return;
            Runnable proceed = () -> {
                if (mc.level != null) {

                    dihclient.modules.DihModule module = dihclient.modules.DihModule.get();
                    if (module != null) module.openMultiUiInGame();
                } else if (MultiManager.get().isActive()) {
                    mc.gui.setScreen(new DihMultiConsoleScreen(null));
                } else {
                    mc.gui.setScreen(new DihMultiScreen(null, currentServerAddress(mc)));
                }
            };

            dihclient.gui.screen.DihMultiDisclaimerScreen.open(mc, null, proceed);
        });
    }

    private static void launch(String name) {
        MultiProfile profile = resolveByName(MultiProfileManager.get().all(), name);
        if (profile == null) {
            DihClientMessaging.sendPrefixed("§cNo profile named: §f" + name);
            String available = availableNames();
            if (!available.isEmpty()) DihClientMessaging.sendPrefixed("§7Profiles: §f" + available);
            return;
        }
        MultiManager.StartResult result = MultiManager.get().start(profile);
        if (result.ok()) {
            DihClientMessaging.sendPrefixed("§aLaunching Multi profile: §f" + profile.name
                + " §7(" + profile.sessions.size() + " account(s)) - run "
                + DihCommands.effectivePrefix() + "multi to open the console.");
        } else {
            DihClientMessaging.sendPrefixed("§cCould not launch: §f" + result.message());
        }
    }

    private static void stopBatch() {
        if (!MultiManager.get().isActive()) {
            DihClientMessaging.sendPrefixed("§7No Multi batch is active.");
            return;
        }
        MultiManager.get().disconnectAll("Stopped via command");
        DihClientMessaging.sendPrefixed("§eStopped the Multi batch.");
    }

    private static void status() {
        MultiManager mm = MultiManager.get();
        if (!mm.isActive()) {
            DihClientMessaging.sendPrefixed("§7Multi: no batch active.");
            return;
        }
        MultiProfile active = mm.activeProfile();
        String name = active == null || active.name == null ? "(unknown)" : active.name;
        DihClientMessaging.sendPrefixed("§aMulti: §f" + name
            + " §7- §f" + mm.readyCount() + "§7 ready, §f" + mm.connectedCount() + "§7 connected.");
    }

    private static String currentServerAddress(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        return sd == null || sd.ip == null ? "" : sd.ip.trim();
    }

    private static String availableNames() {
        StringBuilder sb = new StringBuilder();
        for (MultiProfile p : MultiProfileManager.get().all()) {
            if (p == null || p.name == null) continue;
            if (sb.length() > 0) sb.append("§7, §f");
            sb.append(p.name);
        }
        return sb.toString();
    }

    public static MultiProfile resolveByName(Collection<MultiProfile> profiles, String name) {
        if (profiles == null || name == null) return null;
        String want = name.trim().toLowerCase(Locale.ROOT);
        if (want.isEmpty()) return null;
        for (MultiProfile p : profiles) {
            if (p != null && p.name != null && p.name.trim().toLowerCase(Locale.ROOT).equals(want)) return p;
        }
        return null;
    }
}
