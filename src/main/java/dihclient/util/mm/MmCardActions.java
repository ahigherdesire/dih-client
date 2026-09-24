package dihclient.util.mm;

import dihclient.gui.screen.DihOverlayHostScreen;
import dihclient.util.DihClipboardHelper;
import dihclient.util.DihFilterViewOverlay;
import dihclient.util.DihGuiViewOverlay;
import dihclient.util.DihItemNbtInspectOverlay;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroEditorOverlay;
import dihclient.util.DihModuleViewOverlay;
import dihclient.util.DihNotifications;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketInspectOverlay;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.IDihOverlay;
import dihclient.util.mm.msg.MmMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MmCardActions {
    private static final Map<Integer, MmChatLine> CARDS = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int MAX = 256;

    static final String NAMESPACE = "dihcard";

    private static final int OK = 0xFF35D873;
    private static final int WARN = 0xFFFFC857;
    private static final int ERR = 0xFFFF5B5B;

    private MmCardActions() {}

    public static int register(MmChatLine line) {
        int id = SEQ.incrementAndGet();
        CARDS.put(id, line);
        if (CARDS.size() > MAX) {
            int threshold = id - MAX;
            CARDS.keySet().removeIf(k -> k <= threshold);
        }
        return id;
    }

    public static boolean handleClickCommand(String command) {
        if (command == null) return false;
        String c = command.trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        if (!c.equals(NAMESPACE) && !c.startsWith(NAMESPACE + " ")) return false;
        String rest = c.length() > NAMESPACE.length() ? c.substring(NAMESPACE.length()).trim() : "";
        int sp = rest.indexOf(' ');
        if (sp > 0) {
            String action = rest.substring(0, sp).trim();
            try {
                handleClick(action, Integer.parseInt(rest.substring(sp + 1).trim()));
            } catch (NumberFormatException ignored) {
                DihNotifications.show("This shared item is no longer available.", WARN);
            }
        }
        return true;
    }

    public static void handleClick(String action, int token) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> perform(action, token));
    }

    private static void perform(String action, int token) {
        MmChatLine line = CARDS.get(token);
        if (line == null) { DihNotifications.show("This shared item is no longer available.", WARN); return; }
        try {
            switch (action) {
                case "import" -> { if (line.macro != null) MmShare.importMacro(line.macro); }
                case "run" -> { if (line.command != null) MatchmakingManager.get().runCommandOffer(line.command); }
                case "addqueue" -> { if (line.packet != null) addToQueue(line.packet); }
                case "importfilter" -> {
                    if (line.blob != null) {
                        int n = MmBlobs.importFilter(line.blob);
                        DihNotifications.show("Imported " + n + " filtered packet(s).", OK);
                    }
                }
                case "join" -> { if (line.blob != null) joinServer(line.blob); }
                case "importsteps" -> { if (line.blob != null) importSteps(line.blob); }
                case "toeditor" -> { if (line.blob != null) stepsToEditor(line.blob); }
                case "previewmodule" -> { if (line.blob != null) previewModule(line.blob); }
                case "applymodule" -> { if (line.blob != null) applyModule(line.blob); }
                case "copy" -> copy(line);
                case "inspect" -> inspect(line);
                case "view" -> view(line);
                default -> {}
            }
        } catch (Throwable t) {
            DihNotifications.show("Action failed.", ERR);
        }
    }

    private static void addToQueue(MmMessages.PacketOffer offer) {
        int n = MmShare.addToQueue(offer);
        if (n < 0) { DihNotifications.show("Could not read shared queue.", ERR); return; }
        DihNotifications.show("Added " + n + " packet(s) to your queue.", OK);
    }

    private static void importSteps(MmMessages.BlobOffer b) {
        MmMessages.MacroOffer offer = new MmMessages.MacroOffer();
        offer.hash = b.data;
        offer.macroName = b.friendlyName;
        MmShare.importMacro(offer);
    }

    private static void stepsToEditor(MmMessages.BlobOffer b) {
        DihMacro macro = DihClipboardHelper.deserializeMacroFromBase64(b.data);
        if (macro == null) { DihNotifications.show("Could not read the shared steps.", ERR); return; }
        DihMacroEditorOverlay ed = DihMacroEditorOverlay.getSharedOverlay();
        if (ed == null) return;
        ed.openForImport(macro);
        openInHost(ed, false);
    }

    private static void previewModule(MmMessages.BlobOffer b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        DihModuleViewOverlay ov = new DihModuleViewOverlay(mc.font);
        if (ov.open(b)) openInHost(ov, true);
    }

    private static void applyModule(MmMessages.BlobOffer b) {
        int n = MmBlobs.applyModule(b);
        if (n < 0) { DihNotifications.show("Could not apply module settings.", ERR); return; }
        DihNotifications.show("Applied " + n + " setting(s) to " + MmBlobs.moduleName(b) + ".", OK);
    }

    public static String clipboardTextFor(MmChatLine line) {
        return switch (line.kind) {
            case MACRO_CARD -> line.macro.hash;
            case PACKET_CARD -> line.packet.data;
            case COMMAND_CARD -> MatchmakingManager.renderCommandOffer(line.command);
            case BLOB_CARD -> switch (line.blob.kind) {
                case "server" -> MmBlobs.serverIp(line.blob);
                case "position" -> line.blob.data;

                case "item", "filter", "module" -> MmBlobs.encodeOffer(line.blob);
                default -> line.blob.data;
            };
            default -> line.text;
        };
    }

    private static void copy(MmChatLine line) {
        String data = clipboardTextFor(line);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.keyboardHandler.setClipboard(data == null ? "" : data);
        DihNotifications.copied("Copied to clipboard.");
    }

    private static void inspect(MmChatLine line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        switch (line.kind) {
            case MACRO_CARD -> {
                DihMacro macro = DihClipboardHelper.deserializeMacroFromBase64(line.macro.hash);
                if (macro == null) { DihNotifications.show("Could not read macro.", ERR); return; }
                DihMacroEditorOverlay ed = DihMacroEditorOverlay.getSharedOverlay();
                if (ed == null) return;
                ed.openForImport(macro);
                openInHost(ed, false);
            }
            case PACKET_CARD -> {
                DihPacketLoggerOverlay.LogEntry entry = MmShare.inspectableEntry(line.packet);
                if (entry == null) { DihNotifications.show("Could not rebuild packet.", ERR); return; }
                DihPacketInspectOverlay ov = new DihPacketInspectOverlay(mc.font);
                ov.open(entry, 60, 60);
                openInHost(ov, true);
            }
            case BLOB_CARD -> {
                if ("item".equals(line.blob.kind)) {
                    ItemStack st = MmBlobs.decodeItem(line.blob);
                    if (st == null || st.isEmpty()) { DihNotifications.show("Could not read item.", ERR); return; }
                    DihItemNbtInspectOverlay ov = DihItemNbtInspectOverlay.getSharedOverlay(mc.font);
                    if (ov == null) return;
                    ov.open(st, 60, 60);
                    openInHost(ov, true);
                } else if ("filter".equals(line.blob.kind)) {
                    DihFilterViewOverlay ov = new DihFilterViewOverlay(mc.font);
                    if (ov.open(line.blob)) openInHost(ov, true);
                }
            }
            default -> {}
        }
    }

    private static void view(MmChatLine line) {
        if (line.blob == null || !"gui".equals(line.blob.kind)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        DihGuiViewOverlay ov = new DihGuiViewOverlay(mc.font);
        if (ov.open(line.blob)) openInHost(ov, true);
    }

    private static void joinServer(MmMessages.BlobOffer b) {
        if (!"server".equals(b.kind)) return;
        Minecraft mc = Minecraft.getInstance();
        Screen ret = mc == null ? null : mc.gui.screen();
        confirmJoinServer(MmBlobs.serverIp(b), MmBlobs.serverName(b), ret);
    }

    public static void confirmJoinServer(String rawIp, String displayName, Screen returnScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        String ip = MmText.clean(rawIp, 64);
        if (ip.isBlank()) { DihNotifications.show("No server address.", WARN); return; }
        if (MatchmakingManager.alreadyOn(ip)) { DihNotifications.show("You're already on that server.", WARN); return; }
        String shown = (displayName == null || displayName.isBlank()) ? ip : MmText.clean(displayName, 64);
        mc.gui.setScreen(new ConfirmScreen(ok -> {
            if (ok) {
                ServerData data = new ServerData(shown, ip, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(returnScreen, mc, ServerAddress.parseString(ip), data, false, null);
            } else {
                mc.gui.setScreen(returnScreen);
            }
        }, Component.literal("Join " + shown + "?"),
           Component.literal(ip + "\nOnly connect to servers you trust.")));
    }

    private static void openInHost(IDihOverlay ov, boolean background) {
        if (background) DihOverlayManager.get().register(ov, IDihOverlay.OverlayScope.BACKGROUND_STATUS);
        else DihOverlayManager.get().register(ov);
        DihOverlayManager.get().bringToFront(ov);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.gui.setScreen(new DihOverlayHostScreen(ov, null, false, true));
    }

    static boolean isTpa(MmChatLine line) {
        return line.command != null && line.command.body != null
            && line.command.body.trim().toLowerCase(Locale.ROOT).startsWith("tpa");
    }
}
