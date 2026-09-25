package dihclient;

import dihclient.gui.vanillaui.components.UiText;
import dihclient.modules.PackAutoReconnectState;
import dihclient.modules.DihModule;
import dihclient.render.DihFemaleBodyRenderer;
import dihclient.security.DihItemNbtSanity;
import dihclient.security.DihProtector;
import dihclient.security.DihProtectorPackStrip;
import dihclient.security.DihProtectorServerPackFailureGuard;
import dihclient.security.DihProtectorTracker;
import dihclient.security.DihProtectorVanillaKeys;
import dihclient.util.DihInstaBreakRenderer;
import dihclient.util.DihFakeGamemode;
import dihclient.util.DihJoinMacroController;
import dihclient.util.DihLANSync;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihSvgHudLogo;
import dihclient.util.DihWindowBranding;
import dihclient.util.DihSharedState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public final class DihClientMod implements ClientModInitializer {

    private static void runSafe(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            DihClientAddon.LOG.warn("[Dih] client-event '{}' failed; isolated to protect the client", where, t);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_TICK_ERROR_MS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void logTickError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_TICK_ERROR_MS.get(where);
        if (last != null && now - last < 5000L) return;
        LAST_TICK_ERROR_MS.put(where, now);
        DihClientAddon.LOG.warn("[Dih] tick '{}' failed; isolated to protect the client", where, t);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        DihClientAddon.FOLDER.mkdirs();
        dihclient.dev.DihDevScreenshots.registerIfRequested();

        runSafe("initialize", () -> DihModule.get().initialize());
        runSafe("updateCheck", dihclient.util.DihUpdateChecker::checkOnce);

        DihProtectorTracker.bootstrap();

        if (!DihProtector.isOverlapExternalProtectorPresent()) {
            DihProtectorVanillaKeys.primeAsync();
        }
        if (DihProtector.isFullExternalProtectorPresent()) {
            DihClientAddon.LOG.info(
                "[DihProtector] External protection mod detected; deferring all anti-fingerprint mixins to it.");
        } else if (DihProtector.isExploitPreventerPresent()) {
            DihClientAddon.LOG.info(
                "[DihProtector] ExploitPreventer detected; deferring overlapping protections while keeping brand/channel hiding active.");
        } else {
            DihClientAddon.LOG.info(
                "[DihProtector] Built-in anti-fingerprint layer active.");
        }
        DihInstaBreakRenderer.initialize();
        dihclient.util.DihFreecamHighlightRenderer.initialize();
        dihclient.util.DihSkeletonRenderer.initialize();
        dihclient.util.DihWorldHighlightRenderer.initialize();
        dihclient.util.DihScaffoldPlaceRenderer.initialize();
        dihclient.util.DihKillAuraRenderer.initialize();
        dihclient.util.DihBaritonePathRenderer.initialize();

        dihclient.modules.HoleEspModule.initialize();
        DihFemaleBodyRenderer.initialize();
        dihclient.commands.DihCommands.init();

        dihclient.addons.AddonManager.init();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public net.minecraft.resources.Identifier getFabricId() {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath("dihclient", "ui_assets");
            }

            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                UiText.onClientResourceReload();
                DihSvgHudLogo.clear();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            try {
                if (dihclient.util.DihBlockNbtCapture.hasTickWork()) {
                    dihclient.util.DihBlockNbtCapture.tick(client);
                }
            } catch (Throwable t) {
                logTickError("blockNbtCapture", t);
            }
            try {
                dihclient.modules.ScaffoldModule.endMovementTick();
            } catch (Throwable t) {
                logTickError("scaffoldEndClick", t);
            }
            try {
                DihWindowBranding.apply(client);
            } catch (Throwable ignored) {  }
            try {
                dihclient.security.DihPackResponseScheduler.tick();
            } catch (Throwable t) {
                logTickError("packResponses", t);
            }
            try {
                DihModule.get().tick();
            } catch (Throwable t) {
                logTickError("module", t);
            }
            try {

                dihclient.modules.ModuleOreSim.tickRetention();
            } catch (Throwable t) {
                logTickError("oreSimRetention", t);
            }
            try {
                dihclient.util.multi.PacketTeleportController.tick(client);
            } catch (Throwable t) {
                logTickError("pacedTp", t);
            }
            try {
                DihJoinMacroController.onClientTick(client);
            } catch (Throwable t) {
                logTickError("joinMacro", t);
            }
            try {
                dihclient.util.DihLagWatchdog.onClientTick(client);
            } catch (Throwable t) {
                logTickError("lagWatchdog", t);
            }
            try {

                if (!dihclient.util.DihLiteVariant.enabled()) {
                    dihclient.util.multi.MultiTakeoverState.tick();
                }
            } catch (Throwable t) {
                logTickError("multiTakeover", t);
            }
            try {
                dihclient.util.DihRemoteView.tick();
            } catch (Throwable t) {
                logTickError("remoteView", t);
            }
            try {

                if (!dihclient.util.DihLiteVariant.enabled()
                    && dihclient.util.DihDiscordLogin.hasSession() && dihclient.util.mm.MmPrefs.get().autoJoinPublic())
                    dihclient.util.mm.MatchmakingManager.get().autoJoinNudge();
            } catch (Throwable t) {
                logTickError("mmAutoJoin", t);
            }
        });
        ClientTickEvents.END_LEVEL_TICK.register(level ->
            runSafe("lan.levelTick", () -> DihLANSync.getInstance().onLevelTick(level.getGameTime())));
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> {
            runSafe("cfg.hideMenuOverlays", () -> DihModule.get().hideMenuOverlays());
            runSafe("cfg.configStarted", () -> DihModule.get().onConfigurationConnectionStarted());
            runSafe("cfg.joinMacro", () -> DihJoinMacroController.onConfigurationInit(listener));
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
            runSafe("cfg.remoteView", () -> dihclient.util.DihRemoteView.stop(false));
            runSafe("cfg.persist", dihclient.util.DihConfig::enqueuePendingSaveNow);
            runSafe("cfg.pacedTp", () -> dihclient.util.multi.PacketTeleportController.cancelAll("connection changed"));
            runSafe("cfg.fakeGamemode", DihFakeGamemode::clear);
            runSafe("cfg.instaBreak", DihInstaBreakRenderer::clear);
            runSafe("cfg.packFailureGuard", DihProtectorServerPackFailureGuard::clear);
            runSafe("cfg.packResponses", dihclient.security.DihPackResponseScheduler::clearAll);
            runSafe("cfg.hideOverlays", () -> DihOverlayManager.get().hideAllInteractiveOverlays());
            runSafe("cfg.onGameLeft", () -> DihModule.get().onGameLeft());
            runSafe("cfg.joinMacro", DihJoinMacroController::onConfigurationDisconnect);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            runSafe("join.pacedTp", () -> dihclient.util.multi.PacketTeleportController.cancelAll("connection changed"));

            runSafe("join.movementGrace", dihclient.modules.DihJoinGrace::onJoin);
            runSafe("join.hideMenuOverlays", () -> DihModule.get().hideMenuOverlays());
            runSafe("join.remember", () -> PackAutoReconnectState.remember(client.getCurrentServer()));
            runSafe("join.onGameJoin", () -> DihModule.get().onGameJoin());
            runSafe("join.joinMacro", DihJoinMacroController::onPlayJoin);
            runSafe("join.surfaceFailures", dihclient.addons.AddonManager::surfaceFailuresOnJoin);
            runSafe("join.lagWatchdog", dihclient.util.DihLagWatchdog::reset);
            runSafe("join.macroEditor", dihclient.util.DihMacroEditorOverlay::onPlayJoin);
            runSafe("join.updateNotice", dihclient.util.DihUpdateChecker::announceOnJoin);
            runSafe("join.acquireKnowledge", baritone.acquire.knowledge.VanillaKnowledge::preload);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            runSafe("leave.seedMap", baritone.command.defaults.SeedMapCommand::onDisconnect);
            runSafe("leave.remoteView", () -> dihclient.util.DihRemoteView.stop(false));
            runSafe("leave.persist", dihclient.util.DihConfig::enqueuePendingSaveNow);
            runSafe("leave.stopMacro", () -> {
                if (dihclient.util.DihConfig.getGlobal().stopMacroOnLeave) dihclient.util.macro.MacroExecutor.stop();
            });
            runSafe("leave.pacedTp", () -> dihclient.util.multi.PacketTeleportController.cancelAll("disconnected"));
            runSafe("leave.movementGrace", dihclient.modules.DihJoinGrace::clear);
            runSafe("leave.fakeGamemode", DihFakeGamemode::clear);
            runSafe("leave.instaBreak", DihInstaBreakRenderer::clear);
            runSafe("leave.packStrip", DihProtectorPackStrip::clearAll);
            runSafe("leave.packResponses", dihclient.security.DihPackResponseScheduler::clearAll);
            runSafe("leave.packFailureGuard", DihProtectorServerPackFailureGuard::clear);
            runSafe("leave.macroEditor", dihclient.util.DihMacroEditorOverlay::onPlayDisconnect);
            runSafe("leave.lanSync", () -> DihLANSync.getInstance().onGameDisconnected());
            runSafe("leave.hideOverlays", () -> DihOverlayManager.get().hideAllInteractiveOverlays());
            runSafe("leave.joinMacro", DihJoinMacroController::onGameLeft);
            runSafe("leave.onGameLeft", () -> DihModule.get().onGameLeft());
            runSafe("leave.lagWatchdog", dihclient.util.DihLagWatchdog::reset);
        });
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {

            DihItemNbtSanity.scrubUnsafeTooltipLines(lines);
            DihItemNbtSanity.trimTooltipLines(lines);
            DihModule.get().appendTooltip(stack, lines);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {

            if (!dihclient.util.DihLiteVariant.enabled()) {
                dihclient.util.mm.MatchmakingManager.get().shutdownLeave();
            }

            dihclient.util.DihConfig.flushPendingSaves(2000L);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            if (!dihclient.util.DihLiteVariant.enabled()) {
                dihclient.util.mm.MatchmakingManager.get().shutdownLeave();
            }
            dihclient.util.DihConfig.flushPendingSaves(2000L);
        }, "mm-shutdown-leave"));
    }
}
