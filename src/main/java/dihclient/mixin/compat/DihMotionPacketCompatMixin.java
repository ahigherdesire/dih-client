package dihclient.mixin.compat;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.MotionPacketCompat} to ClientboundSetEntityMotionPacket at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(ClientboundSetEntityMotionPacket.class)
public abstract class DihMotionPacketCompatMixin implements dihclient.compat.MotionPacketCompat {
}
