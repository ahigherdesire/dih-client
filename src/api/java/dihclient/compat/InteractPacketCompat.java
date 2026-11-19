package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

// Minecraft 1.21.11 only: the 26.1 record accessors of ServerboundInteractPacket, injected by Loom and added at runtime
// by DihInteractPacketCompatMixin. An attack has hand MAIN_HAND and location ZERO, as does a plain interaction's location.
public interface InteractPacketCompat {
    private ServerboundInteractPacket dih$self() {
        return (ServerboundInteractPacket) (Object) this;
    }

    default int entityId() {
        return DihPackets.decode(dih$self()).entityId();
    }

    default InteractionHand hand() {
        return DihPackets.decode(dih$self()).hand();
    }

    default Vec3 location() {
        return DihPackets.decode(dih$self()).location();
    }

    default boolean usingSecondaryAction() {
        return dih$self().isUsingSecondaryAction();
    }

    default boolean isAttack() {
        return DihPackets.decode(dih$self()).attack();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface InteractPacketCompat {
}
//?}
