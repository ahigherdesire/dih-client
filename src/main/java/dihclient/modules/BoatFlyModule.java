package dihclient.modules;
import dihclient.api.module.*;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class BoatFlyModule extends Module {
    private static BoatFlyModule instance;

    public BoatFlyModule() {
        super("boat-fly", "BoatFly", ModuleCategory.MOVEMENT, "Lets ridden vehicles fly.");
        instance = this;

        add(new BoolSetting("lock-yaw", "Lock Yaw", false)
            .group("General")
            .description("Points the vehicle where you look").build());

        add(new BoolSetting("change-forward-speed", "Change Forward Speed", false)
            .group("Horizontal")
            .description("Use custom forward speed.").build());
        add(new DoubleSetting("forward-speed", "Forward Speed", 1.0, 0.05, 5.0, 0.05)
            .group("Horizontal")
            .visibleWhen(() -> bool("change-forward-speed"))
            .description("Sets forward speed.").build());

        add(new DoubleSetting("upward-speed", "Upward Speed", 0.3, 0.0, 5.0, 0.05)
            .group("Vertical")
            .description("Rise speed while you hold jump, in blocks per tick").build());
        add(new DoubleSetting("down-speed", "Down Speed", 0.0, 0.0, 5.0, 0.05)
            .group("Vertical")
            .description("Sink speed while you hold sprint; 0 lets the vehicle fall normally").build());
        add(new DoubleSetting("fall-speed", "Fall Speed", 0.0, 0.0, 1.0, 0.01)
            .group("Vertical")
            .description("Slow sink with no keys held; 0 hovers in place").build());
    }

    static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
        BoatFlyModule module = instance;
        if (module == null || !module.isEnabled() || PackHideState.isHardLocked()) return movement;
        if (type != MoverType.SELF || !isControlledVehicle(vehicle)) return movement;

        if (module.bool("lock-yaw")) vehicle.setYRot(MC.player.getYRot());

        double motionX = movement.x;
        double motionY = -module.decimal("fall-speed");
        double motionZ = movement.z;

        if (MC.options.keyJump.isDown()) {
            motionY = module.decimal("upward-speed");
        } else if (MC.options.keySprint.isDown()) {
            double down = module.decimal("down-speed");
            motionY = down > 0.0 ? -down : movement.y;
        }

        if (MC.options.keyUp.isDown() && module.bool("change-forward-speed")) {
            double speed = module.decimal("forward-speed");
            float yawRadians = vehicle.getYRot() * Mth.DEG_TO_RAD;
            motionX = Mth.sin(-yawRadians) * speed;
            motionZ = Mth.cos(yawRadians) * speed;
        }

        return new Vec3(motionX, motionY, motionZ);
    }

    private static boolean isControlledVehicle(Entity vehicle) {
        return MC.player != null
            && vehicle != null
            && MC.player.getVehicle() == vehicle;
    }
}
