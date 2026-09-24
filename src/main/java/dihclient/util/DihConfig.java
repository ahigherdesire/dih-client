package dihclient.util;

import dihclient.DihClientAddon;
import dihclient.modules.PackHideState;
import dihclient.security.DihProtector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class DihConfig implements Cloneable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DihConfig globalInstance;

    public static Runnable afterSave;

    static Consumer<DihConfig> afterPersistenceSnapshot;

    public static DihConfig getGlobal() {
        if (globalInstance == null) {
            globalInstance = load();
            DihPerf.publishConfigState(globalInstance);
            PackHideState.publishRuntimeState(globalInstance);
            DihProtector.publishRuntimeState(globalInstance);
        }
        return globalInstance;
    }

    public static void setGlobal(DihConfig config) {
        globalInstance = config;
        DihPerf.publishConfigState(config);
        PackHideState.publishRuntimeState(config);
        DihProtector.publishRuntimeState(config);
    }

    public transient boolean sendGuiPackets = true;
    public transient boolean delayGuiPackets = false;
    public boolean useCustomPackets = false;
    public List<String> c2sPackets = new ArrayList<>();
    public List<String> s2cPackets = new ArrayList<>();

    public List<String> packetLoggerBlocked = new ArrayList<>();
    public boolean packetLoggerBlockedInit = false;
    public int packetLoggerBlockedDefaultsVersion = 0;
    public List<PayloadChannelFilterRule> packetLoggerPayloadFilters = new ArrayList<>();
    public List<PayloadChannelRegistrationRule> packetLoggerPayloadRegistrations = new ArrayList<>();
    @Deprecated
    public List<PayloadChannelListenerRule> packetLoggerPayloadListeners = new ArrayList<>();
    public boolean payloadRegistrationUnlocked = false;
    public int payloadRegistrationWarningAcceptedVersion = 0;
    public boolean packetLoggerCapturing = false;
    public boolean allowSignEditing = true;
    public boolean autoDenyResourcePack = false;
    public boolean pretendPackAccepted = false;

    public int packResponseDelayMs = 20000;
    public boolean resourcePackChoiceInitialized = false;
    public boolean spoofClientVanilla = true;

    public boolean protectorEnabled = true;
    public boolean protectorSpoofBrand = true;
    public boolean protectorFilterChannels = true;
    public boolean protectorTranslationProtection = true;
    public boolean protectorDisableTelemetry = true;

    public boolean protectorBlockLocalUrls = true;

    public boolean protectorIsolatePackCache = true;

    public boolean protectorStripServerPacks = false;

    public boolean protectorChatSigningOff = false;
    public boolean performanceDebug = false;
    public boolean inventoryMove = false;
    public boolean xCarry = true;
    public boolean noPauseOnLostFocus = true;
    public boolean showItemIds = true;

    public boolean autoProbePlugins = false;

    public transient Map<String, PluginScanCacheEntry> serverPluginScans = new LinkedHashMap<>();
    public boolean lanSyncEnabled = true;
    public boolean staggeredPacketSend = false;
    public int staggeredSendDelay = 1;
    public String executionPreset = "DEFAULT";
    public boolean packetBurstMode = true;
    public boolean useMsSleepMode = false;
    public int msSleepInterval = 5;
    public boolean instantExecutionMode = true;
    public int actionDelayUs = 0;
    public boolean useDirectFlush = true;
    public boolean forceChannelFlush = true;
    public boolean flushQueueOnDelayDisable = true;
    public boolean captureAsExact = false;
    public String commandPrefix = "";
    public boolean joinMacroEnabled = false;
    public String joinMacroName = "";
    public String joinMacroTiming = "WORLD";
    public String joinMacroTriggerJoin = "FIRST";
    public boolean joinMacroKeepEnabled = false;

    public boolean stopMacroOnLeave = true;

    public Map<String, String> joinMacroFormValues = new LinkedHashMap<>();
    public Map<Integer, String> commandBinds = new LinkedHashMap<>();

    public Map<String, ModuleState> modules = new LinkedHashMap<>();
    public List<String> hideRestoreModules = new ArrayList<>();
    public Map<String, ModuleCategoryLayout> moduleCategoryLayouts = new LinkedHashMap<>();
    public Map<String, List<String>> moduleCategoryOrder = new LinkedHashMap<>();
    public Map<String, HudElementState> hudElements = new LinkedHashMap<>();
    public boolean hudLayoutMigrated = false;

    public boolean hudLayoutNormalizedV2 = false;
    public int hudSnapRange = 10;
    public int hudEdgePadding = 4;
    public boolean hudEditorGrid = true;

    public int keybindLoadGui = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
    public int keybindModuleMenu = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    public int keybindFlushQueue = -1;
    public int keybindClearQueue = -1;
    public int keybindToggleLogger = -1;
    public int keybindToggleSend = -1;
    public int keybindToggleDelay = -1;

    public boolean keybindInsideGui = false;

    public boolean customMainMenu = true;

    public boolean infiniChat = true;
    public double overlayScale = 1.0;

    public int tpMaxPackets = 20;
    public int tpPauseMs = 500;

    /** Draw DIH's own screens and HUD with the Geist UI font instead of the pixel font. */
    public boolean modernFont = true;

    public boolean voiceChatModdedPromptShown = false;

    public boolean multiDisclaimerAccepted = false;

    public boolean multiShowTooltips = true;

    public boolean multiAutoSolveCaptcha = true;

    public int multiMacroStartDelayMs = 0;

    public boolean accountGenSetPassword = true;

    public String accountGenPasswordMode = "Generate";

    public String accountGenSharedPassword = "";

    public String activeProfileId = "";

    public List<String> hideRestoreMeteorModules = new ArrayList<>();

    public boolean hideMeteorHudActive = true;

    public boolean essentialHiddenByPanic = false;

    public boolean essentialSavedEnabled = true;

    public List<String> disabledAddonIds = new ArrayList<>();

    public ThemeColors themeColors = new ThemeColors();

    public static final class ThemeColors {

        public boolean advanced = false;

        // DIH Client default identity: blue scheme. The accent family is blue;
        // text stays warm-neutral, success stays green, danger stays red (semantic cues).
        public int master = 0xFF3B6EFF;
        public int accent = 0xFF3B6EFF;
        public int outline = 0xFF2B50B3;
        public int text = 0xFFF3ECE7;
        public int toggle = 0xFF3B6EFF;
        public int backdrop = 0xFF4863B2;
        public int success = 0xFF35D873;
        public int danger = 0xFFE26A6A;
        public int button = 0xFF1F368F;
        public int header = 0xFF3B6EFF;
        public int hover = 0xFF6482FF;
    }

    public static final class ModuleState {
        public boolean enabled = false;
        public int keybind = -1;
        public Map<String, String> settings = new LinkedHashMap<>();
    }

    public static final class ModuleCategoryLayout {
        public int x = -1;
        public int y = -1;
        public boolean collapsed = false;

        public int visibleRows = 0;
    }

    public static final class HudElementState {
        public boolean enabled = true;
        public int x = 8;
        public int y = 8;
        public double scale = 1.0;
        public String anchor = "TOP_LEFT";
        public Map<String, String> settings = new LinkedHashMap<>();
    }

    public static class PayloadChannelFilterRule {
        public String label = "";
        public String pattern = "";
        public boolean enabled = true;
        public boolean preset = false;
    }

    public static class PayloadChannelRegistrationRule {
        public String label = "";
        public String channel = "";
        public boolean enabled = true;
        public String source = "custom";
    }

    @Deprecated
    public static final class PayloadChannelListenerRule extends PayloadChannelFilterRule {
        public String direction = "ANY";
    }

    public static DihConfig load() {
        File file = configFile();
        DihConfig config = null;
        if (file.exists()) {
            config = tryRead(file);
            if (config == null) {

                backupCorruptConfig();
                File backup = backupFile();
                if (backup.exists()) {
                    config = tryRead(backup);
                    if (config != null) {
                        DihClientAddon.LOG.warn("Dih config was corrupt; recovered from backup {}", backup.getName());
                    }
                }
            }
        }
        if (config == null) {
            config = new DihConfig();
        }

        Map<String, PluginScanCacheEntry> legacyScans = config.serverPluginScans;
        ServerPluginScanCache cache = ServerPluginScanCache.get();
        boolean migrated = cache.mergeLegacy(legacyScans);
        config.serverPluginScans = cache.sharedView();
        config.applyRuntimeDefaults();
        if (migrated) config.save();
        return config;
    }

    private static DihConfig tryRead(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            DihConfig loaded = GSON.fromJson(root, DihConfig.class);
            if (loaded != null && root instanceof JsonObject object && object.has("serverPluginScans")) {
                Map<String, PluginScanCacheEntry> legacy = GSON.fromJson(object.get("serverPluginScans"),
                    new TypeToken<Map<String, PluginScanCacheEntry>>() { }.getType());
                loaded.serverPluginScans = legacy == null ? new LinkedHashMap<>() : legacy;
            }
            return loaded != null ? loaded : new DihConfig();
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to read Dih config from {}", file.getName(), t);
            return null;
        }
    }

    private static File backupFile() {
        return new File(configFile().getParentFile(), "config.json.bak");
    }

    static File configFile() {
        return new File(DihClientAddon.FOLDER, "config.json");
    }

    private static void backupCorruptConfig() {
        File file = configFile();
        try {
            File corrupt = new File(file.getParentFile(), "config.json.corrupt-" + System.currentTimeMillis());
            java.nio.file.Files.move(file.toPath(), corrupt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            DihClientAddon.LOG.error("Dih config was corrupt; moved it to {}", corrupt.getName());
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to set aside corrupt Dih config", t);
        }
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        DihPerf.publishConfigState(this);
        PackHideState.publishRuntimeState(this);
        DihProtector.publishRuntimeState(this);
        DihConfigWriter.request(this);

        Runnable hook = afterSave;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable t) {
                DihClientAddon.LOG.error("Dih config afterSave hook failed", t);
            }
        }
    }

    public static void enqueuePendingSaveNow() {
        DihConfigWriter.capturePendingNow();

        if (!DihLiteVariant.enabled()) DihProfileManager.flushPendingMirrorIfInitialized();
    }

    public static void flushPendingSaves(long timeoutMs) {
        enqueuePendingSaveNow();
        DihConfigWriter.flushBlocking(timeoutMs);
    }

    static String toJson(DihConfig config) {
        return toJson(config, ServerPluginScanCache.get());
    }

    static String toJson(DihConfig config, ServerPluginScanCache pluginScanCache) {
        Map<String, PluginScanCacheEntry> fallback = pluginScanCache.pendingLegacyFallback();
        if (fallback.isEmpty()) return GSON.toJson(config);
        JsonObject root = GSON.toJsonTree(config).getAsJsonObject();
        root.add("serverPluginScans", GSON.toJsonTree(fallback));
        return GSON.toJson(root);
    }

    static void onPersistenceSnapshot(DihConfig snapshot) {
        Consumer<DihConfig> hook = afterPersistenceSnapshot;
        if (hook == null) return;
        try {
            hook.accept(snapshot);
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Dih config snapshot hook failed", t);
        }
    }

    static void writeToDisk(String json) {
        synchronized (SAVE_LOCK) {
            long perfStart = DihPerf.begin();
            File file = configFile();
            file.getParentFile().mkdirs();

            File tmp = new File(file.getParentFile(), "config.json.tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(json);
            } catch (Throwable t) {
                DihClientAddon.LOG.error("Failed to write Dih config", t);
                tmp.delete();
                return;
            }

            try {
                if (file.exists()) {
                    java.nio.file.Files.copy(file.toPath(), backupFile().toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                DihClientAddon.LOG.warn("Failed to back up Dih config before save", t);
            }

            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomicFailed) {

                try {
                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable t) {
                    DihClientAddon.LOG.error("Failed to swap in Dih config", t);
                }
            }
            DihPerf.endSpike("config.write", perfStart, 100_000_000L);
        }
    }

    public DihConfig deepCopy() {
        return DihConfigSnapshot.copyOf(this);
    }

    public DihConfig snapshotForProfile() {
        return stripMachineStateForProfile(deepCopy());
    }

    static DihConfig profileSnapshotFromDetached(DihConfig detached) {
        if (detached == null) return stripMachineStateForProfile(new DihConfig());
        return stripMachineStateForProfile(detached.shallowDetachedCopy());
    }

    private DihConfig shallowDetachedCopy() {
        try {
            return (DihConfig) super.clone();
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static DihConfig stripMachineStateForProfile(DihConfig copy) {
        copy.activeProfileId = "";
        copy.packetLoggerCapturing = false;
        copy.serverPluginScans = new LinkedHashMap<>();
        copy.essentialHiddenByPanic = false;
        return copy;
    }

    public static boolean sameThemeColors(DihConfig a, DihConfig b) {
        if (a == null || b == null) return a == b;
        return GSON.toJson(a.themeColors).equals(GSON.toJson(b.themeColors));
    }

    public static boolean samePayloadRules(DihConfig a, DihConfig b) {
        if (a == null || b == null) return a == b;
        return GSON.toJson(a.packetLoggerPayloadFilters).equals(GSON.toJson(b.packetLoggerPayloadFilters))
            && GSON.toJson(a.packetLoggerPayloadRegistrations).equals(GSON.toJson(b.packetLoggerPayloadRegistrations));
    }

    public void applyRuntimeDefaults() {
        sendGuiPackets = true;
        delayGuiPackets = false;
        staggeredPacketSend = false;
        if (c2sPackets == null) c2sPackets = new ArrayList<>();
        if (s2cPackets == null) s2cPackets = new ArrayList<>();
        if (packetLoggerPayloadFilters == null) packetLoggerPayloadFilters = new ArrayList<>();
        if (packetLoggerPayloadRegistrations == null) packetLoggerPayloadRegistrations = new ArrayList<>();
        if (packetLoggerPayloadListeners == null) packetLoggerPayloadListeners = new ArrayList<>();
        if (packetLoggerPayloadFilters.isEmpty() && !packetLoggerPayloadListeners.isEmpty()) {
            for (PayloadChannelListenerRule oldRule : packetLoggerPayloadListeners) {
                if (oldRule == null) continue;
                PayloadChannelFilterRule rule = new PayloadChannelFilterRule();
                rule.label = oldRule.label;
                rule.pattern = oldRule.pattern;
                rule.enabled = oldRule.enabled;
                rule.preset = oldRule.preset;
                packetLoggerPayloadFilters.add(rule);
            }
        }
        if (!packetLoggerPayloadListeners.isEmpty()) {
            packetLoggerPayloadListeners = new ArrayList<>();
        }
        if (modules == null) modules = new LinkedHashMap<>();
        if (hideRestoreModules == null) hideRestoreModules = new ArrayList<>();
        if (hideRestoreMeteorModules == null) hideRestoreMeteorModules = new ArrayList<>();
        if (moduleCategoryLayouts == null) moduleCategoryLayouts = new LinkedHashMap<>();
        if (moduleCategoryOrder == null) moduleCategoryOrder = new LinkedHashMap<>();
        if (hudElements == null) hudElements = new LinkedHashMap<>();
        if (serverPluginScans == null) serverPluginScans = new LinkedHashMap<>();
        if (disabledAddonIds == null) disabledAddonIds = new ArrayList<>();
        if (themeColors == null) themeColors = new ThemeColors();
        overlayScale = DihUiScale.nearestAllowedOverlayScale(overlayScale);
        tpMaxPackets = Math.max(1, Math.min(100, tpMaxPackets));
        tpPauseMs = Math.max(50, Math.min(10_000, tpPauseMs));
        if (hudSnapRange <= 0) hudSnapRange = 10;
        if (hudEdgePadding < 0) hudEdgePadding = 4;
        commandPrefix = DihCompatManager.normalizeStoredCommandPrefix(commandPrefix);
        if (!resourcePackChoiceInitialized) {
            pretendPackAccepted = false;
            resourcePackChoiceInitialized = true;
        }
        for (HudElementState state : hudElements.values()) {
            if (state != null && state.settings == null) state.settings = new LinkedHashMap<>();
        }
    }

    private static String pluginScanKey(String address, String contextSignature) {
        String a = address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT);
        String c = contextSignature == null ? "" : contextSignature.trim().toLowerCase(java.util.Locale.ROOT);
        return c.isEmpty() ? a : a + "|" + Integer.toHexString(c.hashCode());
    }

    public PluginScanCacheEntry getPluginScan(String address, String contextSignature) {
        if (address == null || address.isBlank()) return null;
        return ServerPluginScanCache.get().get(pluginScanKey(address, contextSignature));
    }

    public long getPluginScanTimestamp(String address, String contextSignature) {
        PluginScanCacheEntry e = getPluginScan(address, contextSignature);
        return e == null ? 0L : e.scannedAtMs;
    }

    public PluginScanCacheEntry getPluginScanByAddress(String address) {
        if (address == null || address.isBlank()) return null;
        return ServerPluginScanCache.get().newestForAddress(
            address.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public long getPluginScanTimestampByAddress(String address) {
        PluginScanCacheEntry e = getPluginScanByAddress(address);
        return e == null ? 0L : e.scannedAtMs;
    }

    public Map<String, PluginScanCacheEntry> allPluginScans() {
        return ServerPluginScanCache.get().snapshot();
    }

    public long pluginScanRevision() {
        return ServerPluginScanCache.get().revision();
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, Map.of(), Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, guis, confidence, Map.of(), Map.of());
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence, Map<String, String> copyEvidence,
                              Map<String, List<String>> copyCommands) {
        putPluginScan(address, contextSignature, plugins, commands, evidence, channels, guis, confidence,
            copyEvidence, copyCommands, "COMPLETE", 0, 0, 0, 0, "", "");
    }

    public void putPluginScan(String address, String contextSignature, List<String> plugins,
                              Map<String, List<String>> commands, Map<String, String> evidence,
                              Map<String, List<String>> channels, Map<String, List<String>> guis,
                              Map<String, String> confidence, Map<String, String> copyEvidence,
                              Map<String, List<String>> copyCommands, String scanStatus,
                              int totalProbes, int answeredProbes, int retriedProbes, int failedProbes,
                              String serverName, String serverAddress) {
        if (address == null || address.isBlank()) return;
        PluginScanCacheEntry e = new PluginScanCacheEntry();
        e.contextSignature = contextSignature == null ? "" : contextSignature;
        e.serverName = serverName == null ? "" : serverName;
        e.serverAddress = serverAddress == null || serverAddress.isBlank() ? address : serverAddress;
        e.plugins = plugins == null ? new ArrayList<>() : new ArrayList<>(plugins);
        e.commands = commands == null ? new LinkedHashMap<>() : new LinkedHashMap<>(commands);
        e.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
        e.channels = channels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(channels);
        e.guis = guis == null ? new LinkedHashMap<>() : new LinkedHashMap<>(guis);
        e.confidence = confidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(confidence);
        e.copyEvidence = copyEvidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(copyEvidence);
        e.copyCommands = copyCommands == null ? new LinkedHashMap<>() : new LinkedHashMap<>(copyCommands);
        e.scanStatus = scanStatus == null || scanStatus.isBlank() ? "COMPLETE" : scanStatus;
        e.totalProbes = Math.max(0, totalProbes);
        e.answeredProbes = Math.max(0, answeredProbes);
        e.retriedProbes = Math.max(0, retriedProbes);
        e.failedProbes = Math.max(0, failedProbes);
        e.scannedAtMs = System.currentTimeMillis();
        ServerPluginScanCache.get().put(pluginScanKey(address, contextSignature), e);
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public void removePluginScan(String address, String contextSignature) {
        if (address == null) return;
        ServerPluginScanCache.get().remove(pluginScanKey(address, contextSignature));
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public void removePluginScan(String address) {
        if (address == null) return;
        String a = pluginScanKey(address, "");
        ServerPluginScanCache.get().removeAddress(a);
        serverPluginScans = ServerPluginScanCache.get().sharedView();
    }

    public static final class PluginScanCacheEntry {
        public String contextSignature = "";

        public String serverName = "";

        public String serverAddress = "";
        public List<String> plugins = new ArrayList<>();
        public Map<String, List<String>> commands = new LinkedHashMap<>();
        public Map<String, String> evidence = new LinkedHashMap<>();
        public Map<String, List<String>> channels = new LinkedHashMap<>();
        public Map<String, List<String>> guis = new LinkedHashMap<>();
        public Map<String, String> confidence = new LinkedHashMap<>();
        public Map<String, String> copyEvidence = new LinkedHashMap<>();
        public Map<String, List<String>> copyCommands = new LinkedHashMap<>();
        public String scanStatus = "COMPLETE";
        public int totalProbes = 0;
        public int answeredProbes = 0;
        public int retriedProbes = 0;
        public int failedProbes = 0;
        public long scannedAtMs = 0L;
    }
}
