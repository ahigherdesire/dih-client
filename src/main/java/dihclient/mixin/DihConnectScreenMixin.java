package dihclient.mixin;

import dihclient.modules.PackAutoReconnectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class DihConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void dih$rememberConnectAttempt(Screen parent, Minecraft minecraft, ServerAddress hostAndPort,
                                                      ServerData data, boolean isQuickPlay, TransferState transferState,
                                                      CallbackInfo ci) {

        PackAutoReconnectState.remember(data, hostAndPort);

        if (!dihclient.util.DihLiteVariant.enabled()) {
            try {
                dihclient.util.DihProfileManager.get().applyForServerConnect(hostAndPort, data);
            } catch (Throwable t) {
                dihclient.DihClientAddon.LOG.error("Profiles: failed to apply on-connect profile", t);
            }
        }
    }
}
