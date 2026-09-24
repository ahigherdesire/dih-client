package dihclient.modules;

import dihclient.api.custommenu.CustomMenuAdapterRegistry;
import dihclient.api.custommenu.CustomMenuSnapshot;
import dihclient.api.custommenu.CustomMenuSubmission;
import dihclient.api.custommenu.CustomMenuSubmitResult;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.StringSetting;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihJoinMacroController;
import dihclient.util.custommenu.CustomMenuTracker;
import dihclient.util.login.AutoLoginConfig;
import dihclient.util.login.AutoLoginEngine;
import dihclient.util.login.AutoLoginHost;
import java.util.Optional;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public final class AutoLoginModule extends Module {

    private static volatile AutoLoginModule instance;

    private final AutoLoginEngine engine = new AutoLoginEngine(new ClientHost());

    AutoLoginModule() {
        super("auto-login", "AutoLogin", ModuleCategory.PLAYER,
            "Answers server login and register gates.");
        instance = this;
        add(new StringSetting("password", "Password", "")
            .description("Sent to login and register gates.").build());
        add(new DoubleSetting("chat-delay", "Chat Delay", 2.0, 0.0, 10.0, 0.1)
            .description("Wait after spawning before sending.").unit("s").build());
        add(new DoubleSetting("window", "Window", 10.0, 3.0, 60.0, 0.5)
            .description("Stop watching this long after joining.").unit("s").build());
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
    }

    @Override
    public void onGameLeft() {
        engine.reset(System.currentTimeMillis());
    }

    @Override
    public void tick() {
        engine.configure(new AutoLoginConfig(
            (long) (decimal("window") * 1000.0),
            (long) (decimal("chat-delay") * 1000.0),
            2_500L, 4, 1_000L, 3_000L));
        engine.tick(System.currentTimeMillis());
    }

    public static void observeIncomingChat(Packet<?> packet) {
        AutoLoginModule module = instance;
        if (module == null || !module.isEnabled()) return;
        try {
            if (packet instanceof ClientboundSystemChatPacket system) {
                module.engine.onChatLine(system.content().getString());
            } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
                module.engine.onChatLine(disguised.message().getString());
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
            return CustomMenuTracker.current();
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
            DihClientMessaging.sendPrefixed("§c" + message);
        }

        @Override
        public void needsPassword(String context) {
            DihClientMessaging.sendPrefixed("§eAutoLogin: set a password in the module settings.");
        }
    }
}
