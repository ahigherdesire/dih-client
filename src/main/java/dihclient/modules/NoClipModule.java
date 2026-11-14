package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.DoubleSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class NoClipModule extends Module {
    private static NoClipModule instance;

    private boolean noClipSet;
    private Entity forcedVehicle;

    private volatile boolean setbackPending;

    public NoClipModule() {
        super("no-clip", "NoClip", ModuleCategory.MOVEMENT, "Move through blocks.");
        instance = this;

        add(new DoubleSetting("speed", "Speed", 0.32, 0.1, 0.4, 0.01)
            .group("General").description("Movement speed.").build());
        add(new DoubleSetting("vertical-speed", "Vertical Speed", 0.0, 0.0, 0.4, 0.01)
            .group("General").description("Up and down speed; 0 uses Speed").build());
        add(new DoubleSetting("sprint-boost", "Sprint Boost", 1.0, 1.0, 3.0, 0.05)
            .group("General").description("Speed multiplier while you hold sprint").build());

        add(new BoolSetting("only-in-vehicle", "Only In Vehicle", false)
            .group("Conditions").description("Only while riding.").build());
        add(new BoolSetting("disable-on-setback", "Disable On Setback", true)
            .group("Conditions").description("Disable on server setback.").build());
    }

    @Override
    public void preMovementTick() {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        if (setbackPending) {
            setbackPending = false;
            disableWithToggleMessage("NoClip disabled: server set you back.");
            return;
        }

        LocalPlayer player = MC.player;
        if (player == null) return;
        if (paused()) {
            if (noClipSet) restore();
            return;
        }

        noClipSet = true;
        player.noPhysics = true;
        player.fallDistance = 0.0;
        player.setOnGround(false);

        double boost = MC.options.keySprint.isDown() ? Math.max(1.0, decimal("sprint-boost")) : 1.0;
        double speed = decimal("speed") * boost;
        double configuredVertical = decimal("vertical-speed");
        double vertical = configuredVertical > 0.0 ? configuredVertical * boost : speed;
        Entity vehicle = player.getControlledVehicle();
        releaseForcedVehicle(vehicle);
        if (vehicle != null) {
            vehicle.noPhysics = true;
            forcedVehicle = vehicle;

            if (!ModuleRegistry.isModuleEnabled("boat-fly")) applyStrafe(vehicle, player, speed, vertical);
        } else {
            applyStrafe(player, player, speed, vertical);
        }
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {

        if (packet instanceof ClientboundPlayerPositionPacket && bool("disable-on-setback") && !paused()) {
            setbackPending = true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        noClipSet = false;
        setbackPending = false;
    }

    @Override
    public void onDisable() {
        restore();
    }

    @Override
    public void onGameLeft() {
        restore();
    }

    public static boolean holdsNoPhysics() {
        NoClipModule module = instance;
        return module != null && module.isEnabled() && !PackHideState.isHardLocked() && !module.paused();
    }

    private boolean paused() {

        LocalPlayer player = MC.player;
        return bool("only-in-vehicle") && (player == null || player.getControlledVehicle() == null);
    }

    private void restore() {
        noClipSet = false;
        if (MC.player != null) {
            MC.player.noPhysics = false;
            Entity vehicle = MC.player.getControlledVehicle();
            if (vehicle != null) vehicle.noPhysics = false;
        }
        releaseForcedVehicle(null);
    }

    private void releaseForcedVehicle(Entity keep) {
        if (forcedVehicle == null || forcedVehicle == keep) return;
        forcedVehicle.noPhysics = false;
        forcedVehicle = null;
    }

    private static void applyStrafe(Entity target, LocalPlayer player, double speed, double vertical) {
        double y = MC.options.keyJump.isDown() ? vertical
            : MC.options.keyShift.isDown() ? -vertical
            : 0.0;
        target.setDeltaMovement(ModuleMovementUtil.withStrafe(player, new Vec3(0.0, y, 0.0), speed));
    }
}
