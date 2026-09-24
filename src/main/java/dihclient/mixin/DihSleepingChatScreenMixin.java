package dihclient.mixin;

import dihclient.gui.screen.DihStyledButton;
import dihclient.gui.vanillaui.components.Button;
import dihclient.modules.DihModule;
import dihclient.util.DihClientWake;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InBedChatScreen.class)
public abstract class DihSleepingChatScreenMixin extends Screen {
    protected DihSleepingChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void dih$init(CallbackInfo ci) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        this.addRenderableWidget(new DihStyledButton(
            5, 5, 140, 20,
            Component.literal("Client wake up"),
            Button.Tone.PRIMARY,
            button -> DihClientWake.wake()
        ));
    }
}
