package dihclient.mixin;

import dihclient.script.ScriptLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures evidence on the main-thread packet replay, before death/disconnect removes the world. */
@Mixin(ClientPacketListener.class)
public abstract class DihScriptLifecycleMixin {
    @Inject(method = "handleSetHealth", at = @At("HEAD"))
    private void dih$scriptHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) ScriptLifecycle.health(packet.getHealth());
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"))
    private void dih$scriptDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread() && mc.player != null && packet.playerId() == mc.player.getId()) ScriptLifecycle.death();
    }
}
