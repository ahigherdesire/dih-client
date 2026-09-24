package dihclient.util.mm;

import dihclient.util.DihClipboardHelper;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.DihNotifications;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.macro.MacroAction;
import dihclient.util.mm.msg.MmMessages;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;

public final class MmShare {
    private MmShare() {}

    public static MmMessages.MacroOffer buildMacroOffer(DihMacro macro) {
        if (macro == null) return null;

        DihMacro stripped = macro.deepCopy();
        stripped.name = "";
        stripped.keyCode = -1;
        String hash = DihClipboardHelper.serializeMacroToBase64(stripped);
        if (hash == null) return null;
        MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
        offer.macroName = "";
        List<MacroAction> actions = macro.actions;
        offer.actionCount = actions == null ? 0 : actions.size();
        if (offer.actionCount == 1) {
            MacroAction only = actions.get(0);
            offer.singleActionLabel = only == null ? "" : safe(only.getDisplayName());
        }
        offer.hash = hash;
        return offer;
    }

    public static MmMessages.PacketOffer buildPacketOffer(List<DihSharedState.QueuedPacket> queue, String friendlyName) {
        if (queue == null || queue.isEmpty()) return null;
        String data = DihClipboardHelper.serializeQueueToBase64(queue);
        if (data == null) return null;
        MmMessages.PacketOffer offer = new MmMessages.PacketOffer();
        int n = queue.size();
        offer.friendlyName = (friendlyName == null || friendlyName.isBlank())
            ? (n + (n == 1 ? " packet" : " packets")) : friendlyName;
        offer.direction = "C2S";
        offer.data = data;
        return offer;
    }

    public static List<DihSharedState.QueuedPacket> queueFromOffer(MmMessages.PacketOffer offer) {
        if (offer == null || offer.data == null) return null;
        return DihClipboardHelper.deserializeQueueFromBase64(offer.data);
    }

    public static int addToQueue(MmMessages.PacketOffer offer) {
        List<DihSharedState.QueuedPacket> add = queueFromOffer(offer);
        if (add == null || add.isEmpty()) return -1;
        List<DihSharedState.QueuedPacket> cur = new ArrayList<>(DihSharedState.get().getDelayedPackets());
        cur.addAll(add);
        DihSharedState.get().setDelayedPackets(cur);
        return add.size();
    }

    public static DihMacro importMacro(MmMessages.MacroOffer offer) {
        if (offer == null) return null;
        DihMacro macro = DihClipboardHelper.deserializeMacroFromBase64(offer.hash);
        if (macro == null) {
            DihNotifications.show("Could not import macro (corrupt or unsupported).", 0xFFE0533A);
            return null;
        }

        macro.keyCode = -1;
        String name = (offer.macroName == null || offer.macroName.isBlank()) ? macro.name : offer.macroName;
        DihMacro imported = DihMacroManager.get().addImportedCopy(macro, name);
        DihNotifications.show("Imported macro: " + (imported != null ? imported.name : name), 0xFF35D873);
        return imported;
    }

    public static Packet<?> packetFromOffer(MmMessages.PacketOffer offer) {
        if (offer == null || offer.data == null) return null;
        return DihClipboardHelper.deserializePacketFromBase64(offer.data);
    }

    public static DihPacketLoggerOverlay.LogEntry inspectableEntry(MmMessages.PacketOffer offer) {
        Packet<?> packet = packetFromOffer(offer);
        if (packet == null) return null;
        String dir = (offer.direction == null || offer.direction.isBlank()) ? "C2S" : offer.direction;
        return new DihPacketLoggerOverlay.LogEntry(
            System.currentTimeMillis(), 0, dir,
            offer.friendlyName == null ? packet.getClass().getSimpleName() : offer.friendlyName,
            packet.getClass(), packet, false, false, false, null, null, null, null);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
