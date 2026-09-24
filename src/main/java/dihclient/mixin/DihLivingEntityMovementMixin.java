package dihclient.mixin;

import dihclient.modules.AirJumpModule;
import dihclient.modules.ModuleMovementUtil;
import dihclient.modules.ScaffoldModule;
import dihclient.util.DihSilentAim;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class DihLivingEntityMovementMixin {
    @Shadow
    protected boolean jumping;

    @Shadow
    private int noJumpDelay;

    @Inject(method = "aiStep", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = Opcodes.GETFIELD))
    private void dih$airJump(CallbackInfo ci) {
        if (this.jumping && this.noJumpDelay == 0 && AirJumpModule.shouldAirJump()) {
            ((LivingEntity) (Object) this).jumpFromGround();
            this.noJumpDelay = 10;
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void dih$airJumpConsume(CallbackInfo ci) {
        AirJumpModule.onJumpFromGround((LivingEntity) (Object) this);
    }

    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "NEW",
        target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dih$killAuraSilentJump(Vec3 original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Vec3 scaffold = ScaffoldModule.correctedJumpImpulse(entity, original);
        return DihSilentAim.correctedJumpImpulse(entity, scaffold);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float dih$killAuraSilentGlidePitch(float original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        float scaffold = ScaffoldModule.correctedFallFlyingPitch(entity, original);
        return DihSilentAim.correctedFallFlyingPitch(entity, scaffold);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dih$killAuraSilentGlideLook(Vec3 original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Vec3 scaffold = ScaffoldModule.correctedFallFlyingLook(entity, original);
        return DihSilentAim.correctedFallFlyingLook(entity, scaffold);
    }

    @Inject(method = "travelInFluid", at = @At("RETURN"))
    private void dih$restoreLiquidSpeed(Vec3 input, CallbackInfo ci) {
        ModuleMovementUtil.applySpeedAfterLiquidTravel((LivingEntity) (Object) this);
    }
}
