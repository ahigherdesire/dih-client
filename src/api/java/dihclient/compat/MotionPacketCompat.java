package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

// Minecraft 1.21.11 only: the 26.1 record accessors of ClientboundSetEntityMotionPacket (injected by Loom, added at
// runtime by DihMotionPacketCompatMixin).
public interface MotionPacketCompat {
    default int id() {
        return ((ClientboundSetEntityMotionPacket) (Object) this).getId();
    }

    default Vec3 movement() {
        return ((ClientboundSetEntityMotionPacket) (Object) this).getMovement();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface MotionPacketCompat {
}
//?}
