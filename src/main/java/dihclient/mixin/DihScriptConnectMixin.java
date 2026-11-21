package dihclient.mixin;

import dihclient.script.ScriptLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bind resume permission after vanilla's preparatory disconnect; manual attempts revoke it. */
@Mixin(ConnectScreen.class)
public abstract class DihScriptConnectMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void dih$scriptConnecting(Minecraft minecraft, ServerAddress address, ServerData server,
                                     TransferState transfer, CallbackInfo ci) {
        ScriptLifecycle.connectionStarted(this, server.ip);
    }

    //? if >=26.1 {
    @Inject(method = "lambda$init$0", at = @At("HEAD"), remap = false)
    //?} else {
    /*@Inject(method = "method_19800", at = @At("HEAD"), remap = false)
    *///?}
    private void dih$scriptConnectionCancelled(Button button, CallbackInfo ci) {
        ScriptLifecycle.connectionFailed(this);
    }
}
