package dihclient.util;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
//? if >=26.3 {
/*import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.List;
*///?} else {
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
//?}
import org.jetbrains.annotations.Nullable;

/**
 * Arm swings on the wire, which differ between Minecraft versions.
 *
 * <p>26.2 sends {@code ServerboundSwingPacket(hand)} for every swing, including the one after using an item or a
 * block. 26.3 replaces it with a hand-less {@code ServerboundPunchPacket} that the client only sends when attacking
 * (a main-hand punch); uses send no swing at all, and the server announces swings with
 * {@code ClientboundSwingAnimationPacket} instead of an animate action.
 *
 * <p>Also the packet fields and constructors 26.3 reshaped: most packets became records ({@code getHand()} is
 * {@code hand()}), vehicle moves carry a {@code PositionAndRotation}, sign updates a line list and a side.
 */
public final class DihPackets {

    /** The class of a swing/punch packet, for packet lists and filters. */
    public static final Class<? extends Packet<?>> SWING =
        //? if >=26.3 {
        /*ServerboundPunchPacket.class;
        *///?} else {
        ServerboundSwingPacket.class;
        //?}

    /** Action codes of 26.2's swing animations, also used for 26.3's (see {@link #swingAnimationAction}). */
    public static final int SWING_MAIN_HAND_ACTION = 0;
    public static final int SWING_OFF_HAND_ACTION = 3;

    private DihPackets() {
    }

    /** A swing on its own (a punch at the air, a macro swing), or null where the version has none (off hand on 26.3). */
    public static @Nullable Packet<?> swing(InteractionHand hand) {
        //? if >=26.3 {
        /*return hand == InteractionHand.MAIN_HAND ? ServerboundPunchPacket.INSTANCE : null;
        *///?} else {
        return new ServerboundSwingPacket(hand);
        //?}
    }

    /** A main-hand swing on its own; every version has one. */
    public static Packet<?> mainHandSwing() {
        //? if >=26.3 {
        /*return ServerboundPunchPacket.INSTANCE;
        *///?} else {
        return new ServerboundSwingPacket(InteractionHand.MAIN_HAND);
        //?}
    }

    /**
     * The swing a client sends after using an item or a block, or null where the version sends none (26.3, where it
     * would be a punch, i.e. an attack).
     */
    public static @Nullable Packet<?> useSwing(InteractionHand hand) {
        //? if >=26.3 {
        /*return null;
        *///?} else {
        return new ServerboundSwingPacket(hand);
        //?}
    }

    public static boolean isSwing(Packet<?> packet) {
        return SWING.isInstance(packet);
    }

    /** The hand of a swing packet, when the version records one. */
    public static @Nullable InteractionHand swingHand(Packet<?> packet) {
        //? if >=26.3 {
        /*return packet instanceof ServerboundPunchPacket ? InteractionHand.MAIN_HAND : null;
        *///?} else {
        return packet instanceof ServerboundSwingPacket swing ? swing.getHand() : null;
        //?}
    }

    /** Entity id of a swing the server announces, or -1 when {@code packet} isn't one. */
    public static int swingAnimationEntity(Packet<?> packet) {
        //? if >=26.3 {
        /*return packet instanceof ClientboundSwingAnimationPacket swing ? swing.entityId() : -1;
        *///?} else {
        return packet instanceof ClientboundAnimatePacket animate && swingAction(animate.getAction()) ? animate.getId() : -1;
        //?}
    }

    /** {@link #SWING_MAIN_HAND_ACTION} or {@link #SWING_OFF_HAND_ACTION} for an announced swing, else -1. */
    public static int swingAnimationAction(Packet<?> packet) {
        //? if >=26.3 {
        /*if (!(packet instanceof ClientboundSwingAnimationPacket swing)) return -1;
        return swing.hand() == InteractionHand.MAIN_HAND ? SWING_MAIN_HAND_ACTION : SWING_OFF_HAND_ACTION;
        *///?} else {
        return packet instanceof ClientboundAnimatePacket animate && swingAction(animate.getAction()) ? animate.getAction() : -1;
        //?}
    }


    // ---------------------------------------------------------------- packet fields

    public static int teleportId(ServerboundAcceptTeleportationPacket p) {
        //? if >=26.3 {
        /*return p.id();
        *///?} else {
        return p.getId();
        //?}
    }

    public static InteractionHand hand(ServerboundUseItemOnPacket p) {
        //? if >=26.3 {
        /*return p.hand();
        *///?} else {
        return p.getHand();
        //?}
    }

    public static BlockHitResult hitResult(ServerboundUseItemOnPacket p) {
        //? if >=26.3 {
        /*return p.hitResult();
        *///?} else {
        return p.getHitResult();
        //?}
    }

    public static int sequence(ServerboundUseItemOnPacket p) {
        //? if >=26.3 {
        /*return p.sequence();
        *///?} else {
        return p.getSequence();
        //?}
    }

    public static InteractionHand hand(ServerboundUseItemPacket p) {
        //? if >=26.3 {
        /*return p.hand();
        *///?} else {
        return p.getHand();
        //?}
    }

    public static int sequence(ServerboundUseItemPacket p) {
        //? if >=26.3 {
        /*return p.sequence();
        *///?} else {
        return p.getSequence();
        //?}
    }

    public static float yRot(ServerboundUseItemPacket p) {
        //? if >=26.3 {
        /*return p.yRot();
        *///?} else {
        return p.getYRot();
        //?}
    }

    public static float xRot(ServerboundUseItemPacket p) {
        //? if >=26.3 {
        /*return p.xRot();
        *///?} else {
        return p.getXRot();
        //?}
    }

    public static InteractionHand hand(ClientboundOpenBookPacket p) {
        //? if >=26.3 {
        /*return p.hand();
        *///?} else {
        return p.getHand();
        //?}
    }

    public static int chunkX(ClientboundLevelChunkWithLightPacket p) {
        //? if >=26.3 {
        /*return p.x();
        *///?} else {
        return p.getX();
        //?}
    }

    public static int chunkZ(ClientboundLevelChunkWithLightPacket p) {
        //? if >=26.3 {
        /*return p.z();
        *///?} else {
        return p.getZ();
        //?}
    }

    public static ClientboundLevelChunkPacketData chunkData(ClientboundLevelChunkWithLightPacket p) {
        //? if >=26.3 {
        /*return p.chunkData();
        *///?} else {
        return p.getChunkData();
        //?}
    }

    public static ClientboundLightUpdatePacketData lightData(ClientboundLevelChunkWithLightPacket p) {
        //? if >=26.3 {
        /*return p.lightData();
        *///?} else {
        return p.getLightData();
        //?}
    }

    public static IntList entityIds(ClientboundRemoveEntitiesPacket p) {
        //? if >=26.3 {
        /*return p.entityIds();
        *///?} else {
        return p.getEntityIds();
        //?}
    }

    public static double x(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.x();
        *///?} else {
        return p.getX();
        //?}
    }

    public static double y(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.y();
        *///?} else {
        return p.getY();
        //?}
    }

    public static double z(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.z();
        *///?} else {
        return p.getZ();
        //?}
    }

    public static Vec3 position(ServerboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().position();
        *///?} else {
        return p.position();
        //?}
    }

    public static float yRot(ServerboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().yRot();
        *///?} else {
        return p.yRot();
        //?}
    }

    public static float xRot(ServerboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().xRot();
        *///?} else {
        return p.xRot();
        //?}
    }

    public static Vec3 position(ClientboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().position();
        *///?} else {
        return p.position();
        //?}
    }

    public static float yRot(ClientboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().yRot();
        *///?} else {
        return p.yRot();
        //?}
    }

    public static float xRot(ClientboundMoveVehiclePacket p) {
        //? if >=26.3 {
        /*return p.movingTo().xRot();
        *///?} else {
        return p.xRot();
        //?}
    }

    /** The synced position, or null when the packet leaves it unchanged (26.3 only syncs what changed). */
    public static @Nullable Vec3 position(ClientboundEntityPositionSyncPacket p) {
        //? if >=26.3 {
        /*return p.hasPosition() ? p.position().endPosition() : null;
        *///?} else {
        return p.values().position();
        //?}
    }

    /** The synced velocity, or null where the version doesn't send one (26.3). */
    public static @Nullable Vec3 movement(ClientboundEntityPositionSyncPacket p) {
        //? if >=26.3 {
        /*return null;
        *///?} else {
        return p.values().deltaMovement();
        //?}
    }

    public static float yRot(ClientboundEntityPositionSyncPacket p) {
        //? if >=26.3 {
        /*return p.yRot();
        *///?} else {
        return p.values().yRot();
        //?}
    }

    public static float xRot(ClientboundEntityPositionSyncPacket p) {
        //? if >=26.3 {
        /*return p.xRot();
        *///?} else {
        return p.values().xRot();
        //?}
    }

    public static double xDist(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.xDist();
        *///?} else {
        return p.getXDist();
        //?}
    }

    public static double yDist(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.yDist();
        *///?} else {
        return p.getYDist();
        //?}
    }

    public static double zDist(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.zDist();
        *///?} else {
        return p.getZDist();
        //?}
    }

    /** The particle speed; 26.3 has one per axis, this is the largest. */
    public static double maxSpeed(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return Math.max(Math.abs(p.xMaxSpeed()), Math.max(Math.abs(p.yMaxSpeed()), Math.abs(p.zMaxSpeed())));
        *///?} else {
        return p.getMaxSpeed();
        //?}
    }

    public static int count(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.count();
        *///?} else {
        return p.getCount();
        //?}
    }

    public static ParticleOptions particle(ClientboundLevelParticlesPacket p) {
        //? if >=26.3 {
        /*return p.particle();
        *///?} else {
        return p.getParticle();
        //?}
    }

    public static BlockPos pos(ClientboundOpenSignEditorPacket p) {
        //? if >=26.3 {
        /*return p.pos();
        *///?} else {
        return p.getPos();
        //?}
    }

    /** Whether the sign editor opens the front side. */
    public static boolean front(ClientboundOpenSignEditorPacket p) {
        //? if >=26.3 {
        /*return p.slot() == SignTextSlot.FRONT;
        *///?} else {
        return p.isFrontText();
        //?}
    }

    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags(ClientboundUpdateTagsPacket p) {
        //? if >=26.3 {
        /*return p.tags();
        *///?} else {
        return p.getTags();
        //?}
    }

    /** A chat message's signature, or null when unsigned. */
    public static @Nullable MessageSignature signature(ClientboundPlayerChatPacket p) {
        //? if >=26.3 {
        /*return p.signature().orElse(null);
        *///?} else {
        return p.signature();
        //?}
    }

    /** The server's replacement text for a chat message, or null when it shows the signed text. */
    public static @Nullable Component unsignedContent(ClientboundPlayerChatPacket p) {
        //? if >=26.3 {
        /*return p.unsignedContent().orElse(null);
        *///?} else {
        return p.unsignedContent();
        //?}
    }

    // ---------------------------------------------------------------- packet constructors

    public static ServerboundMoveVehiclePacket moveVehicle(Vec3 position, float yRot, float xRot, boolean onGround) {
        //? if >=26.3 {
        /*return new ServerboundMoveVehiclePacket(PositionAndRotation.of(position, yRot, xRot), onGround);
        *///?} else {
        return new ServerboundMoveVehiclePacket(position, yRot, xRot, onGround);
        //?}
    }

    /** A chat message; a null {@code signature} sends it unsigned. */
    public static ServerboundChatPacket chat(String content, Instant timestamp, long salt, @Nullable MessageSignature signature,
                                             LastSeenMessages.Update lastSeen) {
        //? if >=26.3 {
        /*return new ServerboundChatPacket(content, timestamp, salt, Optional.ofNullable(signature), lastSeen);
        *///?} else {
        return new ServerboundChatPacket(content, timestamp, salt, signature, lastSeen);
        //?}
    }

    /** Accepts teleport {@code id}; 26.3 also echoes where the teleport put us. */
    public static ServerboundAcceptTeleportationPacket acceptTeleport(int id, Vec3 position, float yRot, float xRot) {
        //? if >=26.3 {
        /*return new ServerboundAcceptTeleportationPacket(id, position.x, position.y, position.z, yRot, xRot);
        *///?} else {
        return new ServerboundAcceptTeleportationPacket(id);
        //?}
    }

    /** A sign edit: the four lines of the {@code front} or back side. */
    public static ServerboundSignUpdatePacket signUpdate(BlockPos pos, boolean front, String line1, String line2,
                                                         String line3, String line4) {
        //? if >=26.3 {
        /*return new ServerboundSignUpdatePacket(pos, List.of(line1, line2, line3, line4),
            front ? SignTextSlot.FRONT : SignTextSlot.BACK);
        *///?} else {
        return new ServerboundSignUpdatePacket(pos, front, line1, line2, line3, line4);
        //?}
    }

    private static boolean swingAction(int action) {
        return action == SWING_MAIN_HAND_ACTION || action == SWING_OFF_HAND_ACTION;
    }
}
