package dihclient.util.macro;

import dihclient.modules.PackHideState;
import dihclient.util.DihClipboardHelper;
import dihclient.util.PacketRegenerator;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;

public class PacketAction implements MacroAction {
    public String packetData = "";
    public boolean regenerate = true;
    public String description = "Packet";

    public PacketAction() {}

    public PacketAction(String packetData, boolean regenerate, String description) {
        this.packetData = packetData;
        this.regenerate = regenerate;
        this.description = description;
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc.getConnection() == null) {
            DihClientMessaging.sendPrefixed("§cNo network connection!");
            return;
        }

        MacroTemplate.Resolution packetDataResolution = MacroVariables.resolve(packetData, mc);
        if (!packetDataResolution.success()) return;
        String resolvedPacketData = packetDataResolution.value();
        if (resolvedPacketData.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cPacket data is empty!");
            return;
        }

        try {
            Packet<?> packet = DihClipboardHelper.deserializePacketFromBase64(resolvedPacketData);
            if (packet == null) {
                DihClientMessaging.sendPrefixed("§cFailed to deserialize packet! Invalid data.");
                return;
            }

            if (regenerate) {
                Packet<?> regenerated = PacketRegenerator.regenerate(packet);
                if (regenerated == null) {
                    DihClientMessaging.sendPrefixed("§cFailed to regenerate packet: " + packet.getClass().getSimpleName());
                    return;
                }
                packet = regenerated;
            }

            dihclient.util.DihPacketSender.send(packet);

        } catch (Exception e) {
            DihClientMessaging.sendPrefixed("§cPacket send error: " + e.getMessage());
            dihclient.DihClientAddon.LOG.error("[MacroExecutor] Packet action failed", e);
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("packetData", packetData);
        tag.putBoolean("regenerate", regenerate);
        tag.putString("description", description);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("packetData")) packetData = tag.getStringOr("packetData", "");
        if (tag.contains("regenerate")) regenerate = tag.getBooleanOr("regenerate", true);
        if (tag.contains("description")) description = tag.getStringOr("description", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PACKET;
    }

    @Override
    public String getDisplayName() {
        return description.isEmpty() ? "Unknown Packet" : description;
    }

    @Override
    public String getIcon() {
        return "Pkt";
    }
}
