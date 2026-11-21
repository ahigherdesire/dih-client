package dihclient.mixin;

import dihclient.script.ScriptInventory;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DihScriptInventoryNetworkMixin {
    @Inject(method="handleContainerContent",at=@At("RETURN"))
    private void dih$scriptInventorySnapshot(ClientboundContainerSetContentPacket packet,CallbackInfo ci) {
        ScriptInventory.onContent(packet.containerId());
    }
}
