package dihclient.mixin;

import dihclient.gui.screen.DihPauseScreen;
import dihclient.gui.screen.DihPanicTitleScreen;
import dihclient.gui.screen.DihTitleScreen;
import dihclient.modules.PackHideState;
import dihclient.gui.vanillaui.components.CompactTextInput;
import dihclient.util.DihMenuPrefs;
import dihclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class DihGuiSetScreenMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean dih$replacingScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void dih$replaceScreen(Screen screen, CallbackInfo ci) {

        CompactTextInput.clearFocusedInput();
        if (!dih$replacingScreen) MacroExecutor.recordRecentGuiScreen(screen);
        if (dih$replacingScreen || screen == null) return;

        if (PackHideState.isActive() && screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
            dih$setScreen(new DihPauseScreen());
            ci.cancel();
            return;
        }

        if (!(screen instanceof TitleScreen)) return;

        if (PackHideState.isActive()) {
            dih$setScreen(new DihPanicTitleScreen());
            ci.cancel();
            return;
        }

        if (!dihclient.util.DihLiteVariant.enabled() && DihMenuPrefs.customMainMenuEnabled()) {
            dih$setScreen(new dihclient.gui.screen.DihTitleScreen());
            ci.cancel();
        }
    }

    @Unique
    private void dih$setScreen(Screen screen) {
        dih$replacingScreen = true;
        try {
            minecraft.gui.setScreen(screen);
        } finally {
            dih$replacingScreen = false;
        }
    }
}
