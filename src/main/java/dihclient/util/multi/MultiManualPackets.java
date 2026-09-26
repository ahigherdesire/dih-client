package dihclient.util.multi;

import dihclient.util.DihPackets;
import dihclient.util.DihPacketRegistry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MultiManualPackets {
    private static final Set<Class<?>> SAFE = Set.of(
        ServerboundChatCommandPacket.class,
        ServerboundClientCommandPacket.class,
        ServerboundCustomPayloadPacket.class,
        ServerboundMovePlayerPacket.Pos.class,
        ServerboundMovePlayerPacket.PosRot.class,
        ServerboundMovePlayerPacket.Rot.class,
        ServerboundMovePlayerPacket.StatusOnly.class,
        ServerboundPlayerInputPacket.class,
        ServerboundSetCarriedItemPacket.class,
        DihPackets.SWING
    );

    private MultiManualPackets() {
    }

    public static boolean isSafe(Class<? extends Packet<?>> packetClass) {
        return packetClass != null && SAFE.contains(packetClass);
    }

    public static Set<Class<? extends Packet<?>>> unsafeC2S() {
        Set<Class<? extends Packet<?>>> out = new LinkedHashSet<>();
        for (Class<? extends Packet<?>> packetClass : DihPacketRegistry.getC2SPackets()) {
            if (!isSafe(packetClass)) out.add(packetClass);
        }
        return out;
    }
}
