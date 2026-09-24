package dihclient.util;

import dihclient.gui.macro.editor.ActionEditorOverlay;
import dihclient.util.macro.PayloadAction;
import dihclient.util.macro.SendPacketAction;
import dihclient.util.macro.WaitForPacketAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class DihPacketEntryActions {
    private DihPacketEntryActions() {
    }

    public static boolean canQueue(DihPacketLoggerOverlay.LogEntry entry) {
        return entry != null && "C2S".equalsIgnoreCase(entry.direction) && entry.packetRef != null;
    }

    public static boolean canDirectSend(DihPacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean directSend(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canDirectSend(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be sent.");
            return false;
        }

        if (entry.packetRef instanceof ServerboundCustomPayloadPacket) {
            return directSendPayload(entry);
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }
        try {
            DihPacketSender.send(regenerated);
            DihClientMessaging.sendPrefixed("Sent: " + entry.shortName);
            return true;
        } catch (Exception e) {
            DihClientMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean directSendPayload(DihPacketLoggerOverlay.LogEntry entry) {
        DihPayloadSupport.PayloadSnapshot snapshot = DihPayloadSupport.snapshotFromEntry(entry);
        if (snapshot == null || snapshot.channel() == null || snapshot.channel().isBlank()) {
            Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
            if (regenerated != null) {
                try {
                    DihPacketSender.send(regenerated);
                    DihClientMessaging.sendPrefixed("Sent: " + entry.shortName);
                    return true;
                } catch (Exception e) {
                    DihClientMessaging.sendPrefixed("\u00a7cSend failed: " + e.getMessage());
                    return false;
                }
            }
            DihClientMessaging.sendPrefixed("\u00a7cCannot send: no payload data for " + entry.shortName);
            return false;
        }
        byte[] rawBytes = snapshot.rawBytes();
        if (!DihPayloadSupport.sendPayload(snapshot.channel(), rawBytes, snapshot.protocolPhase())) {
            DihClientMessaging.sendPrefixed("\u00a7cFailed to send payload: " + entry.shortName);
            return false;
        }
        DihClientMessaging.sendPrefixed("Sent payload: " + snapshot.channel());
        return true;
    }

    public static boolean canAddSendAction(DihPacketLoggerOverlay.LogEntry entry) {
        return canQueue(entry);
    }

    public static boolean canAddWaitAction(DihPacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.shortName != null && !entry.shortName.isBlank() && entry.direction != null && !entry.direction.isBlank();
    }

    public static boolean canEditPayload(DihPacketLoggerOverlay.LogEntry entry) {
        return entry != null && entry.isPayload && "C2S".equalsIgnoreCase(entry.direction);
    }

    public static boolean canAddPayloadAction(DihPacketLoggerOverlay.LogEntry entry) {
        return canEditPayload(entry);
    }

    public static boolean queue(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canQueue(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be queued.");
            return false;
        }

        DihSharedState.get().enqueuePacket(entry.packetRef);
        DihClientMessaging.sendPrefixed("Queued: " + entry.shortName);
        return true;
    }

    public static boolean addSendActionToVisibleMacro(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canAddSendAction(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cOnly C2S packets can be added as send actions.");
            return false;
        }

        DihMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        Packet<?> regenerated = PacketRegenerator.regenerate(entry.packetRef);
        if (regenerated == null) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot regenerate: " + entry.shortName);
            return false;
        }

        SendPacketAction action = new SendPacketAction();
        action.waitForGuiBefore = false;
        action.waitForGuiAfter = false;
        action.guiName = "";
        action.packets.add(new DihSharedState.QueuedPacket(regenerated, 0));
        macroEditor.addAction(action);
        DihOverlayManager.get().bringToFront(macroEditor);
        DihClientMessaging.sendPrefixed("Added send action: " + entry.shortName);
        return true;
    }

    public static boolean addWaitActionToVisibleMacro(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canAddWaitAction(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        DihMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        String target = WaitForPacketAction.withDirection(entry.direction, entry.shortName);
        if (target.isEmpty()) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot add this packet as a wait condition.");
            return false;
        }

        WaitForPacketAction action = new WaitForPacketAction(target);
        action.packetNames.add(target);
        macroEditor.addAction(action);
        DihOverlayManager.get().bringToFront(macroEditor);
        DihClientMessaging.sendPrefixed("Added wait condition: " + WaitForPacketAction.getDisplayLabel(target));
        return true;
    }

    public static boolean openPayloadEditor(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canEditPayload(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be edited.");
            return false;
        }

        PayloadAction action = DihPayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            DihClientMessaging.sendPrefixed("\u00a7cFailed to seed payload editor from packet.");
            return false;
        }

        ActionEditorOverlay.getSharedOverlay().openStandalonePayloadEditor(action);
        DihOverlayManager.get().bringToFront(ActionEditorOverlay.getSharedOverlay());
        return true;
    }

    public static boolean addPayloadActionToVisibleMacro(DihPacketLoggerOverlay.LogEntry entry) {
        if (!canAddPayloadAction(entry)) {
            DihClientMessaging.sendPrefixed("\u00a7cOnly captured C2S custom payload packets can be added as payload actions.");
            return false;
        }

        DihMacroEditorOverlay macroEditor = getOrOpenMacroEditor();
        if (macroEditor == null) {
            DihClientMessaging.sendPrefixed("\u00a7cCannot open the macro editor.");
            return false;
        }

        PayloadAction action = DihPayloadSupport.seedActionFromEntry(entry);
        if (action == null) {
            DihClientMessaging.sendPrefixed("\u00a7cFailed to create payload action from packet.");
            return false;
        }

        macroEditor.addAction(action);
        DihOverlayManager.get().bringToFront(macroEditor);
        DihClientMessaging.sendPrefixed("Added payload action: " + entry.shortName);
        return true;
    }

    public static boolean hasVisibleMacroEditor() {
        return findVisibleMacroEditor() != null;
    }

    private static DihMacroEditorOverlay getOrOpenMacroEditor() {
        DihMacroEditorOverlay macroEditor = findVisibleMacroEditor();
        if (macroEditor != null) {
            DihOverlayManager.get().bringToFront(macroEditor);
            return macroEditor;
        }

            macroEditor = DihMacroEditorOverlay.getSharedOverlay();
        if (macroEditor == null) return null;

        DihMacro existingMacro = DihSharedState.get().getEditingMacro();
        macroEditor.open(existingMacro);
        DihOverlayManager.get().bringToFront(macroEditor);
        return macroEditor;
    }

    private static DihMacroEditorOverlay findVisibleMacroEditor() {
        for (IDihOverlay overlay : DihOverlayManager.get().getOverlays()) {
            if (overlay instanceof DihMacroEditorOverlay macroEditor && macroEditor.isVisible()) {
                return macroEditor;
            }
        }
        return null;
    }
}
