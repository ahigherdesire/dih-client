package dihclient.mixin.compat;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.InteractPacketCompat} to ServerboundInteractPacket at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(ServerboundInteractPacket.class)
public abstract class DihInteractPacketCompatMixin implements dihclient.compat.InteractPacketCompat {
}
