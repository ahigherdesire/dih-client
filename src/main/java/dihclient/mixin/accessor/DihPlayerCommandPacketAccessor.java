package dihclient.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPlayerCommandPacket.class)
public interface DihPlayerCommandPacketAccessor {
    @Mutable
    @Accessor("id")
    void dih$setEntityId(int entityId);
}
