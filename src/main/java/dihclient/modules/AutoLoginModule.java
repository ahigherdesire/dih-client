package dihclient.modules;

import dihclient.api.custommenu.CustomMenuAdapterRegistry;
import dihclient.api.custommenu.CustomMenuSnapshot;
import dihclient.api.custommenu.CustomMenuSubmission;
import dihclient.api.custommenu.CustomMenuSubmitResult;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihJoinMacroController;
import dihclient.util.custommenu.CustomMenuTracker;
import dihclient.util.login.AutoLoginConfig;
import dihclient.util.login.AuthMeChatLogin;
import dihclient.util.login.AutoLoginEngine;
import dihclient.util.login.AutoLoginHost;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public final class AutoLoginModule extends Module {

    private static volatile AutoLoginModule instance;

    private final AutoLoginEngine engine = new AutoLoginEngine(new ClientHost());
    private boolean successNoted;

    AutoLoginModule() {
        super("auto-login", "AutoLogin", ModuleCategory.PLAYER,
            "Answers server login and register gates.");
        instance = this;
        add(new StringSetting("password", "Password", "")
            .group("General")
            .description("Sent to login and register gates.").build());
        add(new StringSetting("servers", "Only On Servers", "")
            .group("General")
            .description("Comma-separated server addresses to log in on; empty means every server").build());

        add(new BoolSetting("chat-prompts", "Chat Prompts", true)
            .group("Prompts")
            .description("Answers /login and /register requests in chat").build());
        add(new BoolSetting("answer-register", "Answer Register", true)
            .group("Prompts")
            .description("Also registers new accounts, not only logs in")
            .visibleWhen(() -> bool("chat-prompts")).build());
        add(new BoolSetting("menu-prompts", "Menu Prompts", true)
            .group("Prompts")
            .description("Fills in and submits login dialog screens").build());

        add(new DoubleSetting("chat-delay", "Chat Delay", 2.0, 0.0, 10.0, 0.1)
            .group("Timing")
            .description("Wait after spawning before sending.").unit("s").build());
        add(new DoubleSetting("window", "Window", 10.0, 3.0, 60.0, 0.5)
            .group("Timing")
            .description("Stop watching this long after joining.").unit("s").build());
        add(new DoubleSetting("resend-delay", "Resend Delay", 2.5, 0.5, 10.0, 0.5)
            .group("Timing")
            .description("Wait before trying again when the server asks a second time").unit("s").build());
        add(new IntSetting("max-attempts", "Max Attempts", 4, 1, 10, 1)
            .group("Timing")
            .description("Gives up after sending the password this many times").build());
        add(new DoubleSetting("menu-retry", "Menu Retry", 1.0, 0.1, 5.0, 0.1)
            .group("Timing")
            .description("Wait before retrying a login dialog that could not be submitted").unit("s").build());
        add(new DoubleSetting("register-follow-up", "Login After Register", 3.0, 0.5, 10.0, 0.5)
            .group("Timing")
            .description("Sends /login this long after registering if the server stays quiet").unit("s").build());

        add(new BoolSetting("notify-success", "Notify Success", false)
            .group("Feedback")
            .description("Says in chat once the login went through").build());
        add(new BoolSetting("warn-failure", "Warn On Failure", true)
            .group("Feedback")
            .description("Says in chat when it gives up or needs a password").build());
    }

    @Override
    public boolean ticksWhenDisabled() {
        return false;
    }

    @Override
    public boolean settingsShareable() {
        return false;
    }

    @Override
    public void onGameJoin() {
        engine.reset(System.currentTimeMillis());
        successNoted = false;
    }

    @Override
    public void onGameLeft() {
        engine.reset(System.currentTimeMillis());
    }

    @Override
    public void tick() {
        if (!onAllowedServer()) return;
        engine.configure(new AutoLoginConfig(
            (long) (decimal("window") * 1000.0),
            (long) (decimal("chat-delay") * 1000.0),
            (long) (decimal("resend-delay") * 1000.0),
            integer("max-attempts"),
            (long) (decimal("menu-retry") * 1000.0),
            (long) (decimal("register-follow-up") * 1000.0)));
        engine.tick(System.currentTimeMillis());
        if (!successNoted && engine.phase() == AutoLoginEngine.Phase.DONE) {
            successNoted = true;
            if (bool("notify-success")) DihClientMessaging.sendPrefixed("§aAutoLogin: logged in.");
        }
    }

    private boolean onAllowedServer() {
        String servers = text("servers");
        if (servers == null || servers.isBlank()) return true;
        ServerData server = MC.getCurrentServer();
        if (server == null || server.ip == null) return false;
        String current = server.ip.trim().toLowerCase(Locale.ROOT);
        for (String entry : servers.split(",")) {
            String wanted = entry.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) continue;
            if (current.equals(wanted) || current.startsWith(wanted + ":")) return true;
        }
        return false;
    }

    private void chatLine(String line) {
        if (!bool("chat-prompts") || !onAllowedServer()) return;
        if (!bool("answer-register") && AuthMeChatLogin.detect(line).kind() == AuthMeChatLogin.Kind.REGISTER) return;
        engine.onChatLine(line);
    }

    public static void observeIncomingChat(Packet<?> packet) {
        AutoLoginModule module = instance;
        if (module == null || !module.isEnabled()) return;
        try {
            if (packet instanceof ClientboundSystemChatPacket system) {
                module.chatLine(system.content().getString());
            } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
                module.chatLine(disguised.message().getString());
            }
        } catch (Throwable ignored) {

        }
    }

    private final class ClientHost implements AutoLoginHost {

        @Override
        public String password() {
            String configured = text("password");
            if (configured != null && !configured.isBlank()) return configured;

            try {
                String shared = DihJoinMacroController.openFormValues().get("password");
                if (shared != null && !shared.isBlank()) return shared;
            } catch (RuntimeException ignored) {  }
            return "";
        }

        @Override
        public boolean spawnedInWorld() {
            return MC != null && MC.getConnection() != null && MC.player != null && MC.level != null;
        }

        @Override
        public boolean canSendChat() {
            return MC != null && MC.getConnection() != null;
        }

        @Override
        public CustomMenuSnapshot customMenu() {
            return bool("menu-prompts") ? CustomMenuTracker.current() : null;
        }

        @Override
        public boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
            CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, submission);
            if (result == null || !result.success()) return false;
            for (Packet<?> packet : result.packets()) {

                if (!DihJoinMacroController.sendCommonPacket(packet)) return false;
            }
            CustomMenuTracker.consume(snapshot, result.replacement());
            if (result.clientAction() != null && MC != null) {

                MC.execute(() -> {
                    if (MC.gui.screen() instanceof DialogScreen<?> dialog) {
                        dialog.runAction(Optional.of(result.clientAction()));
                    }
                });
            }
            return true;
        }

        @Override
        public boolean sendCommandLine(String line) {
            if (!canSendChat()) return false;
            sendCommand(line);
            return true;
        }

        @Override
        public boolean screenOwnedElsewhere() {
            return DihJoinMacroController.isDrivingCustomMenu();
        }

        @Override
        public void note(String message) {
            if (bool("warn-failure")) DihClientMessaging.sendPrefixed("§c" + message);
        }

        @Override
        public void needsPassword(String context) {
            if (bool("warn-failure")) DihClientMessaging.sendPrefixed("§eAutoLogin: set a password in the module settings.");
        }
    }
}
