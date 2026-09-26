package dihclient.mixin;

import dihclient.ducks.DihExternalButtonScreen;
import dihclient.gui.screen.DihAccountsScreen;
import dihclient.gui.screen.DihJoinMacroScreen;
import dihclient.gui.screen.DihMultiConsoleScreen;
import dihclient.gui.screen.DihMultiDisclaimerScreen;
import dihclient.gui.screen.DihMultiScreen;
import dihclient.gui.screen.DihProxiesScreen;
import dihclient.gui.screen.DihVoiceChatPromptScreen;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.util.DihConfig;
import dihclient.util.DihProxy;
import dihclient.util.DihProxyManager;
import dihclient.util.DihJoinMacroController;
import dihclient.util.DihMacroManager;
import dihclient.util.multi.MultiManager;
import java.util.Locale;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinMultiplayerScreen.class, priority = 2000)
public abstract class DihMultiplayerScreenMixin extends Screen implements DihExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 60;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int MULTI_BUTTON_WIDTH = 50;
    @Unique private static final int STACK_WIDTH = 104;
    @Unique private static final int MARGIN = 4;
    @Unique private static final int GAP = 3;
    @Unique private static final int EXTERNAL_NONE = 0;
    @Unique private static final int EXTERNAL_VIA_FABRIC_PLUS = 1;
    @Unique private static final int EXTERNAL_REPLAY_RECORD = 2;
    @Unique private static final int EXTERNAL_OPSEC = 3;

    @Unique private Button dih$accountsButton;
    @Unique private Button dih$joinMacroButton;
    @Unique private Button dih$multiButton;
    @Unique private Button dih$proxiesButton;
    @Unique private Button dih$spoofButton;
    @Unique private Button dih$packsButton;
    @Unique private Button dih$libraryButton;
    @Unique private Button dih$recordButton;
    @Unique private boolean dih$voicePromptChecked;
    @Unique private boolean dih$meteorUiConfigSuppressed;
    @Unique private int dih$topButtonsLeft = MARGIN;

    @Shadow protected ServerSelectionList serverSelectionList;

    protected DihMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void dih$repositionElements(CallbackInfo ci) {
        dih$layoutButtons();
        dih$maybeShowVoiceChatPrompt();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void dih$refreshMultiButton(CallbackInfo ci) {
        if (dih$multiButton == null) return;
        String label = dih$multiLabel();
        if (!label.equals(dih$multiButton.getMessage().getString())) {
            dih$multiButton.setMessage(Component.literal(label));
        }
    }

    @Unique
    private static String dih$multiLabel() {
        MultiManager manager = MultiManager.get();
        return manager.isActive() ? "Multi " + manager.readyFraction() : "Multi";
    }

    @Unique
    private void dih$maybeShowVoiceChatPrompt() {
        if (dih$voicePromptChecked) return;
        dih$voicePromptChecked = true;
        if (PackHideState.isActive()) return;
        if (!dihclient.platform.DihLoader.isModLoaded("voicechat")) return;
        DihModule module = DihModule.get();
        if (module == null || !module.isSpoofClientVanilla()) return;
        DihConfig config = DihConfig.getGlobal();
        if (config == null || config.voiceChatModdedPromptShown) return;
        config.voiceChatModdedPromptShown = true;
        config.save();
        Screen parent = this;

        this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() == parent) {
                this.minecraft.gui.setScreen(new DihVoiceChatPromptScreen(parent));
            }
        });
    }

    @Unique
    private void dih$layoutButtons() {
        dih$suppressMeteorWidgets();
        AbstractWidget via = null;
        AbstractWidget opsec = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || dih$isOwned(widget)) continue;
            int kind = dih$externalKind(widget);
            if (kind == EXTERNAL_REPLAY_RECORD) {
                dih$setVisible(widget, false);
            } else if (kind == EXTERNAL_VIA_FABRIC_PLUS) {
                via = widget;
            } else if (kind == EXTERNAL_OPSEC) {
                opsec = widget;
            }
        }

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            dih$setVisible(dih$accountsButton, false);
            dih$setVisible(dih$joinMacroButton, false);
            dih$setVisible(dih$multiButton, false);
            dih$setVisible(dih$proxiesButton, false);
            dih$setVisible(dih$spoofButton, false);
            dih$setVisible(dih$packsButton, false);
            dih$setVisible(dih$libraryButton, false);
            dih$setVisible(dih$recordButton, false);
            dih$setVisible(via, false);
            dih$setVisible(opsec, false);
            return;
        }

        dih$ensureOwnedButtons();
        int topRight = this.width - MARGIN;
        int topAvailable = Math.max(4, this.width - MARGIN * 2);

        boolean lite = dihclient.util.DihLiteVariant.enabled();
        int topCell = lite ? Math.max(1, (topAvailable - GAP * 3) / 4)
            : Math.max(1, (topAvailable - GAP * 4) / 5);
        int accountsW = Math.min(BUTTON_WIDTH, topCell);
        int proxiesW = Math.min(BUTTON_WIDTH, topCell);
        int libraryW = Math.min(BUTTON_WIDTH, topCell);
        int macroW = Math.min(MACRO_BUTTON_WIDTH, topCell);
        int multiW = Math.min(MULTI_BUTTON_WIDTH, topCell);
        int cursor = topRight;
        cursor -= accountsW;
        dih$place(dih$accountsButton, cursor, MARGIN, accountsW, BUTTON_HEIGHT);
        cursor -= GAP + proxiesW;
        dih$place(dih$proxiesButton, cursor, MARGIN, proxiesW, BUTTON_HEIGHT);
        cursor -= GAP + libraryW;
        dih$libraryButton.setMessage(dih$fitLabel(libraryW, "Library"));
        dih$place(dih$libraryButton, cursor, MARGIN, libraryW, BUTTON_HEIGHT);
        cursor -= GAP + macroW;
        dih$place(dih$joinMacroButton, cursor, MARGIN, macroW, BUTTON_HEIGHT);
        if (!lite) {
            cursor -= GAP + multiW;
            dih$place(dih$multiButton, cursor, MARGIN, multiW, BUTTON_HEIGHT);
            String multiLabel = dih$multiLabel();
            if (!multiLabel.equals(dih$multiButton.getMessage().getString())) {
                dih$multiButton.setMessage(Component.literal(multiLabel));
            }
        }
        dih$topButtonsLeft = Math.max(MARGIN, cursor);

        int footerRight = (this.width / 2) - 154 - GAP;
        int footerWidth = Math.max(60, Math.min(STACK_WIDTH, footerRight - MARGIN));
        int footerX = Math.max(MARGIN, footerRight - footerWidth);
        int footerY = Math.max(MARGIN, this.height - (BUTTON_HEIGHT * 2 + GAP + 8));
        dih$spoofButton.setMessage(dih$spoofClientLabel(footerWidth));
        dih$packsButton.setMessage(dih$bypassPacksLabel(footerWidth));
        dih$place(dih$spoofButton, footerX, footerY, footerWidth, BUTTON_HEIGHT);
        dih$place(dih$packsButton, footerX, footerY + BUTTON_HEIGHT + GAP, footerWidth, BUTTON_HEIGHT);

        int rightX = Math.min(this.width - MARGIN - STACK_WIDTH, (this.width / 2) + 154 + GAP);
        int rightWidth = Math.max(60, Math.min(STACK_WIDTH, this.width - MARGIN - rightX));
        int count = (dihclient.platform.DihLoader.isModLoaded("replaymod") ? 1 : 0) + (via != null ? 1 : 0) + (opsec != null ? 1 : 0);
        int rightY = Math.max(MARGIN, this.height - 8 - Math.max(0, count * BUTTON_HEIGHT + Math.max(0, count - 1) * GAP));
        int slot = 0;

        if (dihclient.platform.DihLoader.isModLoaded("replaymod")) {
            dih$recordButton.setMessage(dih$replayServerLabel(rightWidth));
            dih$place(dih$recordButton, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        } else {
            dih$setVisible(dih$recordButton, false);
        }
        if (via != null) {
            dih$place(via, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
        if (opsec != null) {
            dih$place(opsec, rightX, rightY + slot * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
    }

    @Unique
    private void dih$ensureOwnedButtons() {
        if (dih$accountsButton == null) {
            dih$accountsButton = this.addRenderableWidget(Button.builder(Component.literal("Accounts"),
                ignored -> this.minecraft.gui.setScreen(new dihclient.gui.screen.DihAccountsScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$joinMacroButton == null) {
            dih$joinMacroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.gui.setScreen(new DihJoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$multiButton == null && !dihclient.util.DihLiteVariant.enabled()) {

            dih$multiButton = this.addRenderableWidget(Button.builder(Component.literal("Multi"),
                ignored -> dih$openMulti()).bounds(0, 0, MULTI_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$proxiesButton == null) {
            dih$proxiesButton = this.addRenderableWidget(Button.builder(Component.literal("Proxies"),
                ignored -> this.minecraft.gui.setScreen(new dihclient.gui.screen.DihProxiesScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$spoofButton == null) {
            dih$spoofButton = this.addRenderableWidget(Button.builder(Component.literal("Client"),
                ignored -> {
                    DihModule module = DihModule.get();
                    if (module != null) module.setSpoofClientVanilla(!module.isSpoofClientVanilla());
                    dih$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$packsButton == null) {
            dih$packsButton = this.addRenderableWidget(Button.builder(Component.literal("Packs"),
                ignored -> {
                    DihModule module = DihModule.get();
                    if (module != null) module.setBypassResourcePack(!module.isBypassResourcePack());
                    dih$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$libraryButton == null) {
            dih$libraryButton = this.addRenderableWidget(Button.builder(Component.literal("Library"),
                ignored -> dih$openPluginLibrary()).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (dih$recordButton == null) {
            dih$recordButton = this.addRenderableWidget(Button.builder(Component.literal("Replay"),
                ignored -> {
                    dih$toggleReplayServerRecording();
                    dih$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
    }

    @Unique
    private void dih$openPluginLibrary() {
        this.minecraft.gui.setScreen(new dihclient.gui.screen.DihPluginLibraryScreen(this));
    }

    @Unique
    private void dih$openMulti() {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        net.minecraft.client.multiplayer.ServerData selected = dih$selectedServerData();
        String address = selected == null ? "" : selected.ip;
        Runnable proceed = () -> {
            if (MultiManager.get().isActive()) {
                this.minecraft.gui.setScreen(new DihMultiConsoleScreen(this));
            } else {
                MultiManager.get().rememberSelectedServer(selected);
                this.minecraft.gui.setScreen(new DihMultiScreen(this, address));
            }
        };
        DihMultiDisclaimerScreen.open(this.minecraft, this, proceed);
    }

    @Unique
    private boolean dih$isOwned(AbstractWidget widget) {
        return widget == dih$accountsButton || widget == dih$joinMacroButton || widget == dih$multiButton
            || widget == dih$proxiesButton || widget == dih$spoofButton
            || widget == dih$packsButton || widget == dih$libraryButton || widget == dih$recordButton;
    }

    @Unique
    private net.minecraft.client.multiplayer.ServerData dih$selectedServerData() {
        if (serverSelectionList == null) return null;
        ServerSelectionList.Entry selected = serverSelectionList.getSelected();
        if (selected instanceof ServerSelectionList.OnlineServerEntry online) {
            return online.getServerData();
        }
        return null;
    }

    @Unique
    private static void dih$place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget == null) return;
        widget.setX(Math.max(MARGIN, x));
        widget.setY(Math.max(MARGIN, y));
        widget.setSize(Math.max(1, width), Math.max(1, height));
        dih$setVisible(widget, true);
    }

    @Unique
    private static void dih$setVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
    }

    @Unique
    private Component dih$spoofClientLabel(int width) {
        DihModule module = DihModule.get();
        boolean enabled = module != null && module.isSpoofClientVanilla();
        return dih$fitLabel(width, enabled ? "Client: Vanilla" : "Client: Modded", enabled ? "Vanilla" : "Modded", "Client");
    }

    @Unique
    private Component dih$bypassPacksLabel(int width) {
        DihModule module = DihModule.get();
        boolean enabled = module != null && module.isBypassResourcePack();
        return dih$fitLabel(width, enabled ? "Packs: Bypass" : "Packs: Normal", enabled ? "Bypass" : "Normal", "Packs");
    }

    @Unique
    private Component dih$replayServerLabel(int width) {
        boolean enabled = dih$getReplayBoolean("RECORD_SERVER", true);
        return dih$fitLabel(width, enabled ? "Replay: On" : "Replay: Off", enabled ? "Rec: On" : "Rec: Off", "Replay");
    }

    @Unique
    private Component dih$fitLabel(int width, String... candidates) {
        int available = Math.max(1, width - 8);
        for (String candidate : candidates) {
            if (this.font.width(candidate) <= available) return Component.literal(candidate);
        }
        return Component.literal(candidates[candidates.length - 1]);
    }

    @Unique
    private int dih$externalKind(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        String normalized = label.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        if (dihclient.platform.DihLoader.isModLoaded("viafabricplus") && "ViaFabricPlus".equals(label)) return EXTERNAL_VIA_FABRIC_PLUS;
        if (dihclient.platform.DihLoader.isModLoaded("replaymod")
            && (className.contains("replaymod") || normalized.contains("recordserver") || normalized.contains("replaymodguisettingsrecordserver"))) {
            return EXTERNAL_REPLAY_RECORD;
        }
        if (className.contains("opsec") || normalized.contains("opsec")) return EXTERNAL_OPSEC;
        return EXTERNAL_NONE;
    }

    @Unique
    private void dih$suppressMeteorWidgets() {
        if (!dihclient.platform.DihLoader.isModLoaded("meteor-client")) return;
        if (!dih$meteorUiConfigSuppressed) {
            dih$meteorUiConfigSuppressed = true;
            dih$disableMeteorMultiplayerUiConfig();
        }
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button) || dih$isOwned(button)) continue;
            String label = button.getMessage().getString();
            if ("Accounts".equals(label) || "Proxies".equals(label)) dih$setVisible(button, false);
        }
    }

    @Unique
    private void dih$toggleReplayServerRecording() {
        boolean enabled = dih$getReplayBoolean("RECORD_SERVER", true);
        dih$setReplayBoolean("RECORD_SERVER", !enabled);
    }

    @Unique
    private static boolean dih$getReplayBoolean(String settingField, boolean fallback) {
        try {
            Object settings = dih$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Object value = settings.getClass().getMethod("get", Class.forName("com.replaymod.core.SettingsRegistry$SettingKey")).invoke(settings, key);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    @Unique
    private static void dih$setReplayBoolean(String settingField, boolean value) {
        try {
            Object settings = dih$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Class<?> settingKeyClass = Class.forName("com.replaymod.core.SettingsRegistry$SettingKey");
            settings.getClass().getMethod("set", settingKeyClass, Object.class).invoke(settings, key, value);
            settings.getClass().getMethod("save").invoke(settings);
        } catch (ReflectiveOperationException | LinkageError ignored) {  }
    }

    @Unique
    private static Object dih$replaySettingsRegistry() throws ReflectiveOperationException {
        Object replayMod = Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);
        return replayMod.getClass().getMethod("getSettingsRegistry").invoke(replayMod);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dih$disableMeteorMultiplayerUiConfig() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Class<?> buttonPositionClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config$ButtonPosition");
            Object hidden = Enum.valueOf((Class<? extends Enum>) buttonPositionClass.asSubclass(Enum.class), "Hidden");
            dih$setMeteorSetting(configClass.getField("accountButtonAnchor").get(config), hidden);
            dih$setMeteorSetting(configClass.getField("proxiesButtonAnchor").get(config), hidden);
            dih$setMeteorSetting(configClass.getField("showAccountStatus").get(config), false);
            dih$setMeteorSetting(configClass.getField("showProxiesStatus").get(config), false);
        } catch (ReflectiveOperationException ignored) {  }
    }

    @Unique
    private static void dih$setMeteorSetting(Object setting, Object value) throws ReflectiveOperationException {
        setting.getClass().getSuperclass().getMethod("set", Object.class).invoke(setting, value);
    }

    @Override
    public void dih$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        dih$layoutButtons();
        if (PackHideState.isActive()) return;
        int leftTextWidth = Math.max(0, dih$topButtonsLeft - MARGIN - GAP);
        String username = this.minecraft.getUser().getName();
        if (leftTextWidth > 8) {
            graphics.text(this.font, dih$fitPlain("Logged in as " + username, leftTextWidth), MARGIN, MARGIN, 0xFFFFFFFF, false);
        }
        DihProxy proxy = DihProxyManager.get().getEnabled();
        int statusY = MARGIN + 12;
        if (proxy != null) {
            String proxyLabel = "Using proxy " + proxy.address + ":" + proxy.port;
            if (leftTextWidth > 8) graphics.text(this.font, dih$fitPlain(proxyLabel, leftTextWidth), MARGIN, statusY, 0xFFAFAFAF, false);
            statusY += 12;
        }
        String macroName = DihJoinMacroController.selectedMacroName();
        String macroLabel;
        int macroColor;
        if (macroName.isBlank()) {
            macroLabel = "Join Macro: none";
            macroColor = 0xFF8F8A8A;
        } else if (DihMacroManager.get().get(macroName) == null) {
            macroLabel = "Join Macro missing: " + macroName;
            macroColor = 0xFFFF6B6B;
        } else {
            macroLabel = "Join Macro: " + macroName + " - " + DihJoinMacroController.modeSummary();
            macroColor = 0xFF66E08A;
        }
        if (leftTextWidth > 8) graphics.text(this.font, dih$fitPlain(macroLabel, leftTextWidth), MARGIN, statusY, macroColor, false);
    }

    @Unique
    private String dih$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }

}
