package dihclient.modules;

import dihclient.util.DihClientMessaging;
import dihclient.util.DihConfig;
import dihclient.util.DihContainerHold;
import dihclient.util.DihInputClicker;
import dihclient.util.DihInstaBreakRenderer;
import dihclient.util.DihKillAuraRotation;
import dihclient.util.DihLANSync;
import dihclient.util.DihNotifications;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPayloadChannelSubscriptionManager;
import dihclient.util.DihPayloadStudySession;
import dihclient.util.DihSharedState;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.macro.PacketGateManager;
import dihclient.util.multi.MultiManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PackHideState {
    public static final String HIDE_ID = "hide";

    private static boolean silentOverride;

    private record ActiveFlag(boolean active) {}
    private static volatile ActiveFlag activeFlag;

    private PackHideState() {
    }

    public static boolean isActive() {
        ActiveFlag flag = activeFlag;
        if (flag != null) return flag.active;
        DihConfig config = DihConfig.getGlobal();
        DihConfig.ModuleState state = config.modules.get(HIDE_ID);
        boolean active = state != null && state.enabled;
        activeFlag = new ActiveFlag(active);
        return active;
    }

    public static void publishRuntimeState(DihConfig config) {
        DihConfig.ModuleState state = config == null || config.modules == null ? null : config.modules.get(HIDE_ID);
        activeFlag = new ActiveFlag(state != null && state.enabled);
    }

    public static void refresh() {
        activeFlag = null;
    }

    public static boolean isSilenced() {
        return silentOverride || isActive();
    }

    public static boolean isHardLocked() {
        return isActive();
    }

    public static boolean shouldSuppressClientOutput() {
        return silentOverride || isHardLocked();
    }

    public static boolean isHideModule(Module module) {
        return module != null && HIDE_ID.equals(module.id());
    }

    public static boolean isHideModuleName(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return false;
        String normalized = idOrName.toLowerCase(Locale.ROOT).replace(' ', '-').replace("_", "-");

        return HIDE_ID.equals(normalized) || "panic-mode".equals(normalized) || "panicmode".equals(normalized)
            || "panic".equals(normalized);
    }

    public static boolean blocksEnable(Module module) {
        return isActive() && !isHideModule(module);
    }

    public static void enable(Module hideModule) {
        refresh();
        withSilence(() -> {
            enterHardLockCleanup();
            DihClientMessaging.clearClientMessages();
            DihConfig config = DihConfig.getGlobal();
            Set<String> enabled = new LinkedHashSet<>();
            for (Module module : ModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) enabled.add(module.id());
            }
            config.hideRestoreModules = new ArrayList<>(enabled);

            stopRuntimeWork();
            for (Module module : ModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) module.setEnabledSilently(false);
            }
            ModuleRenderUtil.refreshWorldRenderer();

            dihclient.util.DihMeteorBridge.disableAndSave(config);

            dihclient.util.DihEssentialBridge.disable(config);
            config.save();
        });
    }

    public static void disableAndRestore(Module hideModule) {
        refresh();
        withSilence(() -> {
            DihConfig config = DihConfig.getGlobal();
            List<String> restore = config.hideRestoreModules == null ? List.of() : new ArrayList<>(config.hideRestoreModules);
            config.hideRestoreModules = new ArrayList<>();
            config.save();

            for (String id : restore) {
                Module module = ModuleRegistry.get(id);
                if (module != null && !isHideModule(module)) module.setEnabledSilently(true);
            }
            ModuleRenderUtil.refreshWorldRenderer();

            dihclient.util.DihMeteorBridge.restore(config);

            dihclient.util.DihEssentialBridge.restore(config);
            config.save();

            if (config.lanSyncEnabled) {
                DihLANSync.getInstance().start();
            }
            ModuleRegistry.clearKeyStates();
            DihInputClicker.clear();
        });
    }

    public static void enforceStartupHidden() {
        refresh();
        if (!isActive()) return;
        withSilence(() -> {
            enterHardLockCleanup();
            stopRuntimeWork();
            for (Module module : ModuleRegistry.all()) {
                if (module == null || isHideModule(module)) continue;
                if (module.isEnabled()) module.setEnabledSilently(false);
            }
            ModuleRenderUtil.refreshWorldRenderer();

            dihclient.util.DihMeteorBridge.enforceHidden();

            dihclient.util.DihEssentialBridge.disable(DihConfig.getGlobal());
            DihClientMessaging.clearClientMessages();
        });
    }

    private static void enterHardLockCleanup() {
        stopRuntimeWork();
        DihNotifications.clear();
        DihOverlayManager.get().hideAllInteractiveOverlays();
        DihInstaBreakRenderer.clear();
        DihPayloadStudySession.stop();
        DihPayloadChannelSubscriptionManager.clear();
        DihClientMessaging.clearClientMessages();

        DihClientMessaging.clearChatInputHistory();

        if (!dihclient.util.DihLiteVariant.enabled()) {
            MultiManager.get().clearHistory();
        }
    }

    public static void stopRuntimeWork() {
        dihclient.util.DihNetworkCaptureState.disable();
        ModuleRegistry.clearKeyStates();
        DihInputClicker.clear();
        DihLANSync.getInstance().stopSilently();
        if (MacroExecutor.isRunning()) MacroExecutor.stop();
        DihContainerHold.clearAll();
        PacketGateManager.clearAll();

        DihBlinkManager.disableAndFlush();

        DihKillAuraRotation.reset();
        dihclient.util.macro.PingSpoofController.clearAllMacros();
        dihclient.util.macro.PingSpoofController.clearModuleOverride();
        dihclient.util.multi.PacketTeleportController.cancelAll("panic mode");

        DihSharedState shared = DihSharedState.get();
        shared.setSendGuiPackets(true);
        shared.setDelayGuiPackets(false);
        shared.setStaggeredPacketSend(false);
        shared.setCaptureMode(false);
        shared.clearQueuedPackets();

        shared.setXCarryForcedTargets(java.util.Collections.emptySet(), false);
        shared.setXCarryForced(false);
        shared.setXCarryActive(false);
        shared.setSuppressNextContainerClosePacket(false);
    }

    private static void withSilence(Runnable action) {
        boolean previous = silentOverride;
        silentOverride = true;
        try {
            action.run();
        } finally {
            silentOverride = previous;
        }
    }
}
