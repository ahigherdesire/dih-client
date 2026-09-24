package dihclient.mixin;

import dihclient.ducks.DihDisconnectedScreenAccess;
import dihclient.gui.screen.DihStyledButton;
import dihclient.gui.vanillaui.components.Button;
import dihclient.modules.PackAutoReconnectState;
import dihclient.modules.PackHideState;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DihDisconnectedScreenMixin extends Screen implements DihDisconnectedScreenAccess {
    @Shadow @Final private Screen parent;

    protected DihDisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Override
    public Screen dih$parent() {
        return this.parent;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dih$addReconnectButton(CallbackInfo ci) {

        if (PackHideState.isActive()) return;
        if (!PackAutoReconnectState.canShowToggle()) return;

        int w = 148;
        this.addRenderableWidget(new DihStyledButton(
            this.width / 2 - w / 2, this.height - 38, w, 20,
            Component.literal("Auto Reconnect"),
            Button.Tone.PRIMARY,
            PackAutoReconnectState::toggleLabel,
            button -> PackAutoReconnectState.toggle()
        ));
    }
}
