package dihclient.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundRotateHeadPacket.class)
public interface DihRotateHeadPacketAccessor {
    @Accessor("entityId")
    int dih$getEntityId();
}
