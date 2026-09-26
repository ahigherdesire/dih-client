package dihclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DihNormalPacketAnalyzer {
    private static final int MAX_CHANGED_SLOTS = 16;
    private static final int MAX_CONTENT_ITEMS = 12;

    private DihNormalPacketAnalyzer() {
    }

    public static Analysis analyze(DihPacketLoggerOverlay.LogEntry entry) {
        Builder out = new Builder();
        if (entry == null) return out.build(false, "fallback");
        appendWire(entry, out);

        WireDecode wireDecode = decodeFromCapturedWire(entry);
        Packet<?> packet = wireDecode.packet != null ? wireDecode.packet : entry.packetRef;
        if (packet == null) {
            if (wireDecode.message != null && !wireDecode.message.isBlank()) {
                out.decoded("Wire Decode: " + wireDecode.message, wireDecode.success ? DihColors.packetGreen() : DihColors.packetYellow());
            }
            return out.build(false, "fallback");
        }

        boolean complete = decodeRealityAndMeaning(packet, out, wireDecode);
        return out.build(complete, complete ? "complete" : out.hasDecoded() ? wireDecode.status() : "fallback");
    }

    public static String exportDecodedText(DihPacketLoggerOverlay.LogEntry entry) {
        Analysis analysis = analyze(entry);
        if (!analysis.hasDecoded() && !analysis.hasContext()) return "";
        StringBuilder out = new StringBuilder();
        if (analysis.hasDecoded()) {
            out.append("[Reality]\n");
            appendPlain(out, analysis.decodedLines());
        }
        if (analysis.hasContext()) {
            out.append("\n[Meaning]\n");
            appendPlain(out, analysis.contextLines());
        }
        return out.toString().stripTrailing();
    }

    private static void appendPlain(StringBuilder out, List<DihPacketInspector.InspectionLine> lines) {
        for (DihPacketInspector.InspectionLine line : lines) {
            if (line != null) out.append(line.getText()).append('\n');
        }
    }

    private static WireDecode decodeFromCapturedWire(DihPacketLoggerOverlay.LogEntry entry) {
        if (entry == null || entry.packetRef == null || entry.packetClass == null) return WireDecode.none();
        if (!Packet.class.isAssignableFrom(entry.packetClass)) return WireDecode.none();
        DihPacketCapture.PacketSnapshot snapshot = DihPacketCapture.snapshot(entry.packetRef);
        if (snapshot == null || snapshot.plaintextBytes().length == 0) return WireDecode.none();
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) entry.packetClass;
            DihPacketCodecIo.DecodedPacket decoded = DihPacketCodecIo.decodeFullPacket(packetClass, snapshot.plaintextBytes(), snapshot.protocolPhase());
            return WireDecode.success(decoded.packet(), "decoded from captured plaintext with Minecraft STREAM_CODEC");
        } catch (Throwable t) {
            return WireDecode.failure("captured plaintext was kept, but STREAM_CODEC decode failed: " + safe(t));
        }
    }

    private static boolean decodeRealityAndMeaning(Packet<?> packet, Builder out, WireDecode wireDecode) {
        if (wireDecode != null && wireDecode.message != null && !wireDecode.message.isBlank()) {
            out.decoded("Wire Decode: " + wireDecode.message, wireDecode.success ? DihColors.packetGreen() : DihColors.packetYellow());
        }
        DihPacketDecodedView view = DihPacketDecodedView.decode(packet);
        appendReality(view, out);
        appendMeaning(packet, view, out);
        return view.complete();
    }

    private static void appendReality(DihPacketDecodedView view, Builder out) {
        if (view == null) {
            out.decoded("Status: fallback", DihColors.packetYellow());
            out.decoded("No packet object was available.", DihColors.dangerText());
            return;
        }
        DihPacketSchemaRegistry.PacketSchema schema = view.schema();
        out.decoded("Status: " + view.status(), view.complete() ? DihColors.packetGreen() : DihColors.packetYellow());
        if (schema != null) {
            out.decoded("Schema: " + (view.sourceBacked() ? "Minecraft source-backed schema" : "reflection fallback"), DihColors.textPrimary());
            out.decoded("Protocol: " + schema.protocol() + " | Direction: " + schema.direction(), DihColors.textSecondary());
            out.decoded("Codec: " + schema.codecStyle() + " | Source: " + schema.source(), DihColors.textSecondary());
            if (!schema.packetType().isBlank()) out.decoded("Packet Type: " + schema.packetType(), DihColors.textSecondary());
            if (schema.inheritedFallback()) out.decoded("Field Note: using parent packet schema for nested packet variant", DihColors.packetYellow());
        } else {
            out.decoded("Schema: reflection fallback", DihColors.packetYellow());
        }
        if (!view.fallbackReason().isBlank()) out.decoded("Fallback Note: " + view.fallbackReason(), DihColors.textMuted());
        if (view.fields().isEmpty()) {
            out.decoded("Fields: none", DihColors.textMuted());
            return;
        }
        out.decoded("Fields: " + view.fields().size(), DihColors.textPrimary());
        for (DihPacketFieldValue field : view.fields()) {
            int color = field.readable() ? DihColors.packetWhite() : DihColors.dangerText();
            out.decoded(field.name() + " (" + field.javaType() + "): " + field.summary(), color);
            for (String detail : field.details()) {
                out.decoded("  " + detail, DihColors.textSecondary());
            }
        }
    }

    private static void appendMeaning(Packet<?> packet, DihPacketDecodedView view, Builder out) {
        if (packet instanceof ClientboundOpenScreenPacket open) {
            out.context("Server opens a handled screen.", DihColors.textPrimary());
            out.context("Container: #" + open.getContainerId() + " type=" + safe(open.getType()), DihColors.packetBlue());
            out.context("Title: " + quote(open.getTitle() == null ? "" : open.getTitle().getString()), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            out.context("Server replaces the full contents of a container.", DihColors.textPrimary());
            out.context("Container: #" + content.containerId() + " state=" + content.stateId(), DihColors.packetBlue());
            out.context("Slots: " + content.items().size() + " total, " + countNonEmpty(content.items()) + " non-empty", DihColors.textPrimary());
            out.context("Carried Item: " + DihPacketContextTracker.summarizeItem(content.carriedItem()), DihColors.successText());
            appendMeaningContentPreview(content.items(), out);
            return;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            out.context("Server updates one container slot.", DihColors.textPrimary());
            out.context("Container: #" + slot.getContainerId() + " state=" + slot.getStateId(), DihColors.packetBlue());
            out.context("Slot: " + slot.getSlot(), DihColors.textPrimary());
            out.context("Item: " + DihPacketContextTracker.summarizeItem(slot.getItem()), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            out.context("Server sets the carried cursor item.", DihColors.textPrimary());
            out.context("Cursor: " + DihPacketContextTracker.summarizeItem(cursor.contents()), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            out.context("Server updates a player inventory slot.", DihColors.textPrimary());
            out.context("Inventory Slot: " + inventory.slot(), DihColors.packetBlue());
            out.context("Item: " + DihPacketContextTracker.summarizeItem(inventory.contents()), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundSetHeldSlotPacket held) {
            out.context("Server changes the selected hotbar slot.", DihColors.textPrimary());
            out.context("Selected Slot: " + held.slot(), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            out.context("Client changes the selected hotbar slot.", DihColors.textPrimary());
            out.context("Selected Slot: " + carried.getSlot(), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundContainerClickPacket click) {
            out.context("Client clicks inside a handled screen.", DihColors.textPrimary());
            out.context("Container: #" + click.containerId() + " state=" + click.stateId(), DihColors.packetBlue());
            out.context("Input: " + describeInput(click.containerInput()) + " (" + click.containerInput() + ")", DihColors.successText());
            out.context("Slot: " + click.slotNum() + " | Button: " + describeButton(click.containerInput(), click.buttonNum()), DihColors.textPrimary());
            out.context("Changed Slots: " + click.changedSlots().size(), DihColors.textPrimary());
            int shown = 0;
            for (Int2ObjectMap.Entry<HashedStack> slot : click.changedSlots().int2ObjectEntrySet()) {
                if (shown >= MAX_CHANGED_SLOTS) {
                    out.context("  ... +" + (click.changedSlots().size() - shown) + " more", DihColors.textMuted());
                    break;
                }
                out.context("  #" + slot.getIntKey() + " -> " + DihPacketContextTracker.summarizeHashedStack(slot.getValue()), DihColors.textSecondary());
                shown++;
            }
            out.context("Carried Item: " + DihPacketContextTracker.summarizeHashedStack(click.carriedItem()), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundContainerButtonClickPacket button) {
            out.context("Client presses a handled-screen button.", DihColors.textPrimary());
            out.context("Container: #" + safe(call(button, "containerId", "getContainerId", "getSyncId")), DihColors.packetBlue());
            out.context("Button Id: " + safe(call(button, "buttonId", "getButtonId")), DihColors.textPrimary());
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
            out.context(packet instanceof ServerboundContainerClosePacket ? "Client closes a handled screen." : "Server closes a handled screen.",
                DihColors.textPrimary());
            Object id = packet instanceof ClientboundContainerClosePacket close ? close.getContainerId() : call(packet, "containerId", "getContainerId");
            out.context("Container: #" + safe(id), DihColors.packetBlue());
            return;
        }
        if (packet instanceof ClientboundContainerSetDataPacket data) {
            out.context("Server updates a container property/data slot.", DihColors.textPrimary());
            out.context("Container: #" + safe(call(data, "containerId", "getContainerId", "getSyncId")), DihColors.packetBlue());
            out.context("Property: " + safe(call(data, "id", "getId", "propertyId", "getPropertyId"))
                + " = " + safe(call(data, "value", "getValue")), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
            out.context("Client sets a creative inventory slot.", DihColors.textPrimary());
            out.context("Slot: " + safe(call(creative, "slot", "getSlot")), DihColors.packetBlue());
            out.context("Stack: " + summarizeMaybeItem(call(creative, "itemStack", "stack", "getItem", "getStack")), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundSelectBundleItemPacket bundle) {
            out.context("Client selects an item inside a bundle.", DihColors.textPrimary());
            out.context("Slot: " + safe(call(bundle, "slot", "getSlot"))
                + " | Index: " + safe(call(bundle, "selectedItemIndex", "index", "getIndex")), DihColors.packetBlue());
            return;
        }

        if (packet instanceof ClientboundPlayerPositionPacket position) {
            PositionMoveRotation change = position.change();
            out.context("Server sends an authoritative player position/rotation correction.", DihColors.textPrimary());
            out.context("Teleport Id: " + position.id(), DihColors.packetBlue());
            out.context("Relative Flags: " + formatRelatives(position.relatives()), DihColors.textPrimary());
            out.context("Encoded Position: " + formatVec3(change.position()), DihColors.textPrimary());
            out.context("Encoded Velocity Delta: " + formatVec3(change.deltaMovement()), DihColors.textPrimary());
            out.context("Encoded Rotation: yaw=" + formatFloat(change.yRot()) + ", pitch=" + formatFloat(change.xRot()), DihColors.textPrimary());
            out.context("Expected Reply: ServerboundAcceptTeleportation #" + position.id(), DihColors.packetOrange());
            return;
        }
        if (packet instanceof ServerboundMovePlayerPacket move) {
            out.context("Client sends player movement state.", DihColors.textPrimary());
            out.context("Variant: " + move.getClass().getSimpleName().replace("ServerboundMovePlayerPacket$", ""), DihColors.textPrimary());
            out.context("Has Position: " + move.hasPosition() + " | Has Rotation: " + move.hasRotation(), DihColors.textPrimary());
            out.context("On Ground: " + move.isOnGround() + " | Horizontal Collision: " + move.horizontalCollision(), DihColors.textPrimary());
            out.context("Encoded Position: x=" + move.getX(0.0) + ", y=" + move.getY(0.0) + ", z=" + move.getZ(0.0), DihColors.successText());
            out.context("Encoded Rotation: yaw=" + formatFloat(move.getYRot(0.0F)) + ", pitch=" + formatFloat(move.getXRot(0.0F)), DihColors.successText());
            return;
        }
        if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
            out.context("Client acknowledges a server teleport/correction.", DihColors.textPrimary());
            out.context("Teleport Id: " + DihPackets.teleportId(ack), DihColors.packetBlue());
            return;
        }

        if (packet instanceof ClientboundLoginPacket login) {
            out.context("Server enters the play world.", DihColors.textPrimary());
            out.context("Player Entity Id: " + login.playerId(), DihColors.packetBlue());
            out.context("Dimension: " + login.commonPlayerSpawnInfo().dimension().identifier(), DihColors.successText());
            out.context("Game Mode: " + login.commonPlayerSpawnInfo().gameType(), DihColors.textPrimary());
            out.context("Chunk Radius: " + login.chunkRadius() + " | Simulation Distance: " + login.simulationDistance(), DihColors.textPrimary());
            return;
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            out.context("Server respawns player or changes dimension/world.", DihColors.textPrimary());
            out.context("Dimension: " + respawn.commonPlayerSpawnInfo().dimension().identifier(), DihColors.successText());
            out.context("Game Mode: " + respawn.commonPlayerSpawnInfo().gameType(), DihColors.textPrimary());
            out.context("Keep Data Mask: " + respawn.dataToKeep(), DihColors.textPrimary());
            return;
        }
        if (packet instanceof ClientboundTransferPacket transfer) {
            out.context("Server requests client transfer.", DihColors.textPrimary());
            out.context("Target: " + transfer.host() + ":" + transfer.port(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundStartConfigurationPacket) {
            out.context("Server starts configuration phase.", DihColors.textPrimary());
            return;
        }
        if (packet instanceof ClientboundFinishConfigurationPacket) {
            out.context("Server finishes configuration phase.", DihColors.textPrimary());
            return;
        }
        if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            out.context("Server changes chunk cache center.", DihColors.textPrimary());
            out.context("Chunk Center: " + center.getX() + ", " + center.getZ(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            out.context("Server changes chunk render/cache radius.", DihColors.textPrimary());
            out.context("Radius: " + radius.getRadius(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundSetSimulationDistancePacket simulation) {
            out.context("Server changes simulation distance.", DihColors.textPrimary());
            out.context("Simulation Distance: " + simulation.simulationDistance(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            out.context("Client should unload a chunk.", DihColors.textPrimary());
            out.context("Chunk: " + forget.pos().x() + ", " + forget.pos().z(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            out.context("Server sends chunk data with light data.", DihColors.textPrimary());
            out.context("Chunk: " + DihPackets.chunkX(chunk) + ", " + DihPackets.chunkZ(chunk), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundGameEventPacket gameEvent) {
            out.context("Server sends a game/world event.", DihColors.textPrimary());
            out.context("Event: " + safe(gameEvent.getEvent()) + " | Value: " + gameEvent.getParam(), DihColors.successText());
            return;
        }
        if (packet instanceof ClientboundResourcePackPushPacket pack) {
            out.context("Server requests a resource pack.", DihColors.textPrimary());
            out.context("Id: " + pack.id(), DihColors.packetBlue());
            out.context("Url: " + pack.url(), DihColors.successText());
            out.context("Required: " + pack.required() + " | Hash: " + pack.hash(), DihColors.textPrimary());
            return;
        }
        if (packet instanceof ClientboundResourcePackPopPacket pack) {
            out.context("Server removes a resource pack.", DihColors.textPrimary());
            out.context("Id: " + pack.id().map(Object::toString).orElse("latest / top"), DihColors.packetBlue());
            return;
        }
        if (packet instanceof ClientboundDisconnectPacket disconnect) {
            out.context("Server disconnects the client.", DihColors.textPrimary());
            out.context("Reason: " + safe(disconnect.reason()), DihColors.dangerText());
            return;
        }

        if (view != null && view.complete()) {
            out.context("No specialist meaning layer yet; Reality shows the exact source-backed packet fields.", DihColors.textMuted());
        }
    }

    private static void appendMeaningContentPreview(List<ItemStack> items, Builder out) {
        int shown = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.isEmpty()) continue;
            if (shown == 0) out.context("Non-empty Preview:", DihColors.textPrimary());
            if (shown >= MAX_CONTENT_ITEMS) {
                out.context("  ... more non-empty slots hidden", DihColors.textMuted());
                return;
            }
            out.context("  #" + i + " " + DihPacketContextTracker.summarizeItem(stack), DihColors.textSecondary());
            shown++;
        }
    }

    private static boolean decodePacket(DihPacketLoggerOverlay.LogEntry entry, Packet<?> packet, Builder out) {
        DihPacketContextTracker.Capture capture = entry.packetContext;
        DihPacketContextTracker.Snapshot before = capture == null ? DihPacketContextTracker.Snapshot.EMPTY : capture.before();
        DihPacketContextTracker.Snapshot after = capture == null ? DihPacketContextTracker.Snapshot.EMPTY : capture.after();

        if (packet instanceof ClientboundOpenScreenPacket open) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server opened a handled screen", DihColors.textPrimary());
            out.decoded("Container Id: " + open.getContainerId(), DihColors.packetBlue());
            out.decoded("Screen Type: " + safe(open.getType()), DihColors.textPrimary());
            out.decoded("Title: " + quote(open.getTitle() == null ? "" : open.getTitle().getString()), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: server replaced full container contents", DihColors.textPrimary());
            out.decoded("Container Id: " + content.containerId(), DihColors.packetBlue());
            out.decoded("State Id: " + content.stateId(), DihColors.textSecondary());
            out.decoded("Slots: " + content.items().size() + " total, " + countNonEmpty(content.items()) + " non-empty", DihColors.textPrimary());
            out.decoded("Container Area: " + describeContainerArea(after), DihColors.textPrimary());
            out.decoded("Cursor: " + DihPacketContextTracker.summarizeItem(content.carriedItem()), DihColors.successText());
            appendContentPreview(content.items(), out);
            return true;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: server updated one container slot", DihColors.textPrimary());
            out.decoded("Container Id: " + slot.getContainerId(), DihColors.packetBlue());
            out.decoded("State Id: " + slot.getStateId(), DihColors.textSecondary());
            out.decoded("Slot: " + slot.getSlot() + " (" + DihPacketContextTracker.slotArea(after, slot.getSlot()) + ")", DihColors.textPrimary());
            out.decoded("Before: " + before.slotItem(slot.getSlot()), DihColors.textMuted());
            out.decoded("After: " + DihPacketContextTracker.summarizeItem(slot.getItem()), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: server updated carried cursor item", DihColors.textPrimary());
            out.decoded("Before: " + before.cursorItem(), DihColors.textMuted());
            out.decoded("After: " + DihPacketContextTracker.summarizeItem(cursor.contents()), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: server updated a player inventory slot", DihColors.textPrimary());
            out.decoded("Inventory Slot: " + inventory.slot(), DihColors.packetBlue());
            out.decoded("Before: " + before.playerInventorySlots().getOrDefault(inventory.slot(), "unknown"), DihColors.textMuted());
            out.decoded("After: " + DihPacketContextTracker.summarizeItem(inventory.contents()), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundSetHeldSlotPacket held) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: server selected held hotbar slot", DihColors.textPrimary());
            out.decoded("Before: " + before.selectedHotbarSlot(), DihColors.textMuted());
            out.decoded("After: " + held.slot(), DihColors.successText());
            return true;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: client selected hotbar slot", DihColors.textPrimary());
            out.decoded("Before: " + before.selectedHotbarSlot(), DihColors.textMuted());
            out.decoded("After: " + carried.getSlot(), DihColors.successText());
            return true;
        }
        if (packet instanceof ServerboundContainerClickPacket click) {
            decodeContainerClick(click, before, after, out);
            return false;
        }
        if (packet instanceof ServerboundContainerButtonClickPacket button) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: client clicked a handled-screen button", DihColors.textPrimary());
            out.decoded("Container Id: " + call(button, "containerId", "getContainerId", "getSyncId"), DihColors.packetBlue());
            out.decoded("Button Id: " + call(button, "buttonId", "getButtonId"), DihColors.textPrimary());
            return true;
        }
        if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: " + (packet instanceof ServerboundContainerClosePacket ? "client closed a handled screen" : "server closed a handled screen"),
                DihColors.textPrimary());
            Object id = packet instanceof ClientboundContainerClosePacket close ? close.getContainerId() : call(packet, "containerId", "getContainerId");
            out.decoded("Container Id: " + safe(id), DihColors.packetBlue());
            if (!before.screenTitle().isBlank()) out.decoded("Closed Screen: " + quote(before.screenTitle()), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundContainerSetDataPacket data) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server updated container property/data", DihColors.textPrimary());
            out.decoded("Container Id: " + call(data, "containerId", "getContainerId", "getSyncId"), DihColors.packetBlue());
            out.decoded("Property: " + call(data, "id", "getId", "propertyId", "getPropertyId"), DihColors.textPrimary());
            out.decoded("Value: " + call(data, "value", "getValue"), DihColors.successText());
            return true;
        }
        if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: client set a creative inventory slot", DihColors.textPrimary());
            out.decoded("Slot: " + call(creative, "slot", "getSlot"), DihColors.packetBlue());
            out.decoded("Stack: " + summarizeMaybeItem(call(creative, "itemStack", "stack", "getItem", "getStack")), DihColors.successText());
            return true;
        }
        if (packet instanceof ServerboundSelectBundleItemPacket bundle) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: client selected an item inside a bundle", DihColors.textPrimary());
            out.decoded("Slot: " + call(bundle, "slot", "getSlot"), DihColors.packetBlue());
            out.decoded("Selected Item Index: " + call(bundle, "selectedItemIndex", "index", "getIndex"), DihColors.textPrimary());
            return true;
        }

        if (packet instanceof ClientboundPlayerPositionPacket position) {
            decodeServerPosition(position, before, after, out);
            return true;
        }
        if (packet instanceof ServerboundMovePlayerPacket move) {
            decodeClientMove(move, before, after, out);
            return true;
        }
        if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
            out.decoded("Status: context-aware", DihColors.packetGreen());
            out.decoded("Meaning: client acknowledged server teleport/correction", DihColors.textPrimary());
            out.decoded("Teleport Id: " + DihPackets.teleportId(ack), DihColors.packetBlue());
            if (before.lastTeleportId() >= 0) {
                out.decoded("Pending Before: " + before.lastTeleportId()
                    + (before.lastTeleportId() == DihPackets.teleportId(ack) ? " (matched)" : " (different)"), DihColors.textPrimary());
            }
            return true;
        }

        if (packet instanceof ClientboundLoginPacket login) {
            decodeLogin(login, out);
            return true;
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: respawn or dimension/world change", DihColors.textPrimary());
            out.decoded("Dimension: " + respawn.commonPlayerSpawnInfo().dimension().identifier(), DihColors.successText());
            out.decoded("Game Mode: " + respawn.commonPlayerSpawnInfo().gameType(), DihColors.textPrimary());
            out.decoded("Previous Game Mode: " + respawn.commonPlayerSpawnInfo().previousGameType(), DihColors.textSecondary());
            out.decoded("Keep Data Mask: " + respawn.dataToKeep(), DihColors.textPrimary());
            return true;
        }
        if (packet instanceof ClientboundTransferPacket transfer) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server requested client transfer", DihColors.textPrimary());
            out.decoded("Target: " + transfer.host() + ":" + transfer.port(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundStartConfigurationPacket) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: play connection is switching into configuration phase", DihColors.textPrimary());
            return true;
        }
        if (packet instanceof ClientboundFinishConfigurationPacket) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: configuration phase finished", DihColors.textPrimary());
            return true;
        }
        if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server changed chunk cache center", DihColors.textPrimary());
            out.decoded("Chunk Center: " + center.getX() + ", " + center.getZ(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server changed chunk render/cache radius", DihColors.textPrimary());
            out.decoded("Radius: " + radius.getRadius(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundSetSimulationDistancePacket simulation) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server changed simulation distance", DihColors.textPrimary());
            out.decoded("Simulation Distance: " + simulation.simulationDistance(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: client should unload a chunk", DihColors.textPrimary());
            out.decoded("Chunk: " + forget.pos().x() + ", " + forget.pos().z(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            out.decoded("Status: fallback", DihColors.packetYellow());
            out.decoded("Meaning: server sent chunk data with light data", DihColors.textPrimary());
            out.decoded("Chunk: " + DihPackets.chunkX(chunk) + ", " + DihPackets.chunkZ(chunk), DihColors.successText());
            out.decoded("Chunk Data: " + safe(DihPackets.chunkData(chunk)), DihColors.textSecondary());
            out.decoded("Light Data: " + safe(DihPackets.lightData(chunk)), DihColors.textSecondary());
            return false;
        }
        if (packet instanceof ClientboundGameEventPacket gameEvent) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: game/world event", DihColors.textPrimary());
            out.decoded("Event: " + safe(gameEvent.getEvent()), DihColors.textPrimary());
            out.decoded("Value: " + gameEvent.getParam(), DihColors.successText());
            return true;
        }
        if (packet instanceof ClientboundResourcePackPushPacket pack) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server requested resource pack", DihColors.textPrimary());
            out.decoded("Id: " + pack.id(), DihColors.packetBlue());
            out.decoded("Url: " + pack.url(), DihColors.successText());
            out.decoded("Hash: " + pack.hash(), DihColors.textSecondary());
            out.decoded("Required: " + pack.required(), DihColors.textPrimary());
            pack.prompt().ifPresent(component -> out.decoded("Prompt: " + quote(component.getString()), DihColors.textPrimary()));
            return true;
        }
        if (packet instanceof ClientboundResourcePackPopPacket pack) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server removed resource pack", DihColors.textPrimary());
            out.decoded("Id: " + pack.id().map(Object::toString).orElse("latest / top"), DihColors.packetBlue());
            return true;
        }
        if (packet instanceof ClientboundDisconnectPacket disconnect) {
            out.decoded("Status: complete", DihColors.packetGreen());
            out.decoded("Meaning: server disconnected the client", DihColors.textPrimary());
            out.decoded("Reason: " + safe(disconnect.reason()), DihColors.dangerText());
            return true;
        }

        return false;
    }

    private static void decodeContainerClick(ServerboundContainerClickPacket click, DihPacketContextTracker.Snapshot before,
                                             DihPacketContextTracker.Snapshot after, Builder out) {
        out.decoded("Status: context-aware", DihColors.packetGreen());
        out.decoded("Meaning: client clicked inside a handled screen", DihColors.textPrimary());
        out.decoded("Container Id: " + click.containerId(), DihColors.packetBlue());
        out.decoded("State Id: " + click.stateId(), DihColors.textSecondary());
        out.decoded("Input: " + describeInput(click.containerInput()) + " (" + click.containerInput() + ")", DihColors.successText());
        out.decoded("Slot: " + click.slotNum() + " (" + DihPacketContextTracker.slotArea(before, click.slotNum()) + ")", DihColors.textPrimary());
        out.decoded("Button: " + describeButton(click.containerInput(), click.buttonNum()), DihColors.textPrimary());
        out.decoded("Slot Before: " + before.slotItem(click.slotNum()), DihColors.textMuted());
        out.decoded("Slot After: " + after.slotItem(click.slotNum()), DihColors.successText());
        out.decoded("Cursor Before: " + before.cursorItem(), DihColors.textMuted());
        out.decoded("Cursor After: " + DihPacketContextTracker.summarizeHashedStack(click.carriedItem()), DihColors.successText());
        out.decoded("Changed Slots: " + click.changedSlots().size(), DihColors.textPrimary());
        int shown = 0;
        for (Int2ObjectMap.Entry<HashedStack> slot : click.changedSlots().int2ObjectEntrySet()) {
            if (shown >= MAX_CHANGED_SLOTS) {
                out.decoded("  ... +" + (click.changedSlots().size() - shown) + " more", DihColors.textMuted());
                break;
            }
            out.decoded("  #" + slot.getIntKey() + " " + DihPacketContextTracker.slotArea(before, slot.getIntKey())
                + " -> " + DihPacketContextTracker.summarizeHashedStack(slot.getValue()), DihColors.textSecondary());
            shown++;
        }
    }

    private static void decodeServerPosition(ClientboundPlayerPositionPacket position, DihPacketContextTracker.Snapshot before,
                                             DihPacketContextTracker.Snapshot after, Builder out) {
        PositionMoveRotation change = position.change();
        out.decoded("Status: context-aware", DihColors.packetGreen());
        out.decoded("Meaning: server corrected/teleported local player", DihColors.textPrimary());
        out.decoded("Teleport Id: " + position.id(), DihColors.packetBlue());
        out.decoded("Relative Flags: " + formatRelatives(position.relatives()), DihColors.textPrimary());
        out.decoded("Raw Position Change: " + formatVec3(change.position()), DihColors.textPrimary());
        out.decoded("Raw Delta Movement: " + formatVec3(change.deltaMovement()), DihColors.textPrimary());
        out.decoded("Raw Rotation: yaw=" + formatFloat(change.yRot()) + ", pitch=" + formatFloat(change.xRot()), DihColors.textPrimary());
        if (before.serverPosition() != null) out.decoded("Before Server Pos: " + DihPacketContextTracker.formatPosition(before.serverPosition()), DihColors.textMuted());
        if (after.serverPosition() != null) out.decoded("Computed After: " + DihPacketContextTracker.formatPosition(after.serverPosition()), DihColors.successText());
        out.decoded("Ack Expected: ServerboundAcceptTeleportation #" + position.id(), DihColors.packetOrange());
    }

    private static void decodeClientMove(ServerboundMovePlayerPacket move, DihPacketContextTracker.Snapshot before,
                                         DihPacketContextTracker.Snapshot after, Builder out) {
        out.decoded("Status: context-aware", DihColors.packetGreen());
        out.decoded("Meaning: client player movement update", DihColors.textPrimary());
        out.decoded("Variant: " + move.getClass().getSimpleName().replace("ServerboundMovePlayerPacket$", ""), DihColors.textPrimary());
        out.decoded("Has Position: " + move.hasPosition(), DihColors.textPrimary());
        out.decoded("Has Rotation: " + move.hasRotation(), DihColors.textPrimary());
        out.decoded("On Ground: " + move.isOnGround(), DihColors.textPrimary());
        out.decoded("Horizontal Collision: " + move.horizontalCollision(), DihColors.textPrimary());
        if (before.clientPosition() != null) out.decoded("Before: " + DihPacketContextTracker.formatPosition(before.clientPosition()), DihColors.textMuted());
        if (after.clientPosition() != null) {
            out.decoded("After: " + DihPacketContextTracker.formatPosition(after.clientPosition()), DihColors.successText());
            if (before.clientPosition() != null) {
                out.decoded("Distance Delta: " + String.format(Locale.ROOT, "%.4f blocks", before.clientPosition().distanceTo(after.clientPosition())),
                    DihColors.packetYellow());
            }
        }
    }

    private static void decodeLogin(ClientboundLoginPacket login, Builder out) {
        out.decoded("Status: complete", DihColors.packetGreen());
        out.decoded("Meaning: entered play world", DihColors.textPrimary());
        out.decoded("Player Entity Id: " + login.playerId(), DihColors.packetBlue());
        out.decoded("Dimension: " + login.commonPlayerSpawnInfo().dimension().identifier(), DihColors.successText());
        out.decoded("Game Mode: " + login.commonPlayerSpawnInfo().gameType(), DihColors.textPrimary());
        out.decoded("Hardcore: " + login.hardcore(), DihColors.textPrimary());
        out.decoded("Known Levels: " + login.levels().size(), DihColors.textPrimary());
        out.decoded("Chunk Radius: " + login.chunkRadius(), DihColors.textPrimary());
        out.decoded("Simulation Distance: " + login.simulationDistance(), DihColors.textPrimary());
        out.decoded("Reduced Debug: " + login.reducedDebugInfo(), DihColors.textPrimary());
        out.decoded("Secure Chat Enforced: " + login.enforcesSecureChat(), DihColors.textPrimary());
    }

    private static void appendContext(DihPacketContextTracker.Capture capture, Builder out) {
        if (capture == null || !capture.relevant()) return;
        DihPacketContextTracker.Snapshot before = capture.before();
        DihPacketContextTracker.Snapshot after = capture.after();
        out.context("Context Status: " + (before.known() ? "tracked" : "started mid-stream / partial"), before.known() ? DihColors.packetGreen() : DihColors.packetYellow());
        if (!after.protocolState().isBlank()) out.context("Protocol State: " + after.protocolState(), DihColors.textPrimary());
        if (after.containerId() >= 0 || !after.screenTitle().isBlank()) {
            out.context("Screen: #" + after.containerId() + " " + quote(after.screenTitle()) + " " + after.screenType(), DihColors.successText());
        }
        if (!after.dimension().isBlank()) out.context("Dimension: " + after.dimension(), DihColors.successText());
        if (after.chunkCenterX() != null && after.chunkCenterZ() != null) {
            out.context("Chunk Center: " + after.chunkCenterX() + ", " + after.chunkCenterZ(), DihColors.textPrimary());
        }
        if (after.chunkRadius() != null) out.context("Chunk Radius: " + after.chunkRadius(), DihColors.textPrimary());
        if (after.simulationDistance() != null) out.context("Simulation Distance: " + after.simulationDistance(), DihColors.textPrimary());
        if (after.lastTeleportId() >= 0) out.context("Pending Teleport Ack: " + after.lastTeleportId(), DihColors.packetOrange());
        if (!capture.changes().isEmpty()) {
            out.context("Changes:", DihColors.textPrimary());
            for (String change : capture.changes()) {
                out.context("  - " + change, DihColors.textSecondary());
            }
        }
    }

    private static void appendWire(DihPacketLoggerOverlay.LogEntry entry, Builder out) {
        if (entry == null || entry.packetRef == null) return;
        DihPacketCapture.PacketSnapshot snapshot = DihPacketCapture.snapshot(entry.packetRef);
        if (snapshot == null) return;
        out.wire("Protocol: " + snapshot.protocolPhase(), DihColors.textSecondary());
        out.wire("Direction: " + snapshot.direction(), "C2S".equalsIgnoreCase(snapshot.direction()) ? DihColors.packetOrange() : DihColors.packetCyan());
        if (snapshot.numericPacketId() >= 0) out.wire("Packet ID: " + snapshot.numericPacketId(), DihColors.textSecondary());
        if (snapshot.packetType() != null && !snapshot.packetType().isBlank()) out.wire("Packet Type: " + snapshot.packetType(), DihColors.textSecondary());
        byte[] bytes = snapshot.plaintextBytes();
        out.wire("Plaintext: " + bytes.length + " bytes " + DihPacketCapture.compactHex(bytes, 48), DihColors.textMuted());
    }

    private static void appendContentPreview(List<ItemStack> items, Builder out) {
        int shown = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.isEmpty()) continue;
            if (shown == 0) out.decoded("Non-empty Preview:", DihColors.textPrimary());
            if (shown >= MAX_CONTENT_ITEMS) {
                out.decoded("  ... more non-empty slots hidden", DihColors.textMuted());
                return;
            }
            out.decoded("  #" + i + " " + DihPacketContextTracker.summarizeItem(stack), DihColors.textSecondary());
            shown++;
        }
    }

    private static String describeContainerArea(DihPacketContextTracker.Snapshot snapshot) {
        if (snapshot == null || snapshot.slotCount() < 0) return "unknown";
        int containerSlots = Math.max(0, snapshot.containerSlotCount());
        int playerSlots = Math.max(0, snapshot.slotCount() - containerSlots);
        return containerSlots + " container slots + " + playerSlots + " player slots";
    }

    private static int countNonEmpty(Collection<ItemStack> items) {
        int count = 0;
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && !item.isEmpty()) count++;
            }
        }
        return count;
    }

    private static String describeInput(ContainerInput input) {
        if (input == null) return "unknown";
        return switch (input) {
            case PICKUP -> "PICKUP left/right click pickup/place";
            case QUICK_MOVE -> "QUICK_MOVE shift-click transfer";
            case SWAP -> "SWAP hotbar/offhand swap";
            case CLONE -> "CLONE creative middle-click";
            case THROW -> "THROW drop from slot";
            case QUICK_CRAFT -> "QUICK_CRAFT drag distribute";
            case PICKUP_ALL -> "PICKUP_ALL gather matching carried stack";
        };
    }

    private static String describeButton(ContainerInput input, int button) {
        if (input == ContainerInput.PICKUP) return button == 0 ? "0 left click" : button == 1 ? "1 right click" : String.valueOf(button);
        if (input == ContainerInput.QUICK_MOVE) return button == 0 ? "0 shift-left" : button == 1 ? "1 shift-right" : String.valueOf(button);
        if (input == ContainerInput.SWAP) return button == 40 ? "40 offhand" : button + " hotbar slot";
        if (input == ContainerInput.THROW) return button == 0 ? "0 drop one" : button == 1 ? "1 drop stack" : String.valueOf(button);
        return String.valueOf(button);
    }

    private static String summarizeMaybeItem(Object value) {
        if (value instanceof ItemStack stack) return DihPacketContextTracker.summarizeItem(stack);
        return safe(value);
    }

    private static String formatRelatives(Set<Relative> relatives) {
        if (relatives == null || relatives.isEmpty()) return "none / absolute";
        return relatives.toString();
    }

    private static String formatVec3(Vec3 vec) {
        if (vec == null) return "unknown";
        return String.format(Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f", vec.x, vec.y, vec.z);
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Object call(Object target, String... methodNames) {
        if (target == null || methodNames == null) return null;
        for (String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) continue;
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static String safe(Object value) {
        if (value == null) return "unknown";
        if (value instanceof ItemStack stack) return DihPacketContextTracker.summarizeItem(stack);
        try {
            if (value instanceof Optional<?> optional) return optional.map(DihNormalPacketAnalyzer::safe).orElse("empty");
            if (value instanceof net.minecraft.core.Holder<?> holder) {
                return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(String.valueOf(holder.value()));
            }
            if (value instanceof net.minecraft.world.item.Item item) return BuiltInRegistries.ITEM.getKey(item).toString();
            return String.valueOf(value);
        } catch (Throwable ignored) {
            return value.getClass().getSimpleName();
        }
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        String trimmed = value.length() > 96 ? value.substring(0, 93) + "..." : value;
        return "\"" + trimmed + "\"";
    }

    private record WireDecode(Packet<?> packet, boolean success, String message) {
        static WireDecode none() {
            return new WireDecode(null, false, "");
        }

        static WireDecode success(Packet<?> packet, String message) {
            return new WireDecode(packet, true, message);
        }

        static WireDecode failure(String message) {
            return new WireDecode(null, false, message);
        }

        String status() {
            return success ? "wire-codec" : "fallback";
        }
    }

    public record Analysis(List<DihPacketInspector.InspectionLine> decodedLines,
                           List<DihPacketInspector.InspectionLine> contextLines,
                           List<DihPacketInspector.InspectionLine> wireLines,
                           boolean complete,
                           String status) {
        public Analysis {
            decodedLines = decodedLines == null ? List.of() : List.copyOf(decodedLines);
            contextLines = contextLines == null ? List.of() : List.copyOf(contextLines);
            wireLines = wireLines == null ? List.of() : List.copyOf(wireLines);
            status = status == null ? "fallback" : status;
        }

        public boolean hasDecoded() {
            return !decodedLines.isEmpty();
        }

        public boolean hasContext() {
            return !contextLines.isEmpty();
        }

        public boolean hasWire() {
            return !wireLines.isEmpty();
        }
    }

    private static final class Builder {
        private final List<DihPacketInspector.InspectionLine> decoded = new ArrayList<>();
        private final List<DihPacketInspector.InspectionLine> context = new ArrayList<>();
        private final List<DihPacketInspector.InspectionLine> wire = new ArrayList<>();

        void decoded(String text, int color) {
            decoded.add(new DihPacketInspector.InspectionLine(text, color));
        }

        void context(String text, int color) {
            context.add(new DihPacketInspector.InspectionLine(text, color));
        }

        void wire(String text, int color) {
            wire.add(new DihPacketInspector.InspectionLine(text, color));
        }

        boolean hasDecoded() {
            return !decoded.isEmpty();
        }

        Analysis build(boolean complete, String status) {
            return new Analysis(decoded, context, wire, complete, status);
        }
    }
}
