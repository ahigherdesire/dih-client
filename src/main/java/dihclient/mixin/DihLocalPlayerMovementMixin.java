package dihclient.mixin;

import dihclient.modules.ScaffoldModule;
import dihclient.modules.KillAuraModule;
import dihclient.modules.BuiltinModules;
import dihclient.modules.PackFreecamState;
import dihclient.modules.ModuleMovementUtil;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihSilentAim;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import dihclient.modules.EntityControlModule;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class DihLocalPlayerMovementMixin {
    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float dih$scaffoldSilentMovementYaw(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        float fastExp = BuiltinModules.outgoingFastExpMovementYaw(player, original);
        float scaffold = ScaffoldModule.outgoingMovementYaw(player, fastExp);
        return DihSilentAim.outgoingMovementYaw(player, scaffold);
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float dih$scaffoldSilentMovementPitch(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        float fastExp = BuiltinModules.outgoingFastExpMovementPitch(player, original);
        float scaffold = ScaffoldModule.outgoingMovementPitch(player, fastExp);
        return DihSilentAim.outgoingMovementPitch(player, scaffold);
    }

    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void dih$flightNoSneak(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleMovementUtil.flightNoSneak((LocalPlayer) (Object) this) || PackFreecamState.isActive()) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
    private boolean dih$sprintDecisionMovementTick(boolean original) {
        return ModuleMovementUtil.sprintDecision(original, true) && !KillAuraModule.blocksSprintForCrit()
            && !ScaffoldModule.blocksSprintWithoutForward();
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean dih$sprintDecisionInput(boolean original) {
        return ModuleMovementUtil.sprintDecision(original, false) && !KillAuraModule.blocksSprintForCrit()
            && !ScaffoldModule.blocksSprintWithoutForward();
    }

    @ModifyExpressionValue(method = "sendIsSprintingIfNeeded", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isSprinting()Z"))
    private boolean dih$sprintNetworkDecision(boolean original) {
        return ModuleMovementUtil.sprintNetworkAllowed(original);
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean dih$omniSprintForwardImpulse(boolean original) {
        if (ModuleMovementUtil.sprintIsOmnidirectional()) {
            return ((LocalPlayer) (Object) this).input.getMoveVector().length() > 1.0E-5F;
        }
        return original;
    }

    @ModifyExpressionValue(method = "shouldStopRunSprinting", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z",
        opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private boolean dih$sprintIgnoreCollision(boolean original) {
        return !ModuleMovementUtil.sprintIgnoresCollision() && original;
    }

    @ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
    private boolean dih$sprintForceStop(boolean shouldStop) {
        return shouldStop || ModuleMovementUtil.sprintShouldPrevent() || KillAuraModule.blocksSprintForCrit()
            || ScaffoldModule.blocksSprintWithoutForward();
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean dih$sprintIgnoreBlindness(boolean original) {
        return !ModuleMovementUtil.sprintIgnoresBlindness() && original;
    }

    @Inject(method = "getJumpRidingScale", at = @At("RETURN"), cancellable = true)
    private void dih$entityControlMaxJump(CallbackInfoReturnable<Float> cir) {
        if (EntityControlModule.shouldMaxJump()) cir.setReturnValue(1.0F);
    }

    @Inject(method = "jumpableVehicle", at = @At("RETURN"), cancellable = true)
    private void dih$entityControlFlightJump(CallbackInfoReturnable<PlayerRideableJumping> cir) {
        if (EntityControlModule.shouldCancelRidingJump()) cir.setReturnValue(null);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void dih$networkMovementTickPre(CallbackInfo ci) {
        if ((LocalPlayer) (Object) this != Minecraft.getInstance().player) return;
        ModuleRegistry.onNetworkMovementTickPre();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
        shift = At.Shift.BEFORE, ordinal = 0), cancellable = true)
    private void dih$playerTickCancel(CallbackInfo ci) {
        if ((LocalPlayer) (Object) this != Minecraft.getInstance().player) return;
        if (ModuleRegistry.shouldCancelPlayerTick()) ci.cancel();
    }
}
