package dihclient.compat;

//? if >=26.1 {
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** Entity attack/interact packets by entity id. On 26.1+ these are the vanilla records; see the 1.21.11 branch. */
public final class DihPackets {
    private DihPackets() {
    }

    public static ServerboundAttackPacket attack(int entityId) {
        return new ServerboundAttackPacket(entityId);
    }

    public static ServerboundInteractPacket interact(int entityId, InteractionHand hand, Vec3 location, boolean secondary) {
        return new ServerboundInteractPacket(entityId, hand, location, secondary);
    }

    public static boolean isAttack(Packet<?> packet) {
        return packet instanceof ServerboundAttackPacket;
    }
}
//?} else {
/*import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

// Minecraft 1.21.11: attacks and interactions are one ServerboundInteractPacket with a private action, built from an
// Entity. These build them from an entity id by decoding the wire format through the packet's own codec, and read
// them back the same way (InteractPacketCompat).
public final class DihPackets {
    private DihPackets() {
    }

    private static final int INTERACT = 0;
    private static final int ATTACK = 1;
    private static final int INTERACT_AT = 2;

    public static ServerboundInteractPacket attack(int entityId) {
        var player = Minecraft.getInstance().player;
        return attack(entityId, player != null && player.isShiftKeyDown());
    }

    public static ServerboundInteractPacket attack(int entityId, boolean secondary) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(entityId);
        buf.writeVarInt(ATTACK);
        buf.writeBoolean(secondary);
        return ServerboundInteractPacket.STREAM_CODEC.decode(buf);
    }

    public static ServerboundInteractPacket interact(int entityId, InteractionHand hand, Vec3 location, boolean secondary) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(entityId);
        if (location == null) {
            buf.writeVarInt(INTERACT);
        } else {
            buf.writeVarInt(INTERACT_AT);
            buf.writeFloat((float) location.x);
            buf.writeFloat((float) location.y);
            buf.writeFloat((float) location.z);
        }
        buf.writeEnum(hand == null ? InteractionHand.MAIN_HAND : hand);
        buf.writeBoolean(secondary);
        return ServerboundInteractPacket.STREAM_CODEC.decode(buf);
    }

    public static boolean isAttack(Packet<?> packet) {
        return packet instanceof ServerboundInteractPacket interact && interact.isAttack();
    }

    // The packet as written: entity id, action type, the action's fields, sneaking.
    static Decoded decode(ServerboundInteractPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ServerboundInteractPacket.STREAM_CODEC.encode(buf, packet);
        int entityId = buf.readVarInt();
        int type = buf.readVarInt();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Vec3 location = Vec3.ZERO;
        if (type == INTERACT_AT) {
            location = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            hand = buf.readEnum(InteractionHand.class);
        } else if (type == INTERACT) {
            hand = buf.readEnum(InteractionHand.class);
        }
        return new Decoded(entityId, type, hand, location, buf.readBoolean());
    }

    record Decoded(int entityId, int type, InteractionHand hand, Vec3 location, boolean secondary) {
        boolean attack() {
            return type == ATTACK;
        }
    }
}
*///?}
