package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AirJumpModule extends Module {

    private int airJumpsLeft = 1;

    public AirJumpModule() {
        super("air-jump", "AirJump", ModuleCategory.MOVEMENT, "Jump in mid air.");
        add(new ChoiceSetting("mode", "Mode", "JumpFreely", "JumpFreely", "DoubleJump", "GhostBlock")
            .group("General")
            .description("JumpFreely: jump any time. DoubleJump: a set number per fall. GhostBlock: stand on air while holding jump")
            .build());
        add(new IntSetting("air-jumps", "Air Jumps", 1, 1, 10, 1)
            .group("General")
            .description("Jumps you get in the air before you have to land again")
            .visibleWhen(() -> "DoubleJump".equals(choice("mode")))
            .build());

        add(new BoolSetting("only-falling", "Only Falling", false)
            .group("Conditions")
            .description("Only jumps once you are on the way down")
            .visibleWhen(() -> !"GhostBlock".equals(choice("mode")))
            .build());
        add(new BoolSetting("pause-sneaking", "Pause Sneaking", false)
            .group("Conditions")
            .description("No air jumps while you sneak")
            .visibleWhen(() -> !"GhostBlock".equals(choice("mode")))
            .build());
    }

    @Override
    public void onEnable() {
        airJumpsLeft = Math.max(1, integer("air-jumps"));
    }

    @Override
    public void preMovementTick() {

        if (MC.player != null && MC.player.onGround()) airJumpsLeft = Math.max(1, integer("air-jumps"));
    }

    private boolean allowJump() {
        if (MC.player != null) {
            if (bool("only-falling") && MC.player.getDeltaMovement().y >= 0.0D) return false;
            if (bool("pause-sneaking") && MC.player.isShiftKeyDown()) return false;
        }
        return "JumpFreely".equals(choice("mode"))
            || ("DoubleJump".equals(choice("mode")) && airJumpsLeft > 0);
    }

    public static boolean shouldAirJump() {
        Module module = ModuleRegistry.get("air-jump");
        return module instanceof AirJumpModule airJump && airJump.isEnabled() && airJump.allowJump();
    }

    public static void onJumpFromGround(LivingEntity entity) {
        if (MC == null || MC.player == null || entity != MC.player) return;
        Module module = ModuleRegistry.get("air-jump");
        if (module instanceof AirJumpModule airJump && airJump.isEnabled()
            && airJump.airJumpsLeft > 0 && !MC.player.onGround()) {
            airJump.airJumpsLeft--;
        }
    }

    public static VoxelShape ghostBlockShape(VoxelShape original, BlockPos pos) {
        if (MC == null || MC.player == null || MC.options == null) return original;
        Module module = ModuleRegistry.get("air-jump");
        if (module instanceof AirJumpModule airJump && airJump.isEnabled()
            && "GhostBlock".equals(airJump.choice("mode"))
            && pos.getY() < MC.player.blockPosition().getY()
            && ghostJumpHeld()) {
            return Shapes.block();
        }
        return original;
    }

    private static volatile long lastJumpDownAtMs;

    public static boolean ghostJumpHeld() {
        if (MC == null || MC.options == null) return false;
        long now = System.currentTimeMillis();
        if (MC.options.keyJump.isDown()) {
            lastJumpDownAtMs = now;
            return true;
        }
        return now - lastJumpDownAtMs <= ghostGraceMs();
    }

    private static long ghostGraceMs() {
        if (MC.player == null) return 300L;
        net.minecraft.world.phys.Vec3 v = MC.player.getDeltaMovement();
        double speed = Math.max(Math.hypot(v.x, v.z), Math.abs(v.y));
        if (speed <= 0.22) return 300L;
        if (speed >= 0.80) return 900L;
        return 300L + (long) ((speed - 0.22) / 0.58 * 600.0);
    }
}
