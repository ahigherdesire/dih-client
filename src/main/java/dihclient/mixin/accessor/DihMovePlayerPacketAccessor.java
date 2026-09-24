package dihclient.mixin.accessor;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface DihMovePlayerPacketAccessor {
    @Mutable
    @Accessor("y")
    void dih$setY(double y);

    @Mutable
    @Accessor("onGround")
    void dih$setOnGround(boolean onGround);

    @Mutable
    @Accessor("yRot")
    void dih$setYRot(float yRot);

    @Mutable
    @Accessor("xRot")
    void dih$setXRot(float xRot);
}
