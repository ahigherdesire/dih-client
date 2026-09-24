package dihclient.util.macro;

import dihclient.modules.PackHideState;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihContainerHold;
import dihclient.util.DihPacketClick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

public class PacketClickAction implements MacroAction {
    public DihPacketClick.Target target;
    public String mode = DihPacketClick.Mode.LEFT_CLICK.name();
    public int times = 1;
    public boolean queue = false;

    private transient boolean holdsContainer = false;

    public PacketClickAction() {}

    public PacketClickAction(DihPacketClick.Target target, int times, boolean queue) {
        this.target = target;
        this.times = Math.max(1, times);
        this.queue = queue;
        acquireHold();
    }

    public void setTarget(DihPacketClick.Target newTarget) {
        releaseHoldNoFlush();
        this.target = newTarget == null ? null : newTarget.withMode(effectiveMode());
        acquireHold();
    }

    private DihPacketClick.Mode effectiveMode() {
        if (target != null && (mode == null || mode.isBlank())) {
            return target.mode() == null ? DihPacketClick.Mode.LEFT_CLICK : target.mode();
        }
        return DihPacketClick.Mode.fromName(mode);
    }

    private void acquireHold() {
        if (target == null || holdsContainer) return;
        DihContainerHold.hold(target.containerId());
        holdsContainer = true;
    }

    private void releaseHold(ClientPacketListener conn) {
        if (!holdsContainer || target == null) {
            holdsContainer = false;
            return;
        }
        DihContainerHold.release(target.containerId(), conn);
        holdsContainer = false;
    }

    private void releaseHoldNoFlush() {
        if (!holdsContainer || target == null) {
            holdsContainer = false;
            return;
        }
        DihContainerHold.release(target.containerId(), null);
        holdsContainer = false;
    }

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (mc.getConnection() == null) {
            DihClientMessaging.sendPrefixed("§cNo network connection!");
            return;
        }
        if (target == null) {
            DihClientMessaging.sendPrefixed("§cPacket Click has no captured target.");
            return;
        }

        int count = Math.max(1, times);
        DihPacketClick.Target effectiveTarget = target.withMode(effectiveMode());
        for (int i = 0; i < count; i++) {
            ServerboundContainerClickPacket packet = effectiveTarget.buildPacket();
            if (queue) {
                dihclient.util.DihSharedState.get().enqueueExactPacket(packet);
            } else {
                dihclient.util.DihSharedState.get().sendPacketBypassDelay(mc.getConnection(), packet);
            }
        }

        releaseHold(mc.getConnection());
    }

    public void cancelHold() {
        releaseHoldNoFlush();
    }

    public void releasePendingClose(ClientPacketListener conn) {
        releaseHold(conn);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("mode", effectiveMode().name());
        tag.putInt("times", Math.max(1, times));
        tag.putBoolean("queue", queue);
        if (target != null) tag.put("target", target.withMode(effectiveMode()).toTag());
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        times = Math.max(1, tag.getIntOr("times", 1));
        queue = tag.getBooleanOr("queue", false);
        mode = tag.getStringOr("mode", "");
        target = tag.getCompound("target").map(DihPacketClick.Target::fromTag).orElse(null);
        if (mode == null || mode.isBlank()) {
            mode = target == null || target.mode() == null ? DihPacketClick.Mode.LEFT_CLICK.name() : target.mode().name();
        }
        if (target != null) target = target.withMode(effectiveMode());
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PACKET_CLICK;
    }

    @Override
    public String getDisplayName() {
        if (target == null) return "Packet Click (empty)";
        String suffix = times > 1 ? " x" + times : "";
        return "Packet Click " + target.withMode(effectiveMode()).summary() + suffix;
    }

    @Override
    public String getIcon() {
        return "Pkt";
    }
}
