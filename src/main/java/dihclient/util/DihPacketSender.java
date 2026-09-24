package dihclient.util;

import dihclient.modules.PackHideState;
import dihclient.security.DihProtector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class DihPacketSender {

    public static void send(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        DihProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

    public static void sendPacketDirect(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        DihProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

}
