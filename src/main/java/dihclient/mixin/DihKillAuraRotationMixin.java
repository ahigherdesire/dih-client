package dihclient.mixin;

import dihclient.modules.ScaffoldModule;
import dihclient.util.DihSilentAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(LocalPlayer.class)
public class DihKillAuraRotationMixin {

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float dih$killAuraSilentYaw(float original) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (dihclient.util.macro.MacroRaycastAim.isActive()) {
            return dihclient.util.macro.MacroRaycastAim.outgoingYaw(self, original);
        }
        return DihSilentAim.outgoingMovementYaw(self, original);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float dih$killAuraSilentPitch(float original) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (dihclient.util.macro.MacroRaycastAim.isActive()) {
            return dihclient.util.macro.MacroRaycastAim.outgoingPitch(self, original);
        }
        return DihSilentAim.outgoingMovementPitch(self, original);
    }

    @ModifyExpressionValue(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 dih$killAuraSilentViewVector(Vec3 original, Entity camera, double blockInteractionRange,
                                                        double entityInteractionRange, float tickDelta) {
        if (camera != Minecraft.getInstance().player) {
            return original;
        }

        if (dihclient.util.macro.MacroRaycastAim.isActive()) {
            return dihclient.util.macro.MacroRaycastAim.viewVector((LocalPlayer) camera, original);
        }
        Vec3 scaffold = ScaffoldModule.silentViewVector((LocalPlayer) camera, original);
        return DihSilentAim.silentViewVector((LocalPlayer) camera, scaffold);
    }
}
