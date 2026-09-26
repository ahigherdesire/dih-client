package dihclient.mixin;

import dihclient.modules.FreeLookModule;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihCpsTracker;
import dihclient.util.DihMouseInputSimulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class DihMouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique private float dih$turnStartYaw;
    @Unique private float dih$turnStartPitch;
    @Unique private boolean dih$turnHadPlayer;

    @Unique private double dih$simulatedDX;
    @Unique private double dih$simulatedDY;

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void dih$freeLookTurn(LocalPlayer player, double x, double y, Operation<Void> original) {
        double assistX = dih$simulatedDX;
        double assistY = dih$simulatedDY;
        if (assistX != 0.0D || assistY != 0.0D) {
            dih$simulatedDX = 0.0D;
            dih$simulatedDY = 0.0D;
            if (FreeLookModule.consumeMouseTurn(x, y)) {

                original.call(player, assistX, assistY);
                return;
            }

            original.call(player, x + assistX, y + assistY);
            return;
        }
        if (FreeLookModule.consumeMouseTurn(x, y)) return;
        original.call(player, x, y);
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void dih$clearQueuedMouseInputWhenUnavailable(CallbackInfo ci) {
        DihMouseInputSimulator.clearIfUnavailable();
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void dih$trackCps(long window, net.minecraft.client.input.MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action != com.mojang.blaze3d.platform.InputConstants.PRESS || button == null) return;
        if (minecraft == null || minecraft.gui.screen() != null) return;
        int b = button.button();
        if (b == 0) DihCpsTracker.recordLeft();
        else if (b == 1) DihCpsTracker.recordRight();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void dih$freecamScrollSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {

        if (dihclient.modules.AutoTotemModule.operationActive()
            || dihclient.modules.AutoArmorModule.operationActive()) {
            ci.cancel();
            return;
        }

        if (dihclient.modules.PackFreecamState.onMouseScroll(yOffset)) {
            ci.cancel();
            return;
        }

        if (minecraft != null && minecraft.gui.screen() == null
            && dihclient.util.multi.MultiPilot.handleHotbarScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleAccumulatedMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V")
    )
    private void dih$applyQueuedRawMouseInput(CallbackInfo ci) {
        DihMouseInputSimulator.Delta delta = DihMouseInputSimulator.consume();
        if (DihMouseInputSimulator.hasExclusiveInput()) {

            accumulatedDX = delta.x();
            accumulatedDY = delta.y();
            return;
        }
        if (delta.isZero()) return;
        if (minecraft.player != null && FreeLookModule.lookingInstance() != null) {

            dih$simulatedDX += delta.x();
            dih$simulatedDY += delta.y();
            return;
        }
        accumulatedDX += dih$additiveAssist(accumulatedDX, delta.x());
        accumulatedDY += dih$additiveAssist(accumulatedDY, delta.y());
    }

    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean dih$avoidDoubleSmoothingTelly(boolean original) {
        return original && !DihMouseInputSimulator.hasExclusiveInput(
            DihMouseInputSimulator.Source.SCAFFOLD_TELLY);
    }

    @Unique
    private static double dih$additiveAssist(double realInput, double assistInput) {
        if (Math.abs(assistInput) < 1.0E-7) return 0.0;
        if (Math.abs(realInput) < 1.0E-4) return assistInput;
        return Math.signum(realInput) == Math.signum(assistInput) ? assistInput : 0.0;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void dih$swallowTurnDuringTotemOperation(double deltaTime, CallbackInfo ci) {
        if (!dihclient.modules.AutoTotemModule.operationActive()
            && !dihclient.modules.AutoArmorModule.movementInputPaused()) return;
        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
        dih$simulatedDX = 0.0D;
        dih$simulatedDY = 0.0D;
        DihMouseInputSimulator.clear();
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void dih$beforeTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!dihclient.util.DihRuntimeActivity.has(dihclient.util.DihRuntimeActivity.MOUSE_ROTATION)) {
            dih$turnHadPlayer = false;
            return;
        }
        dih$turnHadPlayer = minecraft != null && minecraft.player != null;
        if (dih$turnHadPlayer) {
            dih$turnStartYaw = minecraft.player.getYRot();
            dih$turnStartPitch = minecraft.player.getXRot();
        }
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void dih$afterTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!dih$turnHadPlayer || minecraft == null || minecraft.player == null) return;
        double deltaYaw = minecraft.player.getYRot() - dih$turnStartYaw;
        double deltaPitch = minecraft.player.getXRot() - dih$turnStartPitch;
        if (Math.abs(deltaYaw) > 1.0E-6 || Math.abs(deltaPitch) > 1.0E-6) {
            ModuleRegistry.onMouseRotation(deltaYaw, deltaPitch);
        }
    }
}
