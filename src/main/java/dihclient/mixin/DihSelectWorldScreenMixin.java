package dihclient.mixin;

import dihclient.ducks.DihExternalButtonScreen;
import dihclient.gui.screen.DihJoinMacroScreen;
import dihclient.modules.PackHideState;
import dihclient.util.DihJoinMacroController;
import dihclient.util.DihMacroManager;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SelectWorldScreen.class, priority = 2000)
public abstract class DihSelectWorldScreenMixin extends Screen implements DihExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 104;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int BUTTON_GAP = 3;
    @Unique private Button dih$macroButton;
    @Unique private Button dih$recordButton;

    protected DihSelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void dih$repositionElements(CallbackInfo ci) {
        dih$layoutReplayButton();
    }

    @Unique
    private void dih$layoutReplayButton() {
        AbstractWidget back = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (widget == dih$recordButton || widget == dih$macroButton) continue;
            if (dih$isReplayRecordButton(widget)) {
                widget.visible = false;
                widget.active = false;
                continue;
            }
            if (dih$isBackButton(widget)) back = widget;
        }

        if (PackHideState.isActive()) {
            if (dih$macroButton != null) {
                dih$macroButton.visible = false;
                dih$macroButton.active = false;
            }
            if (dih$recordButton != null) {
                dih$recordButton.visible = false;
                dih$recordButton.active = false;
            }
            return;
        }

        if (dih$macroButton == null) {
            dih$macroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.gui.setScreen(new DihJoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        if (dih$recordButton == null) {
            dih$recordButton = this.addRenderableWidget(Button.builder(dih$replaySingleplayerLabel(), ignored -> {
                dih$toggleReplaySingleplayerRecording();
                dih$recordButton.setMessage(dih$replaySingleplayerLabel());
            }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        int macroX = back != null ? back.getRight() + BUTTON_GAP : (this.width / 2) + 104;
        int y = back != null ? back.getY() : this.height - 28;
        if (macroX + MACRO_BUTTON_WIDTH > this.width - 4) {
            macroX = Math.max(4, this.width - 4 - MACRO_BUTTON_WIDTH);
            y = Math.max(4, y - BUTTON_HEIGHT - BUTTON_GAP);
        }
        dih$macroButton.setX(macroX);
        dih$macroButton.setY(y);
        dih$macroButton.setSize(MACRO_BUTTON_WIDTH, BUTTON_HEIGHT);
        dih$macroButton.visible = true;
        dih$macroButton.active = true;

        if (!FabricLoader.getInstance().isModLoaded("replaymod")) {
            dih$recordButton.visible = false;
            dih$recordButton.active = false;
            return;
        }

        int width = Math.min(BUTTON_WIDTH, Math.max(60, this.width - 8));
        int recordX = macroX + MACRO_BUTTON_WIDTH + BUTTON_GAP;
        int recordY = y;
        if (recordX + width > this.width - 4) {
            recordX = Math.max(4, this.width - 4 - width);
            recordY = Math.max(4, y - BUTTON_HEIGHT - BUTTON_GAP);
        }
        dih$recordButton.setX(recordX);
        dih$recordButton.setY(recordY);
        dih$recordButton.setSize(width, BUTTON_HEIGHT);
        dih$recordButton.setMessage(dih$replaySingleplayerLabel());
        dih$recordButton.visible = true;
        dih$recordButton.active = true;
    }

    @Unique
    private boolean dih$isBackButton(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        return label.equals(Component.translatable("gui.back").getString()) || "back".equalsIgnoreCase(label);
    }

    @Unique
    private boolean dih$isReplayRecordButton(AbstractWidget widget) {
        if (!FabricLoader.getInstance().isModLoaded("replaymod")) return false;
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        String normalizedLabel = widget.getMessage().getString().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        return className.contains("replaymod")
            || normalizedLabel.contains("recordsingleplayer")
            || normalizedLabel.contains("replaymodguisettingsrecordsingleplayer");
    }

    @Unique
    private Component dih$replaySingleplayerLabel() {
        boolean enabled = dih$getReplayBoolean("RECORD_SINGLEPLAYER", true);
        return Component.literal(enabled ? "Replay: On" : "Replay: Off");
    }

    @Unique
    private void dih$toggleReplaySingleplayerRecording() {
        boolean enabled = dih$getReplayBoolean("RECORD_SINGLEPLAYER", true);
        dih$setReplayBoolean("RECORD_SINGLEPLAYER", !enabled);
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

    @Override
    public void dih$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        dih$layoutReplayButton();
        if (PackHideState.isActive()) return;

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
        graphics.text(this.font, dih$fitPlain(macroLabel, 220), 4, 4, macroColor, false);
    }

    @Unique
    private String dih$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }
}
