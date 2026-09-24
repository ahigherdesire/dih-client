package dihclient.mixin;

import dihclient.gui.screen.DihModuleScreen;
import dihclient.gui.screen.DihOverlayHostScreen;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.util.DihLinks;
import dihclient.util.DihMatchmakingOverlay;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihProfilesOverlay;
import dihclient.util.IDihOverlay;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class DihTitleScreenSupportMixin extends Screen {
    protected DihTitleScreenSupportMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dih$addSupportButtons(CallbackInfo ci) {
        if (PackHideState.isActive()) return;

        dih$rightButton(Component.literal("Modules & Macros"), 4, b -> {
            if (!PackHideState.isHardLocked()) {
                this.minecraft.gui.setScreen(new DihModuleScreen(this, DihModuleScreen.Mode.TITLE_SETUP));
            }
        });
        dih$rightButton(Component.literal("Matchmaking"), 28, b -> dih$openMenuOverlay(true));
        dih$rightButton(Component.literal("Profiles"), 52, b -> dih$openMenuOverlay(false));

        dih$leftButton(Component.literal("Website"), 4, DihLinks.WEBSITE);
        dih$leftButton(Component.literal("Source code"), 28, DihLinks.SOURCE);
    }

    @Unique
    private void dih$rightButton(Component label, int y, Button.OnPress onPress) {
        int w = dih$buttonWidth(label);
        this.addRenderableWidget(Button.builder(label, onPress).bounds(this.width - w - 4, y, w, 20).build());
    }

    @Unique
    private void dih$leftButton(Component label, int y, String url) {
        this.addRenderableWidget(Button.builder(label, b -> DihLinks.open(url))
            .bounds(4, y, dih$buttonWidth(label), 20).build());
    }

    @Unique
    private int dih$buttonWidth(Component label) {
        return Math.max(96, Math.min(this.width - 8, this.font.width(label) + 20));
    }

    @Unique
    private void dih$openMenuOverlay(boolean matchmaking) {
        if (PackHideState.isHardLocked()) return;
        DihModule mod = DihModule.get();
        if (mod == null) return;
        IDihOverlay overlay = matchmaking ? mod.getMatchmakingOverlay() : mod.getProfilesOverlay();
        if (overlay == null) return;
        DihOverlayManager.get().register(overlay);
        if (overlay instanceof DihMatchmakingOverlay mm) mm.setMainMenuMode(true);
        else if (overlay instanceof DihProfilesOverlay pf) pf.setMainMenuMode(true);
        overlay.setVisible(true);
        this.minecraft.gui.setScreen(new DihOverlayHostScreen(overlay, this, true));
    }
}
