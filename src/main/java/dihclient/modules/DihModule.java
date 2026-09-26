package dihclient.modules;

import dihclient.DihClientAddon;
import dihclient.gui.screen.DihModuleScreen;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihConfig;
import dihclient.util.DihLANSync;
import dihclient.util.DihInputGate;
import dihclient.util.DihJoinMacroController;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.DihNotifications;
import dihclient.util.DihPayloadChannelSubscriptionManager;
import dihclient.util.DihPayloadFilterNotifier;
import dihclient.util.DihPayloadStudySession;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihPacketRegistry;
import dihclient.util.DihPerf;
import dihclient.util.DihServerInfoOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class DihModule {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final DihModule INSTANCE = new DihModule();
    private static final long PASSIVE_PAYLOAD_CAPTURE_MS = 20_000L;
    private static final int PASSIVE_PAYLOAD_RING_CAP = 96;
    private static final int PAYLOAD_FINGERPRINT_CAP = 256;
    private static final boolean PAYLOAD_TRACE = Boolean.getBoolean("dih.payload.trace");

    private static final Set<Class<?>> C2S_EXCLUDED_DEFAULTS = Set.of(
        net.minecraft.network.protocol.common.ServerboundKeepAlivePacket.class,
        net.minecraft.network.protocol.common.ServerboundPongPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.class,
        net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket.class,
        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.class,
        net.minecraft.network.protocol.game.ServerboundContainerClosePacket.class,
        dihclient.util.DihPackets.SWING,
        net.minecraft.network.protocol.game.ServerboundPlayerInputPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly.class,
        net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos.class
    );

    private DihConfig config;
    private boolean initialized;
    private boolean loadGuiKeyPressed;
    private boolean flushQueueKeyPressed;
    private boolean clearQueueKeyPressed;
    private boolean toggleLoggerKeyPressed;
    private boolean toggleSendKeyPressed;
    private boolean toggleDelayKeyPressed;
    private boolean moduleMenuKeyPressed;
    private final java.util.Map<String, Boolean> macroKeyStates = new java.util.HashMap<>();
    private List<DihMacro> cachedKeyboundMacros = List.of();
    private long cachedMacroKeybindRevision = -1L;
    private int autoSendTickCounter;
    private int packetLoggerTickCounter;
    private DihPacketLoggerOverlay packetLoggerOverlay;
    private dihclient.util.DihPayloadChannelListeners passivePayloadListeners;
    private volatile boolean payloadListenerCacheValid;
    private volatile boolean payloadListenerEnabledCache;
    private DihServerInfoOverlay serverInfoOverlay;

    private dihclient.util.IDihOverlay matchmakingOverlay;
    private dihclient.util.IDihOverlay profilesOverlay;
    private dihclient.util.IDihOverlay multiOverlay;
    private final Deque<PassivePayloadCapture> passivePayloadRing = new ArrayDeque<>(PASSIVE_PAYLOAD_RING_CAP);
    private final Set<Packet<?>> capturedPayloadPackets = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Deque<String> capturedRawPayloadFingerprints = new ArrayDeque<>(PAYLOAD_FINGERPRINT_CAP);
    private final Set<String> capturedRawPayloadFingerprintSet = new HashSet<>();
    private volatile boolean joinedPlayConnection;
    private volatile boolean spawnedInWorld;
    private long lastTickErrorLogMs;

    private int stuckActiveTicks;
    private long lastWatchdogLogMs;
    private static final int ACTIVE_WATCHDOG_TICKS = 20;
    private volatile long passivePayloadCaptureUntilMs;

    private volatile boolean autoProbePending;
    private volatile long autoProbePendingSince;
    private static final long AUTO_PROBE_CMD_GRACE_MS = 2000L;
    private static final long AUTO_PROBE_GIVE_UP_MS = 25000L;

    private DihModule() {
    }

    public static DihModule get() {
        return INSTANCE;
    }

    public void initialize() {
        if (initialized) return;

        config = DihConfig.load();
        initStep("applyRuntimeDefaults", () -> config.applyRuntimeDefaults());
        DihConfig.setGlobal(config);

        if (!dihclient.util.DihLiteVariant.enabled()) {
            initStep("themeWarm", () -> dihclient.util.DihTheme.active());
        }
        initStep("moduleRegistry", () -> ModuleRegistry.initialize(config));
        initStep("c2sPackets", () -> {
            if (config.c2sPackets.isEmpty()) config.c2sPackets = encodePackets(defaultC2SPackets());
        });
        initStep("sharedState", this::applyConfigToSharedState);
        initStep("packHide", () -> { if (PackHideState.isActive()) PackHideState.stopRuntimeWork(); });
        initStep("lanSync", () -> {
            if (config.lanSyncEnabled && !PackHideState.isActive()) DihLANSync.getInstance().start();
        });

        if (!dihclient.util.DihLiteVariant.enabled()) {
            initStep("profiles", () -> dihclient.util.DihProfileManager.get());
        }

        initialized = true;
        dihclient.util.DihNetworkCaptureState.refresh(this);
    }

    private void initStep(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            DihClientAddon.LOG.warn("[Dih] init step '{}' failed; isolated so the mod still activates", where, t);
        }
    }

    public void applyConfig(DihConfig next) {
        if (next == null) return;
        next.applyRuntimeDefaults();

        DihConfig prev = this.config != null ? this.config : DihConfig.getGlobal();
        if (prev != null) {
            next.serverPluginScans = prev.serverPluginScans;

            next.lanSyncEnabled = prev.lanSyncEnabled;
        }

        boolean themeChanged = !DihConfig.sameThemeColors(prev, next);
        boolean filtersChanged = !DihConfig.samePayloadRules(prev, next);

        java.util.Map<String, Boolean> wasEnabled = new java.util.LinkedHashMap<>();
        for (Module m : ModuleRegistry.all()) wasEnabled.put(m.id(), m.isEnabled());
        boolean panicBefore = PackHideState.isActive();
        boolean inWorld = MC != null && MC.level != null;

        this.config = next;
        DihConfig.setGlobal(next);
        next.save();

        for (Module m : ModuleRegistry.all()) {
            boolean now = m.isEnabled();
            boolean before = Boolean.TRUE.equals(wasEnabled.get(m.id()));
            if (now == before) continue;

            if (inWorld && !(now && PackHideState.blocksEnable(m))) {
                ModuleRegistry.fireEnableTransition(m, now);
            }
        }
        if (!inWorld) ModuleRegistry.clearOfflineDeferrals();

        ModuleRegistry.refreshEnabledModuleSettings();
        ModuleRegistry.markModuleEnabledChanged();

        applyConfigToSharedState();
        invalidatePayloadListenerCache(filtersChanged);
        dihclient.util.DihNetworkCaptureState.refresh(this);
        dihclient.util.DihHudManager.ensureDefaults();
        if (themeChanged) dihclient.util.DihTheme.reload();

        boolean panicNow = PackHideState.isActive();
        if (panicNow && !panicBefore) PackHideState.stopRuntimeWork();

        if (!panicNow) {
            if (next.lanSyncEnabled && !DihLANSync.getInstance().isRunning()) {
                DihLANSync.getInstance().start();
            } else if (!next.lanSyncEnabled && DihLANSync.getInstance().isRunning()) {
                DihLANSync.getInstance().stop();
            }
        }
    }

    public void tick() {
        if (!initialized || MC == null) return;

        try {
            updateWorldSpawnState();
        } catch (Throwable t) {
            logTickError("worldSpawnState", t);
        }
        try {
            tickWork();
        } catch (Throwable t) {
            logTickError("tickWork", t);
        }
    }

    private void logTickError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastTickErrorLogMs < 5000L) return;
        lastTickErrorLogMs = now;
        DihClientAddon.LOG.warn("[Dih] tick '{}' failed; isolated to protect the client", where, t);
    }

    private void logWatchdogRecovery() {
        long now = System.currentTimeMillis();
        if (now - lastWatchdogLogMs < 5000L) return;
        lastWatchdogLogMs = now;
        DihClientAddon.LOG.warn("[Dih] active-state watchdog: a live world was present but joinedPlayConnection "
            + "was false; force-restored isActive() (HUD/overlay would otherwise stay hidden until reconnect)");
    }

    private void tickWork() {
        dihclient.util.DihNetworkCaptureState.refreshIfDue(this);

        if (!dihclient.util.DihLiteVariant.enabled()) {
            dihclient.util.DihProfileManager.get().flushMirrorIfDue();
        }

        dihclient.util.macro.PingSpoofController.flushDue();
        DihBlinkManager.tick();

        dihclient.util.DihClientWake.tick(MC);

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            autoProbePending = false;
            passivePayloadCaptureUntilMs = 0L;
            DihPayloadStudySession.stop();
            DihPayloadFilterNotifier.clear();
            DihServerInfoOverlay hiddenServerInfo = getServerDataOverlayIfExists();
            if (hiddenServerInfo != null) hiddenServerInfo.stopPluginScanSilently();
        }

        DihPerf.tickJoinWindow();
        DihPayloadFilterNotifier.tick();
        if (!hidden) DihPayloadChannelSubscriptionManager.tick(MC, isPacketLoggerCapturing());
        long perf;
        DihLANSync lanSync = DihLANSync.getInstance();
        if (!hidden && lanSync.hasTickWork()) {
            perf = DihPerf.beginJoin();
            lanSync.tick();
            DihPerf.endJoinSpike("join.lanSync.tick", perf);
        }
        if (!hidden && MacroConditionRegistry.hasPendingConditions()) {
            perf = DihPerf.beginJoin();
            MacroConditionRegistry.onTick(MC);
            DihPerf.endJoinSpike("join.macroConditions.tick", perf);
        }
        DihSharedState shared = DihSharedState.get();
        DihJoinGrace.tick();
        if (!hidden && shared.hasStaggeredSendWork()) shared.tickStaggeredSend();
        if (!hidden
            && dihclient.util.DihRuntimeActivity.has(dihclient.util.DihRuntimeActivity.MODULE_TICK)) {
            perf = DihPerf.beginJoin();
            ModuleRegistry.tick();
            DihPerf.endJoinSpike("join.modules.tick", perf, 8_000_000L);
        }
        if (!hidden) PackAutoReconnectState.tickCurrentScreen();
        updatePassiveXCarryState();
        DihServerInfoOverlay serverInfo = getServerDataOverlayIfExists();
        if (!hidden && serverInfo != null && (serverInfo.isVisible() || serverInfo.shouldRenderBackgroundProbeBanner())) {
            serverInfo.tickBackground();
        }

        if (shared.shouldDelayGuiPackets() && MC.getConnection() != null) {
            if (autoSendTickCounter++ >= 1) {
                autoSendTickCounter = 0;
            }
        } else {
            autoSendTickCounter = 0;
        }

        if (DihInputGate.canRunDihKeybinds()) {
            tickKeybinds();
        } else {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
        }

        packetLoggerTickCounter++;
        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (logger != null) {
            logger.setGameTick(packetLoggerTickCounter);
        }

        if (!hidden && dihclient.api.event.AddonEvents.hasTickListeners()) {
            dihclient.api.event.AddonEvents.fireTick(MC);
        }
        publishAuxiliaryHudActivity(hidden, shared, serverInfo);
    }

    private void publishAuxiliaryHudActivity(boolean hidden, DihSharedState shared, DihServerInfoOverlay serverInfo) {
        boolean active = !hidden && (
            dihclient.util.macro.MacroExecutor.isVisibleRunning()
                || shared.shouldDelayGuiPackets()
                || shared.hasDelayedPackets()
                || shared.hasStaggeredPackets()
                || shared.isCaptureMode()
                || shared.isGBreakCapturing()
                || shared.hasCaptureCancelCallback()
                || shared.hasAttackCaptureCallback()
                || shared.hasBlockCaptureCallback()
                || shared.hasEntityCaptureCallback()
                || DihPayloadStudySession.isActive()
                || ModuleRenderUtil.has2dEspWork()
                || AntiVanishModule.shouldShowHud()
                || DihNotifications.hasVisible()
                || serverInfo != null && serverInfo.shouldRenderBackgroundProbeBanner()
        );
        dihclient.util.DihRuntimeActivity.publish(dihclient.util.DihRuntimeActivity.HUD_AUX, active);
    }

    public void onGameJoin() {
        dihclient.util.SodiumTerrainPassGuard.armForTransition();
        DihPerf.beginJoinWindow();
        long perf = DihPerf.beginJoin();
        joinedPlayConnection = true;
        spawnedInWorld = false;
        if (PackHideState.isActive()) {
            autoProbePending = false;
            passivePayloadCaptureUntilMs = 0L;
        } else {
            autoProbePending = config != null && config.autoProbePlugins;
            if (autoProbePending) autoProbePendingSince = System.currentTimeMillis();
            beginPassivePayloadCapture();
            DihPayloadChannelSubscriptionManager.requestRefresh();
        }
        applyRuntimePacketFlowDefaults();
        if (PackHideState.isActive()) PackHideState.stopRuntimeWork();

        if (!PackHideState.isActive() && config != null && config.packetLoggerCapturing) {
            getPacketLoggerOverlay();
        }

        if (config.lanSyncEnabled && !PackHideState.isActive() && !DihLANSync.getInstance().isRunning()) {
            DihLANSync.getInstance().start();
        }

        if (!PackHideState.isActive()) DihLANSync.getInstance().onGameJoined();
        if (!PackHideState.isActive()) ModuleRegistry.onGameJoin();
        if (!PackHideState.isActive()) dihclient.api.event.AddonEvents.fireGameJoin();
        DihPerf.endJoinSpike("join.onGameJoin", perf, 8_000_000L);
    }

    public void onGameLeft() {
        dihclient.util.SodiumTerrainPassGuard.armForTransition();

        ModuleWorldRenderer.dropEspMeshes();

        dihclient.util.oresim.DihOreSimEngine.clear();
        dihclient.util.oresim.DihOreSimEngine.forgetDisproven();
        joinedPlayConnection = false;
        spawnedInWorld = false;
        autoProbePending = false;
        dihclient.util.DihPluginPayloadFingerprints.clearSession();
        DihPayloadFilterNotifier.clear();
        DihPayloadChannelSubscriptionManager.clear();
        DihServerInfoOverlay leftOverlay = getServerDataOverlayIfExists();
        if (leftOverlay != null) leftOverlay.onConnectionClosed();
        passivePayloadCaptureUntilMs = 0L;
        dihclient.util.DihNetworkCaptureState.refresh(this);
        DihSharedState.get().clearRealServerVersion();
        loadGuiKeyPressed = false;
        flushQueueKeyPressed = false;
        clearQueueKeyPressed = false;
        toggleLoggerKeyPressed = false;
        toggleSendKeyPressed = false;
        toggleDelayKeyPressed = false;
        moduleMenuKeyPressed = false;
        if (!PackHideState.isActive()) ModuleRegistry.onGameLeft();
        if (!PackHideState.isActive()) dihclient.api.event.AddonEvents.fireGameLeft();
    }

    public boolean isActive() {
        return initialized && spawnedInWorld && isPlayerSpawnedInWorld();
    }

    public boolean isUsable() {
        return initialized && !PackHideState.isActive();
    }

    public boolean arePacketHooksActive() {
        if (!isActive() || PackHideState.isActive()) return false;
        DihSharedState shared = DihSharedState.get();
        return shared.hasPacketFlowWork()
            || isPacketLoggerCapturing()
            || isServerInfoPacketObservationActive()
            || ModuleRegistry.hasActivePacketEventModules()
            || dihclient.api.event.AddonEvents.hasPacketListeners()
            || dihclient.util.macro.PacketGateManager.hasActiveGates()
            || dihclient.util.macro.MacroExecutor.hasPacketObservationWork();
    }

    public PacketHookSnapshot packetHookSnapshot(boolean playConnection) {
        if (PackHideState.isActive()) return PacketHookSnapshot.inactive();
        long captureState = dihclient.util.DihNetworkCaptureState.state();
        boolean loggerCapture = dihclient.util.DihNetworkCaptureState.capturesPlaintext(captureState);
        boolean passivePayloadCapture = dihclient.util.DihNetworkCaptureState.capturesPayloads(captureState);
        if (!isActive()) {

            return (loggerCapture || passivePayloadCapture)
                ? new PacketHookSnapshot(false, passivePayloadCapture, loggerCapture, false)
                : PacketHookSnapshot.inactive();
        }
        DihSharedState shared = DihSharedState.get();

        boolean serverInfoObservation = passivePayloadCapture && isServerInfoPacketObservationActive();
        boolean normalPath = playConnection && (shared.hasPacketFlowWork()
            || loggerCapture
            || serverInfoObservation
            || ModuleRegistry.hasActivePacketEventModules()
            || dihclient.api.event.AddonEvents.hasPacketListeners()
            || dihclient.util.macro.PacketGateManager.hasActiveGates()
            || dihclient.util.macro.MacroExecutor.hasPacketObservationWork());
        if (!normalPath && !passivePayloadCapture && !loggerCapture && !serverInfoObservation) {
            return PacketHookSnapshot.inactive();
        }
        return new PacketHookSnapshot(normalPath, passivePayloadCapture, loggerCapture, serverInfoObservation);
    }

    public boolean hasPassivePayloadCaptureWork() {
        return !PackHideState.isActive() && (isPassivePayloadCaptureActive() || payloadListenersEnabledCached() || DihPayloadStudySession.isActive());
    }

    public boolean shouldCapturePacketPlaintext() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing();
    }

    public boolean shouldCapturePayloadBytes() {
        if (PackHideState.isActive()) return false;
        return isPacketLoggerCapturing() || hasPassivePayloadCaptureWork() || isServerInfoPacketObservationActive() || DihPayloadStudySession.isActive();
    }

    private boolean isServerInfoPacketObservationActive() {
        if (config != null && config.autoProbePlugins && autoProbePending) return true;
        DihServerInfoOverlay overlay = getServerDataOverlayIfExists();
        return overlay != null && overlay.isPacketObservationActive();
    }

    public boolean hasPluginDiscoveryObservationWork() {
        if (!isActive() || PackHideState.isActive()) return false;
        return isServerInfoPacketObservationActive();
    }

    private DihServerInfoOverlay getPluginDiscoveryOverlay() {
        DihServerInfoOverlay overlay = getServerDataOverlayIfExists();
        if (overlay != null) return overlay;
        if (config != null && config.autoProbePlugins && autoProbePending) {
            return getServerDataOverlay();
        }
        return null;
    }

    public void observePluginDiscoveryPacketSend(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        DihServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        overlay.onCommandSuggestionRequest(packet);
        overlay.onOutgoingCommandPacket(packet);
    }

    public void observePluginDiscoveryPacketReceive(Packet<?> packet) {
        if (!hasPluginDiscoveryObservationWork() || packet == null) return;
        DihServerInfoOverlay overlay = getPluginDiscoveryOverlay();
        if (overlay == null) return;
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket suggestions) {
            overlay.onCommandSuggestions(suggestions.id(), suggestions);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundOpenScreenPacket) {
            overlay.onOpenScreenPacket(packet);
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandsPacket) {
            if (spawnedInWorld && !autoProbePending && config != null && config.autoProbePlugins) {
                autoProbePending = true;
                autoProbePendingSince = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldObservePluginPayloadFingerprints() {
        return isPassivePayloadCaptureActive() || isServerInfoPacketObservationActive() || isPacketLoggerCapturing() || DihPayloadStudySession.isActive();
    }

    public void invalidatePayloadListenerCache() {
        invalidatePayloadListenerCache(false);
    }

    public void invalidatePayloadListenerCache(boolean refreshSubscriptions) {
        payloadListenerCacheValid = false;
        if (passivePayloadListeners != null) passivePayloadListeners.load();
        if (refreshSubscriptions) DihPayloadChannelSubscriptionManager.requestRefresh();
        dihclient.util.DihNetworkCaptureState.refresh(this);
    }

    private boolean payloadListenersEnabledCached() {
        if (payloadListenerCacheValid) return payloadListenerEnabledCache;
        boolean enabled = computePayloadListenersEnabled();
        payloadListenerEnabledCache = enabled;
        payloadListenerCacheValid = true;
        return enabled;
    }

    private boolean computePayloadListenersEnabled() {
        DihConfig cfg = DihConfig.getGlobal();
        if (cfg == null || cfg.packetLoggerPayloadFilters == null) return false;
        for (DihConfig.PayloadChannelFilterRule rule : cfg.packetLoggerPayloadFilters) {
            if (rule != null && rule.enabled) return true;
        }
        return false;
    }

    public void onConfigurationConnectionStarted() {
        beginPassivePayloadCapture();
        DihPayloadChannelSubscriptionManager.requestRefresh();
    }

    public boolean isPassivePayloadCaptureActive() {
        return System.currentTimeMillis() <= passivePayloadCaptureUntilMs;
    }

    public long passivePayloadCaptureDeadlineMs() {
        return passivePayloadCaptureUntilMs;
    }

    public boolean isPacketLoggerCapturing() {
        if (PackHideState.isActive()) return false;
        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (logger != null) return !logger.isPaused();
        return config != null && config.packetLoggerCapturing;
    }

    public boolean capturePayloadPacketForLogger(Packet<?> packet, String direction, String protocolPhase) {
        return capturePayloadPacket(packet, direction, protocolPhase, "connection");
    }

    public boolean captureDecodedPayloadPacket(Packet<?> packet, String direction, String protocolPhase, String source) {
        return capturePayloadPacket(packet, direction, protocolPhase, source == null || source.isBlank() ? "codec" : source);
    }

    public boolean captureRawPayloadFrame(byte[] frameBytes, String direction, String protocolPhase, String source) {
        if (PackHideState.isActive() || frameBytes == null || frameBytes.length == 0) return false;
        dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot =
            dihclient.util.DihPayloadSupport.snapshotFromEncodedPacketFrame(frameBytes, direction, protocolPhase);
        if (snapshot == null) return false;
        String fingerprint = rawPayloadFingerprint(snapshot);
        if (isRawPayloadCaptured(fingerprint)) return true;

        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(snapshot);
        }

        boolean loggerCapture = isPacketLoggerCapturing();
        boolean passiveWork = hasPassivePayloadCaptureWork();
        if (!loggerCapture && !passiveWork) return false;

        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        boolean captured = false;
        if (loggerCapture) {
            if (logger == null) {
                rememberPassivePayload(snapshot, direction, rawPayloadPacketClass(direction));
            } else {
                logger.logPayloadSnapshotSilently(System.currentTimeMillis(), packetLoggerTickCounter,
                    direction == null ? "" : direction, rawPayloadPacketClass(direction), snapshot);
            }
            captured = true;
        } else if (passiveWork) {
            dihclient.util.DihPayloadChannelListeners.Match filterMatch = matchEnabledPayloadFilter(snapshot.channel(), direction);
            if (filterMatch != null) {
                if (logger != null) {
                    logger.logPayloadSnapshotSilently(System.currentTimeMillis(), packetLoggerTickCounter,
                        direction == null ? "" : direction, rawPayloadPacketClass(direction), snapshot);
                } else {
                    DihPayloadFilterNotifier.onMatch(snapshot.channel(), direction, filterMatch);
                    rememberPassivePayload(snapshot, direction, rawPayloadPacketClass(direction));
                }
                captured = true;
            }
        }

        if (captured) {
            markRawPayloadCaptured(fingerprint);
            traceRawPayloadCapture(snapshot, source);
        }
        return captured;
    }

    private boolean capturePayloadPacket(Packet<?> packet, String direction, String protocolPhase, String source) {
        if (PackHideState.isActive() || packet == null) return false;
        if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
            boolean captured = false;
            for (Packet<?> child : bundledPackets(bundle)) {
                if (capturePayloadPacket(child, direction, protocolPhase, source)) captured = true;
            }
            return captured;
        }

        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            dihclient.util.DihPayloadSupport.extractPayload(packet);
        if (payload == null) return false;

        if (isPayloadPacketCaptured(packet)) return true;

        String channel = dihclient.util.DihPayloadSupport.payloadChannel(payload);
        dihclient.util.DihPayloadSupport.rememberPayloadProtocol(packet, protocolPhase);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, direction);
        }

        boolean loggerCapture = isPacketLoggerCapturing();
        boolean passiveWork = hasPassivePayloadCaptureWork();
        if (!loggerCapture && !passiveWork) return false;

        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        boolean captured = false;
        if (loggerCapture) {
            if (logger == null) {
                rememberPassivePayload(packet, direction);
            } else {
                logger.logPayloadPacketSilently(packet, direction);
            }
            captured = true;
        } else if (passiveWork) {
            dihclient.util.DihPayloadChannelListeners.Match filterMatch = matchEnabledPayloadFilter(channel, direction);
            if (filterMatch != null) {
                if (logger != null) {
                    logger.logPayloadPacketSilently(packet, direction);
                } else {
                    DihPayloadFilterNotifier.onMatch(channel, direction, filterMatch);
                    rememberPassivePayload(packet, direction);
                }
                captured = true;
            }
        }

        if (captured) {
            markPayloadPacketCaptured(packet);
            tracePayloadCapture(packet, channel, direction, protocolPhase, source);
        }
        return captured;
    }

    private boolean isPayloadPacketCaptured(Packet<?> packet) {
        synchronized (capturedPayloadPackets) {
            return capturedPayloadPackets.contains(packet);
        }
    }

    private void markPayloadPacketCaptured(Packet<?> packet) {
        if (packet == null) return;
        synchronized (capturedPayloadPackets) {
            capturedPayloadPackets.add(packet);
        }
    }

    private void tracePayloadCapture(Packet<?> packet, String channel, String direction, String protocolPhase, String source) {
        if (!PAYLOAD_TRACE) return;
        int size = -1;
        try {
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
                dihclient.util.DihPayloadSupport.extractPayload(packet);
            size = dihclient.util.DihPayloadSupport.extractPayloadBytes(payload).length;
        } catch (Throwable ignored) {  }
        DihClientAddon.LOG.info("[Dih Payload] {} {} {} {}B via {}",
            direction == null ? "" : direction,
            protocolPhase == null ? "" : protocolPhase,
            channel == null || channel.isBlank() ? "<unknown>" : channel,
            size,
            source == null ? "" : source);
    }

    private void traceRawPayloadCapture(dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot, String source) {
        if (!PAYLOAD_TRACE || snapshot == null) return;
        DihClientAddon.LOG.info("[Dih Payload] {} {} {} {}B via {}",
            snapshot.direction() == null ? "" : snapshot.direction(),
            snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase(),
            snapshot.channel() == null || snapshot.channel().isBlank() ? "<unknown>" : snapshot.channel(),
            snapshot.sizeBytes(),
            source == null ? "" : source);
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction) {
        capturePassivePayloadPacket(packet, direction, "");
    }

    public void capturePassivePayloadPacket(Packet<?> packet, String direction, String protocolPhase) {
        if (PackHideState.isActive()) return;
        if (!hasPassivePayloadCaptureWork()) return;
        capturePayloadPacket(packet, direction, protocolPhase, "passive");
    }

    private void rememberPassivePayload(Packet<?> packet, String direction) {
        long perf = DihPerf.beginJoin();
        dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot =
            dihclient.util.DihPayloadSupport.snapshot(packet, direction);
        if (snapshot == null) return;
        rememberPassivePayload(snapshot, direction, packet.getClass());
        DihPerf.endJoinSpike("join.passivePayload.snapshot", perf);
    }

    private void rememberPassivePayload(dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot, String direction, Class<?> packetClass) {
        if (snapshot == null) return;
        synchronized (passivePayloadRing) {
            while (passivePayloadRing.size() >= PASSIVE_PAYLOAD_RING_CAP) {
                passivePayloadRing.removeFirst();
            }
            passivePayloadRing.addLast(new PassivePayloadCapture(
                System.currentTimeMillis(),
                packetLoggerTickCounter,
                direction == null ? "" : direction,
                packetClass,
                snapshot
            ));
        }
    }

    private dihclient.util.DihPayloadChannelListeners.Match matchEnabledPayloadFilter(String channel, String direction) {
        if (channel == null || channel.isBlank()) return null;
        dihclient.util.DihPayloadChannelListeners listeners = getPassivePayloadListeners();
        return listeners.hasEnabledRules() ? listeners.matchChannel(channel, direction) : null;
    }

    private dihclient.util.DihPayloadChannelListeners getPassivePayloadListeners() {
        if (passivePayloadListeners == null) {
            passivePayloadListeners = new dihclient.util.DihPayloadChannelListeners();
        }
        return passivePayloadListeners;
    }

    @SuppressWarnings("unchecked")
    private static List<Packet<?>> bundledPackets(net.minecraft.network.protocol.BundlePacket<?> bundle) {
        if (bundle == null) return List.of();
        List<Packet<?>> packets = new ArrayList<>();
        try {
            for (Object child : bundle.subPackets()) {
                if (child instanceof Packet<?> packet) packets.add(packet);
            }
        } catch (Throwable ignored) {  }
        return packets;
    }

    private static Class<?> rawPayloadPacketClass(String direction) {
        return direction != null && direction.equalsIgnoreCase("C2S")
            ? net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.class
            : net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class;
    }

    private String rawPayloadFingerprint(dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return "";
        return (snapshot.direction() == null ? "" : snapshot.direction()) + '|'
            + (snapshot.protocolPhase() == null ? "" : snapshot.protocolPhase()) + '|'
            + snapshot.packetId() + '|'
            + (snapshot.channel() == null ? "" : snapshot.channel()) + '|'
            + snapshot.sizeBytes() + '|'
            + Arrays.hashCode(snapshot.rawBytes());
    }

    private boolean isRawPayloadCaptured(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return false;
        synchronized (capturedRawPayloadFingerprintSet) {
            return capturedRawPayloadFingerprintSet.contains(fingerprint);
        }
    }

    private void markRawPayloadCaptured(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return;
        synchronized (capturedRawPayloadFingerprintSet) {
            if (!capturedRawPayloadFingerprintSet.add(fingerprint)) return;
            capturedRawPayloadFingerprints.addLast(fingerprint);
            while (capturedRawPayloadFingerprints.size() > PAYLOAD_FINGERPRINT_CAP) {
                String removed = capturedRawPayloadFingerprints.removeFirst();
                capturedRawPayloadFingerprintSet.remove(removed);
            }
        }
    }

    public void toggle() {
        DihClientMessaging.sendPrefixed("Dih is always enabled in standalone mode.");
    }

    private void beginPassivePayloadCapture() {
        if (PackHideState.isActive()) return;
        passivePayloadCaptureUntilMs = Math.max(passivePayloadCaptureUntilMs, System.currentTimeMillis() + PASSIVE_PAYLOAD_CAPTURE_MS);
        dihclient.util.DihNetworkCaptureState.refresh(this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void appendTooltip(ItemStack stack, List<?> lines) {
        ModuleRegistry.appendTooltip(stack, lines);
    }

    public boolean handlePacketSend(Packet<?> packet) {
        return handlePacketSend(packet, false);
    }

    public boolean handlePacketSend(Packet<?> packet, boolean payloadLoggedEarly) {
        long perf = DihPerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketSend(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "C2S");
        }
        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (ModuleRegistry.onPacketSend(packet)) return true;
        if (dihclient.api.event.AddonEvents.firePacketSend(packet)) return true;
        if (logger == null) return false;
        if (!payloadLoggedEarly && !logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "C2S");
        }
        return false;
        } finally {
            DihPerf.endJoinSpike("join.packetSendHook", perf);
        }
    }

    public boolean handlePacketReceive(Packet<?> packet) {
        return handlePacketReceive(packet, false);
    }

    public boolean handlePacketReceive(Packet<?> packet, boolean payloadLoggedEarly) {
        long perf = DihPerf.beginJoin();
        try {
        if (PackHideState.isActive()) return false;
        observePluginDiscoveryPacketReceive(packet);
        if (shouldObservePluginPayloadFingerprints()) {
            observePluginPayloadFingerprint(packet, "S2C");
        }

        DihPacketLoggerOverlay logger = getPacketLoggerOverlayIfExists();
        if (ModuleRegistry.onPacketReceive(packet)) return true;
        dihclient.api.event.AddonEvents.firePacketReceive(packet);
        if (logger == null) return false;
        if (!payloadLoggedEarly && !logger.isPacketBlocked(packet.getClass())) {
            logger.logPacket(packet, "S2C");
        }
        return false;
        } finally {
            DihPerf.endJoinSpike("join.packetReceiveHook", perf);
        }
    }

    private void observePluginPayloadFingerprint(Packet<?> packet, String direction) {
        if (packet == null || !DihPacketLoggerOverlay.isPayloadPacket(packet)) return;
        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload =
            dihclient.util.DihPayloadSupport.extractPayload(packet);
        if (payload == null) return;
        String channel = dihclient.util.DihPayloadSupport.payloadChannel(payload);
        boolean inbound = "S2C".equalsIgnoreCase(direction);
        if (inbound) {
            dihclient.util.DihPayloadChannelSubscriptionManager.rememberObservedChannel(channel);
        }
        try {
            dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot =
                dihclient.util.DihPayloadSupport.snapshot(packet, direction);
            DihPayloadStudySession.recordPayload(snapshot);
            if (inbound) {
                dihclient.util.DihPayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
            }
        } catch (Throwable ignored) {  }
        if (!inbound || !dihclient.util.DihPluginPayloadFingerprints.shouldObserveChannel(channel)) return;

        try {
            dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot =
                dihclient.util.DihPayloadSupport.snapshot(packet, direction);
            if (snapshot == null) return;
            boolean changed = dihclient.util.DihPluginPayloadFingerprints.observe(
                currentPayloadFingerprintServerAddress(),
                currentPayloadFingerprintBrand(),
                snapshot
            );
            if (changed) {
                DihServerInfoOverlay overlay = getServerDataOverlayIfExists();
                if (overlay != null) overlay.onPayloadFingerprintUpdated();
            }
        } catch (Throwable ignored) {  }
    }

    private void observePluginPayloadFingerprint(dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot) {
        if (snapshot == null) return;
        String channel = snapshot.channel();
        DihPayloadStudySession.recordPayload(snapshot);
        boolean inbound = "S2C".equalsIgnoreCase(snapshot.direction());
        if (inbound) {
            dihclient.util.DihPayloadChannelSubscriptionManager.rememberObservedChannel(channel);
            dihclient.util.DihPayloadChannelSubscriptionManager.rememberObservedPayload(snapshot);
        }
        if (!inbound || !dihclient.util.DihPluginPayloadFingerprints.shouldObserveChannel(channel)) return;

        try {
            boolean changed = dihclient.util.DihPluginPayloadFingerprints.observe(
                currentPayloadFingerprintServerAddress(),
                currentPayloadFingerprintBrand(),
                snapshot
            );
            if (changed) {
                DihServerInfoOverlay overlay = getServerDataOverlayIfExists();
                if (overlay != null) overlay.onPayloadFingerprintUpdated();
            }
        } catch (Throwable ignored) {  }
    }

    private String currentPayloadFingerprintBrand() {
        if (MC == null || MC.getConnection() == null) return "";
        String brand = MC.getConnection().serverBrand();
        return brand == null ? "" : brand;
    }

    private String currentPayloadFingerprintServerAddress() {
        if (MC == null) return "";
        ServerData entry = MC.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return entry.ip.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
            SocketAddress address = MC.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return (host + ":" + inet.getPort()).trim().toLowerCase(java.util.Locale.ROOT);
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return raw.replaceFirst("^/", "").trim().toLowerCase(java.util.Locale.ROOT);
                }
            }
        }
        return "";
    }

    public DihPacketLoggerOverlay getPacketLoggerOverlay() {
        if (packetLoggerOverlay == null && MC != null && MC.font != null) {
            packetLoggerOverlay = new DihPacketLoggerOverlay(MC.font);
            packetLoggerOverlay.restoreState();
            hydratePassivePayloads(packetLoggerOverlay);
        }
        return packetLoggerOverlay;
    }

    public DihPacketLoggerOverlay getPacketLoggerOverlayIfExists() {
        return packetLoggerOverlay;
    }

    private void hydratePassivePayloads(DihPacketLoggerOverlay logger) {
        if (logger == null) return;
        List<PassivePayloadCapture> captures;
        synchronized (passivePayloadRing) {
            if (passivePayloadRing.isEmpty()) return;
            captures = new ArrayList<>(passivePayloadRing);
            passivePayloadRing.clear();
        }
        for (PassivePayloadCapture capture : captures) {
            logger.logPayloadSnapshotSilently(
                capture.timestampMs(),
                capture.gameTick(),
                capture.direction(),
                capture.packetClass(),
                capture.snapshot()
            );
        }
    }

    private record PassivePayloadCapture(long timestampMs, int gameTick, String direction, Class<?> packetClass,
                                         dihclient.util.DihPayloadSupport.PayloadSnapshot snapshot) {
    }

    public record PacketHookSnapshot(
        boolean normalPath,
        boolean passivePayloadCapture,
        boolean packetLoggerCapturing,
        boolean pluginDiscoveryObservation
    ) {
        private static final PacketHookSnapshot INACTIVE = new PacketHookSnapshot(false, false, false, false);

        public static PacketHookSnapshot inactive() {
            return INACTIVE;
        }
    }

    public DihServerInfoOverlay getServerDataOverlay() {
        if (serverInfoOverlay == null && MC != null && MC.font != null) {
            serverInfoOverlay = new DihServerInfoOverlay(MC.font);
            serverInfoOverlay.restoreState();
        }
        return serverInfoOverlay;
    }

    public DihServerInfoOverlay getServerDataOverlayIfExists() {
        return serverInfoOverlay;
    }

    public boolean isNoPauseOnLostFocus() {
        return config.noPauseOnLostFocus;
    }

    public boolean isLANSyncEnabled() {
        return config.lanSyncEnabled;
    }

    public boolean isBypassResourcePack() {
        return config != null ? config.pretendPackAccepted : DihConfig.getGlobal().pretendPackAccepted;
    }

    public boolean isSpoofClientVanilla() {
        return config != null ? config.spoofClientVanilla : DihConfig.getGlobal().spoofClientVanilla;
    }

    public boolean isInventoryMoveEnabled() {
        Module module = ModuleRegistry.get("inv-move");
        return module != null ? module.isEnabled() : config != null && config.inventoryMove;
    }

    public void setInventoryMoveEnabled(boolean value) {
        if (config == null) return;
        config.inventoryMove = value;
        Module module = ModuleRegistry.get("inv-move");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public boolean isXCarryEnabled() {
        Module module = xcarryModule();
        return module != null ? module.isEnabled() : config != null && config.xCarry;
    }

    public void setXCarryEnabled(boolean value) {
        if (config == null) return;
        config.xCarry = value;
        Module module = ModuleRegistry.get("xcarry");
        if (module != null && module.isEnabled() != value) module.setEnabledSilently(value);
        saveConfig();
    }

    public void setBypassResourcePack(boolean value) {
        config.pretendPackAccepted = value;
        DihSharedState.get().setBypassResourcePack(value);
        saveConfig();
    }

    public void setSpoofClientVanilla(boolean value) {
        if (config == null) return;
        config.spoofClientVanilla = value;
        saveConfig();
    }

    public boolean isForceDenyResourcePack() {
        return config != null ? config.autoDenyResourcePack : DihConfig.getGlobal().autoDenyResourcePack;
    }

    public void setForceDenyResourcePack(boolean value) {
        config.autoDenyResourcePack = value;
        DihSharedState.get().setResourcePackForceDeny(value);
        saveConfig();
    }

    public boolean useMsSleepMode() {
        return config.useMsSleepMode;
    }

    public int getMsSleepInterval() {
        return config.msSleepInterval;
    }

    public boolean useInstantExecutionMode() {
        return config.instantExecutionMode;
    }

    public int getActionDelayUs() {
        return config.actionDelayUs;
    }

    public boolean usePacketBurstMode() {
        return config.packetBurstMode;
    }

    public boolean shouldUseDirectFlush() {
        return config.useDirectFlush;
    }

    public boolean shouldForceChannelFlush() {
        return config.forceChannelFlush;
    }

    public boolean shouldFlushQueueOnDelayDisable() {
        return config != null && config.flushQueueOnDelayDisable;
    }

    public void setFlushQueueOnDelayDisable(boolean value) {
        if (config == null) return;
        config.flushQueueOnDelayDisable = value;
        saveConfig();
    }

    public boolean isCaptureAsExact() {
        return config != null && config.captureAsExact;
    }

    public void setCaptureAsExact(boolean value) {
        if (config == null) return;
        config.captureAsExact = value;
        saveConfig();
    }

    public boolean shouldUseCustomPackets() {
        return config.useCustomPackets;
    }

    public void setUseCustomPackets(boolean value) {
        config.useCustomPackets = value;
        DihSharedState.get().setUseCustomPackets(value);
        saveConfig();
    }

    public void setSendGuiPackets(boolean value) {
        config.sendGuiPackets = value;
        DihSharedState.get().setSendGuiPackets(value);
    }

    public boolean applySendGuiPacketsUiBehavior(boolean value) {
        setSendGuiPackets(value);
        saveConfig();
        return value;
    }

    public void setDelayGuiPackets(boolean value) {
        config.delayGuiPackets = value;
        DihSharedState.get().setDelayGuiPackets(value);
    }

    public String getCommandPrefix() {
        return config == null ? "" : (config.commandPrefix == null ? "" : config.commandPrefix);
    }

    public void setCommandPrefix(String prefix) {
        if (config == null) return;
        config.commandPrefix = dihclient.util.DihCompatManager.normalizeStoredCommandPrefix(prefix);
        saveConfig();
    }

    public java.util.Map<Integer, String> getCommandBinds() {
        if (config == null) return java.util.Collections.emptyMap();
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        return config.commandBinds;
    }

    public boolean hasCommandBinds() {
        return config != null && config.commandBinds != null && !config.commandBinds.isEmpty();
    }

    public void setCommandBind(int key, String command) {
        if (config == null) return;
        if (config.commandBinds == null) config.commandBinds = new java.util.LinkedHashMap<>();
        if (command == null || command.isBlank()) config.commandBinds.remove(key);
        else config.commandBinds.put(key, command);
        saveConfig();
    }

    public void clearCommandBind(int key) {
        if (config == null || config.commandBinds == null) return;
        config.commandBinds.remove(key);
        saveConfig();
    }

    private dihclient.modules.Module xcarryCached;
    private int xcarryRevision = -1;

    private dihclient.modules.Module xcarryModule() {
        int revision = dihclient.modules.ModuleRegistry.revision();
        if (revision != xcarryRevision) {
            xcarryCached = dihclient.modules.ModuleRegistry.get("xcarry");
            xcarryRevision = revision;
        }
        return xcarryCached;
    }
    public boolean isXCarryUseCrafting() {
        dihclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-crafting");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseArmor() {
        dihclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-armor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public boolean isXCarryUseOffhand() {
        dihclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("use-offhand");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryUseCrafting(boolean v) {
        dihclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-crafting", Boolean.toString(v));
    }
    public void setXCarryUseArmor(boolean v) {
        dihclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-armor", Boolean.toString(v));
    }
    public void setXCarryUseOffhand(boolean v) {
        dihclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("use-offhand", Boolean.toString(v));
    }
    public boolean isXCarryCarryCursor() {
        dihclient.modules.Module m = xcarryModule();
        if (m == null) return true;
        String v = m.value("carry-cursor");
        return v == null || v.isBlank() || Boolean.parseBoolean(v);
    }
    public void setXCarryCarryCursor(boolean v) {
        dihclient.modules.Module m = xcarryModule();
        if (m != null) m.setValue("carry-cursor", Boolean.toString(v));
    }

    public java.util.Set<Integer> getXCarryModuleSlotMask() {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        if (isXCarryUseCrafting()) { s.add(1); s.add(2); s.add(3); s.add(4); s.add(0); }
        if (isXCarryUseArmor())    { s.add(5); s.add(6); s.add(7); s.add(8); }
        if (isXCarryUseOffhand())  { s.add(45); }
        if (s.isEmpty()) { s.add(1); s.add(2); s.add(3); s.add(4); s.add(0); }
        return s;
    }

    public int applyDelayGuiPacketsUiBehavior(boolean value) {
        setDelayGuiPackets(value);
        saveConfig();

        if (!value && shouldFlushQueueOnDelayDisable() && MC != null && MC.getConnection() != null) {
            return DihSharedState.get().flushDelayedPackets(MC.getConnection());
        }

        return 0;
    }

    public int flushQueuedPacketsUiBehavior() {
        if (MC == null || MC.getConnection() == null) return 0;
        return DihSharedState.get().flushDelayedPackets(MC.getConnection());
    }

    public int clearQueuedPacketsUiBehavior() {
        DihSharedState shared = DihSharedState.get();
        int count = shared.clearQueuedPackets();
        return count;
    }

    public void notifyDelayPacketsUiResult(boolean enabled, int flushed) {
        DihSharedState shared = DihSharedState.get();
        if (enabled) {
            DihNotifications.show("Delay Packets on", 0xFF35D873);
            return;
        }

        if (flushed > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + flushed + " packet" + (flushed == 1 ? "" : "s") + ".");
                DihNotifications.show("Delay Packets off - sending " + flushed, 0xFFFF5B5B);
            } else {
                DihNotifications.show("Delay Packets off - sent " + flushed, 0xFFFF5B5B);
            }
            return;
        }

        if (!shouldFlushQueueOnDelayDisable()
            && (!shared.getDelayedPackets().isEmpty() || !shared.getStaggeredQueue().isEmpty())) {
            DihNotifications.show("Delay Packets off - queue kept", 0xFFFF5B5B);
        } else {

            DihNotifications.show("Delay Packets off", 0xFFFF5B5B);
        }
    }

    public void notifyFlushQueuedPacketsUiResult(int count) {
        DihSharedState shared = DihSharedState.get();
        if (count > 0) {
            if (shared.isStaggering()) {
                shared.setPendingQueueCompletionMessage("Sent " + count + " packet" + (count == 1 ? "" : "s") + ".");
                DihNotifications.show("Sending " + count + " packet" + (count == 1 ? "" : "s"), 0xFFFFC857);
            } else {
                DihNotifications.show("Sent " + count + " packet" + (count == 1 ? "" : "s"), 0xFF35D873);
            }
        } else {
            DihNotifications.show("Queue empty", 0xFFFF5B5B);
        }
    }

    public void notifyClearQueuedPacketsUiResult(int count) {
        DihNotifications.show(
            count > 0 ? "Cleared " + count + " packet" + (count == 1 ? "" : "s") : "Queue empty",
            count > 0 ? 0xFFFFC857 : 0xFFFF5B5B
        );
    }

    public boolean togglePacketLoggerUiBehavior() {
        DihPacketLoggerOverlay overlay = getPacketLoggerOverlay();
        if (overlay == null) return false;
        DihOverlayManager.get().register(overlay);
        overlay.toggle();
        return true;
    }

    public dihclient.util.IDihOverlay getMatchmakingOverlay() {

        if (dihclient.util.DihLiteVariant.enabled()) return null;
        if (matchmakingOverlay == null && MC != null && MC.font != null) {
            matchmakingOverlay = new dihclient.util.DihMatchmakingOverlay(MC.font);
            matchmakingOverlay.restoreLayout();
        }
        return matchmakingOverlay;
    }

    public boolean toggleMatchmakingUiBehavior() {

        if (dihclient.util.DihLiteVariant.enabled()) return false;
        dihclient.util.IDihOverlay overlay = getMatchmakingOverlay();
        if (overlay == null) return false;
        if (overlay.isVisible()) { overlay.setVisible(false); return true; }
        DihOverlayManager.get().register(overlay);
        ((dihclient.util.DihMatchmakingOverlay) overlay).openInGameInteractive();
        DihOverlayManager.get().bringToFront(overlay);

        if (MC != null) MC.gui.setScreen(new dihclient.gui.screen.DihOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public dihclient.util.IDihOverlay getProfilesOverlay() {

        if (dihclient.util.DihLiteVariant.enabled()) return null;
        if (profilesOverlay == null && MC != null && MC.font != null) {
            profilesOverlay = new dihclient.util.DihProfilesOverlay(MC.font);
            profilesOverlay.restoreLayout();
        }
        return profilesOverlay;
    }

    public dihclient.util.IDihOverlay getMultiOverlay() {

        if (dihclient.util.DihLiteVariant.enabled()) return null;
        if (multiOverlay == null && MC != null && MC.font != null) {
            multiOverlay = new dihclient.util.DihMultiOverlay(MC.font);
            multiOverlay.restoreLayout();
        }
        return multiOverlay;
    }

    public dihclient.util.IDihOverlay getMatchmakingOverlayIfExists() { return matchmakingOverlay; }
    public dihclient.util.IDihOverlay getProfilesOverlayIfExists() { return profilesOverlay; }
    public dihclient.util.IDihOverlay getMultiOverlayIfExists() { return multiOverlay; }

    public void hideMenuOverlays() {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        try { if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) matchmakingOverlay.setVisible(false); } catch (Throwable ignored) {  }
        try { if (profilesOverlay != null && profilesOverlay.isVisible()) profilesOverlay.setVisible(false); } catch (Throwable ignored) {  }

        try {
            dihclient.util.multi.MultiManager manager = dihclient.util.multi.MultiManager.getIfInitialized();
            if ((manager == null || !manager.isActive())
                && multiOverlay != null && multiOverlay.isVisible()) {
                multiOverlay.setVisible(false);
            }
        } catch (Throwable ignored) {  }
    }

    public boolean toggleMultiUiBehavior() {

        if (dihclient.util.DihLiteVariant.enabled()) return false;
        dihclient.util.IDihOverlay overlay = getMultiOverlay();
        if (overlay == null) return false;
        DihOverlayManager overlayManager = DihOverlayManager.get();
        if (overlay.isVisible() && !overlayManager.isTemporarilyHidden(overlay)) {
            overlay.setVisible(false);
            return true;
        }

        dihclient.gui.screen.DihMultiDisclaimerScreen.open(MC, MC == null ? null : MC.gui.screen(),
            this::showMultiOverlay);
        return true;
    }

    private void showMultiOverlay() {
        dihclient.util.IDihOverlay overlay = getMultiOverlay();
        if (overlay == null) return;
        DihOverlayManager manager = DihOverlayManager.get();
        manager.register(overlay);
        manager.setTemporarilyHidden(overlay, false);
        ((dihclient.util.DihMultiOverlay) overlay).openInGameInteractive();
        manager.bringToFront(overlay);
    }

    public boolean openMultiUiInGame() {

        if (dihclient.util.DihLiteVariant.enabled()) return false;
        dihclient.util.IDihOverlay overlay = getMultiOverlay();
        if (overlay == null) return false;
        showMultiOverlay();
        if (MC != null) MC.gui.setScreen(new dihclient.gui.screen.DihOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public boolean toggleProfilesUiBehavior() {

        if (dihclient.util.DihLiteVariant.enabled()) return false;
        dihclient.util.IDihOverlay overlay = getProfilesOverlay();
        if (overlay == null) return false;
        if (overlay.isVisible()) { overlay.setVisible(false); return true; }
        DihOverlayManager.get().register(overlay);
        ((dihclient.util.DihProfilesOverlay) overlay).openInGameInteractive();
        DihOverlayManager.get().bringToFront(overlay);

        if (MC != null) MC.gui.setScreen(new dihclient.gui.screen.DihOverlayHostScreen(overlay, null, false, true));
        return true;
    }

    public boolean restoreSavedScreenUiBehavior() {
        DihSharedState shared = DihSharedState.get();
        if (MC == null) return false;
        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            return false;
        }

        MC.execute(() -> {
            MC.gui.setScreen(shared.getStoredScreen());
            AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
            if (MC.player != null) MC.player.containerMenu = handler;
        });
        return true;
    }

    public Set<Class<? extends Packet<?>>> getC2SPackets() {
        return new LinkedHashSet<>(DihSharedState.get().getC2SPackets());
    }

    public Set<Class<? extends Packet<?>>> getS2CPackets() {
        return new LinkedHashSet<>(DihSharedState.get().getS2CPackets());
    }

    public void setC2SPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultC2SPackets() : new LinkedHashSet<>(packets);
        DihSharedState.get().setC2SPackets(safe);
        config.c2sPackets = encodePackets(safe);
        saveConfig();
    }

    public void setS2CPackets(Set<Class<? extends Packet<?>>> packets) {
        Set<Class<? extends Packet<?>>> safe = packets == null ? defaultS2CPackets() : new LinkedHashSet<>(packets);
        DihSharedState.get().setS2CPackets(safe);
        config.s2cPackets = encodePackets(safe);
        saveConfig();
    }

    public void resetC2SPacketsToDefault() {
        setC2SPackets(defaultC2SPackets());
    }

    public void resetS2CPacketsToDefault() {
        setS2CPackets(defaultS2CPackets());
    }

    public Set<Class<? extends Packet<?>>> defaultC2SPackets() {
        Set<Class<? extends Packet<?>>> defaults = new LinkedHashSet<>();
        for (Class<? extends Packet<?>> packetClass : DihPacketRegistry.getC2SPackets()) {
            if (!C2S_EXCLUDED_DEFAULTS.contains(packetClass)) defaults.add(packetClass);
        }

        defaults.add(net.minecraft.network.protocol.game.ServerboundChatPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket.class);
        defaults.add(net.minecraft.network.protocol.game.ServerboundSignUpdatePacket.class);
        return defaults;
    }

    public Set<Class<? extends Packet<?>>> defaultS2CPackets() {
        return new LinkedHashSet<>();
    }

    private void applyConfigToSharedState() {
        DihSharedState shared = DihSharedState.get();
        applyRuntimePacketFlowDefaults();
        shared.setUseCustomPackets(config.useCustomPackets);
        shared.setC2SPackets(resolvePackets(config.c2sPackets, true));
        shared.setS2CPackets(resolvePackets(config.s2cPackets, false));
        shared.setAllowSignEditing(config.allowSignEditing);
        shared.setResourcePackForceDeny(config.autoDenyResourcePack);
        shared.setBypassResourcePack(config.pretendPackAccepted);
        shared.setStaggeredPacketSend(config.staggeredPacketSend);
        shared.setStaggeredSendDelay(config.staggeredSendDelay);
    }

    private void applyRuntimePacketFlowDefaults() {
        if (config != null) {
            config.applyRuntimeDefaults();
        }
        DihSharedState shared = DihSharedState.get();
        shared.setSendGuiPackets(true);
        shared.setDelayGuiPackets(false);
        shared.setStaggeredPacketSend(false);
        shared.setCaptureMode(false);
    }

    private void updatePassiveXCarryState() {
        DihSharedState shared = DihSharedState.get();
        if (shared.isXCarryForced()) return;
        if (!shared.isXCarryActive() && !isXCarryEnabled()) return;
        if (PackHideState.isActive()) {
            shared.setXCarryActive(false);
            return;
        }

        boolean active = false;
        if (isXCarryEnabled() && MC.player != null && MC.player.inventoryMenu != null) {
            active = dihclient.util.macro.XCarryAction.hasStoredItems(MC.player.inventoryMenu, true);
        }

        shared.setXCarryActive(active);
    }

    private void updateWorldSpawnState() {

        if (!joinedPlayConnection && isPlayerSpawnedInWorld()) {
            if (++stuckActiveTicks >= ACTIVE_WATCHDOG_TICKS) {
                joinedPlayConnection = true;
                spawnedInWorld = true;
                stuckActiveTicks = 0;
                logWatchdogRecovery();
                return;
            }
        } else {
            stuckActiveTicks = 0;
        }

        if (!joinedPlayConnection) {
            spawnedInWorld = false;
            autoProbePending = false;
            return;
        }

        boolean wasSpawned = spawnedInWorld;
        spawnedInWorld = isPlayerSpawnedInWorld();
        if (!wasSpawned && spawnedInWorld && !PackHideState.isActive()) {
            DihJoinMacroController.onWorldReady();

            autoProbePending = true;
            autoProbePendingSince = System.currentTimeMillis();
        }
        tickAutoProbe();
    }

    private void tickAutoProbe() {
        if (!autoProbePending) return;
        long waited = System.currentTimeMillis() - autoProbePendingSince;
        if (waited > AUTO_PROBE_GIVE_UP_MS) { autoProbePending = false; return; }
        if (PackHideState.isActive() || config == null || !config.autoProbePlugins) {
            autoProbePending = false;
            return;
        }
        if (!spawnedInWorld) return;

        DihServerInfoOverlay overlay = getServerDataOverlay();
        if (overlay == null || !overlay.isAutoProbeConnectionReady()) return;

        if (!overlay.isAutoProbeContextReady() && waited < AUTO_PROBE_CMD_GRACE_MS) return;

        if (overlay.autoProbeOnSpawn()) autoProbePending = false;
    }

    private boolean isPlayerSpawnedInWorld() {
        return MC != null && MC.getConnection() != null && MC.player != null && MC.level != null;
    }

    private void saveConfig() {
        if (config == null) return;
        config.save();
    }

    private Set<Class<? extends Packet<?>>> resolvePackets(List<String> names, boolean c2s) {
        Set<Class<? extends Packet<?>>> resolved = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                Class<? extends Packet<?>> packetClass = DihPacketRegistry.getPacket(name);
                if (packetClass == null) {
                    try {
                        Class<?> direct = Class.forName(name);
                        if (Packet.class.isAssignableFrom(direct)) {
                            @SuppressWarnings("unchecked")
                            Class<? extends Packet<?>> typed = (Class<? extends Packet<?>>) direct;
                            packetClass = typed;
                        }
                    } catch (ClassNotFoundException ignored) {  }
                }
                if (packetClass != null) resolved.add(packetClass);
            }
        }

        if (resolved.isEmpty()) {
            return c2s ? defaultC2SPackets() : defaultS2CPackets();
        }

        return resolved;
    }

    private List<String> encodePackets(Set<Class<? extends Packet<?>>> packets) {
        List<String> names = new ArrayList<>();
        for (Class<? extends Packet<?>> packetClass : packets) {
            String name = DihPacketRegistry.getName(packetClass);
            names.add(name != null ? name : packetClass.getName());
        }
        return names;
    }

    private void tickKeybinds() {
        DihConfig cfg = config;
        if (cfg == null) return;
        DihSharedState shared = DihSharedState.get();
        refreshKeyboundMacroCache();

        if (cfg.keybindModuleMenu != -1) {
            boolean pressed = isBindPressed(cfg.keybindModuleMenu);

            if (pressed && !moduleMenuKeyPressed && MC.gui.screen() == null
                && !dihclient.util.DihLiteVariant.enabled()) {
                MC.gui.setScreen(new DihModuleScreen(null));
            }
            moduleMenuKeyPressed = pressed;
        }

        if (PackHideState.isActive()) {
            loadGuiKeyPressed = false;
            flushQueueKeyPressed = false;
            clearQueueKeyPressed = false;
            toggleLoggerKeyPressed = false;
            toggleSendKeyPressed = false;
            toggleDelayKeyPressed = false;
            macroKeyStates.clear();
            return;
        }

        if (cfg.keybindLoadGui != -1) {
            boolean pressed = isBindPressed(cfg.keybindLoadGui);
            if (pressed && !loadGuiKeyPressed) {
                if (restoreSavedScreenUiBehavior()) {
                    DihNotifications.show("GUI restored.", 0xFF35D873);
                } else {
                    DihNotifications.error("No stored GUI.");
                }
            }
            loadGuiKeyPressed = pressed;
        }

        if (cfg.keybindFlushQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindFlushQueue);
            if (pressed && !flushQueueKeyPressed) {
                int count = flushQueuedPacketsUiBehavior();
                notifyFlushQueuedPacketsUiResult(count);
            }
            flushQueueKeyPressed = pressed;
        }

        if (cfg.keybindClearQueue != -1) {
            boolean pressed = isBindPressed(cfg.keybindClearQueue);
            if (pressed && !clearQueueKeyPressed) {
                int count = clearQueuedPacketsUiBehavior();
                notifyClearQueuedPacketsUiResult(count);
            }
            clearQueueKeyPressed = pressed;
        }

        if (cfg.keybindToggleLogger != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleLogger);
            if (pressed && !toggleLoggerKeyPressed) {
                togglePacketLoggerUiBehavior();
            }
            toggleLoggerKeyPressed = pressed;
        }

        if (cfg.keybindToggleSend != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleSend);
            if (pressed && !toggleSendKeyPressed) {
                boolean newValue = !shared.shouldSendGuiPackets();
                applySendGuiPacketsUiBehavior(newValue);
                DihNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            toggleSendKeyPressed = pressed;
        }

        if (cfg.keybindToggleDelay != -1) {
            boolean pressed = isBindPressed(cfg.keybindToggleDelay);
            if (pressed && !toggleDelayKeyPressed) {
                boolean newValue = !shared.shouldDelayGuiPackets();
                int sent = applyDelayGuiPacketsUiBehavior(newValue);
                notifyDelayPacketsUiResult(newValue, sent);
            }
            toggleDelayKeyPressed = pressed;
        }

        for (DihMacro macro : cachedKeyboundMacros) {
            boolean pressed = isBindPressed(macro.keyCode);
            boolean wasPressed = macroKeyStates.getOrDefault(macro.name, false);
            if (pressed && !wasPressed) {
                if (dihclient.util.multi.MultiTakeoverState.isActive()) {
                    dihclient.util.multi.MultiManager multi = dihclient.util.multi.MultiManager.getIfInitialized();
                    if (multi != null && multi.isMacroPlayingOnInteractiveScope(macro.name, java.util.Set.of())) {
                        multi.stopMacroOnInteractiveScope(java.util.Set.of());
                    } else {
                        macro.execute();
                    }
                } else if (dihclient.util.macro.MacroExecutor.isMacroRunning(macro)) {
                    dihclient.util.macro.MacroExecutor.stopMacro(macro);
                } else {
                    macro.execute();
                }
            }
            macroKeyStates.put(macro.name, pressed);
        }
    }

    private void refreshKeyboundMacroCache() {
        DihMacroManager macroManager = DihMacroManager.get();
        long revision = macroManager.getRevision();
        if (revision == cachedMacroKeybindRevision) return;

        List<DihMacro> keyboundMacros = new ArrayList<>();
        Set<String> activeMacroNames = new HashSet<>();
        for (DihMacro macro : macroManager.getAll()) {
            if (macro == null || macro.keyCode == -1) continue;
            keyboundMacros.add(macro);
            activeMacroNames.add(macro.name);
        }

        macroKeyStates.keySet().removeIf(name -> !activeMacroNames.contains(name));
        cachedKeyboundMacros = keyboundMacros;
        cachedMacroKeybindRevision = revision;
    }

    private boolean isAnyTextFieldFocused() {
        return DihOverlayManager.get().isAnyTextFieldFocused();
    }

    private boolean isBindPressed(int bindCode) {
        return dihclient.util.DihBindUtil.isBindPressed(MC, bindCode);
    }

    private void restoreSavedScreen() {
        DihSharedState shared = DihSharedState.get();
        if (MC == null) return;

        if (shared.getStoredScreen() == null || shared.getStoredAbstractContainerMenu() == null) {
            DihNotifications.error("No stored GUI.");
            return;
        }

        MC.gui.setScreen(shared.getStoredScreen());
        AbstractContainerMenu handler = shared.getStoredAbstractContainerMenu();
        if (MC.player != null) MC.player.containerMenu = handler;
        DihNotifications.show("GUI restored.", 0xFF35D873);
    }
}
