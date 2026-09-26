package dihclient.mixin;

import dihclient.util.DihKeys;
import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.DihLoadingOverlay;
import dihclient.gui.macro.editor.ActionEditorOverlay;
import dihclient.modules.DihBlinkManager;
import dihclient.modules.DihModule;
import dihclient.modules.BuiltinModules;
import dihclient.modules.PackFreecamState;
import dihclient.modules.PackHideState;
import dihclient.modules.ModuleMovementUtil;
import dihclient.modules.ModuleRenderUtil;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihInputClicker;
import dihclient.util.DihPayloadStudySession;
import dihclient.util.DihSharedState;
import dihclient.util.DihWindowBranding;
import dihclient.util.multi.MultiManager;
import dihclient.util.multi.MultiPilot;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Direction;
import net.minecraft.util.ModCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(Minecraft.class)
public class DihMinecraftClientMixin {
    @Unique
    private static final String PACKUTIL_WINDOW_TITLE = DihWindowBranding.WINDOW_TITLE;

    @Unique
    private boolean dih$escapeWasDown;
    @Unique
    private boolean dih$inventoryWasDown;

    @Unique private boolean dih$freecamPickSwapped;
    @Unique private double dih$fcX, dih$fcY, dih$fcZ, dih$fcXOld, dih$fcYOld, dih$fcZOld;
    @Unique private float dih$fcYRot, dih$fcXRot, dih$fcYRotO, dih$fcXRotO;
    @Unique private Entity dih$remoteViewPickCamera;

    @Inject(method = "pick", at = @At("HEAD"))
    private void dih$remoteViewPickHead(float partialTicks, CallbackInfo ci) {
        dih$remoteViewPickCamera = dihclient.util.DihRemoteView.beginMainPlayerPick((Minecraft) (Object) this);
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void dih$remoteViewPickReturn(float partialTicks, CallbackInfo ci) {
        Entity restore = dih$remoteViewPickCamera;
        dih$remoteViewPickCamera = null;
        dihclient.util.DihRemoteView.endMainPlayerPick((Minecraft) (Object) this, restore);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dih$disconnectMultiOnClose(CallbackInfo ci) {
        MultiManager multi = MultiManager.getIfInitialized();
        if (multi != null) multi.shutdown();
    }

    @Inject(method = "pick", at = @At("HEAD"))
    private void dih$freecamPickHead(float partialTicks, CallbackInfo ci) {
        if (!PackFreecamState.isActive() || !PackFreecamState.interactEnabled()) return;
        Entity cam = ((Minecraft) (Object) this).getCameraEntity();
        if (cam == null) return;
        double eyeOffset = cam.getEyeY() - cam.getY();
        dih$fcX = cam.getX(); dih$fcY = cam.getY(); dih$fcZ = cam.getZ();
        dih$fcXOld = cam.xOld; dih$fcYOld = cam.yOld; dih$fcZOld = cam.zOld;
        dih$fcYRot = cam.getYRot(); dih$fcXRot = cam.getXRot();
        dih$fcYRotO = cam.yRotO; dih$fcXRotO = cam.xRotO;

        double fx = PackFreecamState.getX(partialTicks);
        double fy = PackFreecamState.getY(partialTicks) - eyeOffset;
        double fz = PackFreecamState.getZ(partialTicks);
        float fyaw = PackFreecamState.getYaw(partialTicks);
        float fpitch = PackFreecamState.getPitch(partialTicks);
        cam.setPos(fx, fy, fz);
        cam.xOld = fx; cam.yOld = fy; cam.zOld = fz;
        cam.setYRot(fyaw); cam.setXRot(fpitch);
        cam.yRotO = fyaw; cam.xRotO = fpitch;
        dih$freecamPickSwapped = true;
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void dih$freecamPickReturn(float partialTicks, CallbackInfo ci) {
        if (!dih$freecamPickSwapped) return;
        dih$freecamPickSwapped = false;
        Entity cam = ((Minecraft) (Object) this).getCameraEntity();
        if (cam == null) return;
        cam.setPos(dih$fcX, dih$fcY, dih$fcZ);
        cam.xOld = dih$fcXOld; cam.yOld = dih$fcYOld; cam.zOld = dih$fcZOld;
        cam.setYRot(dih$fcYRot); cam.setXRot(dih$fcXRot);
        cam.yRotO = dih$fcYRotO; cam.xRotO = dih$fcXRotO;
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void dih$multiPovAuthoritativePick(float partialTicks, CallbackInfo ci) {
        if (!MultiPilot.isActive()) return;
        Minecraft client = (Minecraft) (Object) this;
        HitResult vanillaHit = client.hitResult;
        HitResult result = MultiPilot.authoritativePick(client, partialTicks, vanillaHit);
        client.hitResult = result;
        client.crosshairPickEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
    }

    @Redirect(
        method = "*",
        at = @At(value = "NEW", target = "net/minecraft/client/gui/screens/LoadingOverlay")
    )
    private static LoadingOverlay dih$replaceLoadingOverlay(
        Minecraft minecraft, ReloadInstance reload,
        Consumer<Optional<Throwable>> onFinish, boolean fadeIn
    ) {

        return DihLoadingOverlay.create(minecraft, reload, onFinish, fadeIn);
    }

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void dih$createCustomWindowTitle(CallbackInfoReturnable<String> cir) {

        if (PackHideState.isActive() || dihclient.util.DihLiteVariant.enabled()) return;
        cir.setReturnValue(PACKUTIL_WINDOW_TITLE);
    }

    @Inject(method = "checkModStatus", at = @At("HEAD"), cancellable = true)
    private static void dih$reportVanillaWhileHidden(CallbackInfoReturnable<ModCheck> cir) {

        if (PackHideState.isActive()) {
            cir.setReturnValue(new ModCheck(ModCheck.Confidence.PROBABLY_NOT, "Client jar signature and brand is untouched"));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dih$onTickHead(CallbackInfo ci) {
        DihWindowBranding.tick((Minecraft) (Object) this);
        DihSharedState.get().onClientTickStart();
        DihInputClicker.onClientTickStart();
        dihclient.util.DihChamsHit.tick();
        ModuleRenderUtil.flushWorldRendererRefresh();
        if (PackHideState.isHardLocked()) {

            DihInputClicker.releaseOwnedInput();
            return;
        }
        dihclient.security.DihFabricRegisterMimicry.onClientTick((Minecraft) (Object) this);
        ModuleMovementUtil.preMovementTick();

        dihclient.util.macro.MacroExecutor.drainTickAligned();
        if (dihclient.util.DihContainerHold.hasExpiryWork()) dihclient.util.DihContainerHold.tickExpiry();
        DihModule dih = DihModule.get();
        if (!PackHideState.isActive() && dih.hasCommandBinds()) dih$pollCommandBinds(dih);
    }

    @Unique
    private final java.util.Map<Integer, Boolean> dih$commandBindWasDown = new java.util.HashMap<>();

    @Unique
    private void dih$pollCommandBinds(DihModule module) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) return;

        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) return;
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.SignEditScreen) return;
        java.util.Map<Integer, String> binds = module.getCommandBinds();
        if (binds.isEmpty()) return;
        for (java.util.Map.Entry<Integer, String> entry : binds.entrySet()) {
            int key = entry.getKey();
            boolean down = DihKeys.isKeyDown(key);
            boolean wasDown = dih$commandBindWasDown.getOrDefault(key, false);
            dih$commandBindWasDown.put(key, down);
            if (down && !wasDown) {
                String cmd = entry.getValue();
                if (cmd != null && !cmd.isBlank()) {
                    // Chat-typed commands are dispatched with the prefix already stripped; a bound
                    // command may or may not include it, so tolerate both forms here.
                    String body = dihclient.commands.DihCommands.isDihCommandMessage(cmd)
                        ? dihclient.commands.DihCommands.commandBody(cmd)
                        : cmd;
                    dihclient.commands.DihCommands.dispatch(body);
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void dih$repairStrayTitleScreen(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (!(client.gui.screen() instanceof TitleScreen)) return;
        if (PackHideState.isActive()) {
            client.gui.setScreen(new dihclient.gui.screen.DihPanicTitleScreen());

        } else if (!dihclient.util.DihLiteVariant.enabled()
                && dihclient.util.DihMenuPrefs.customMainMenuEnabled()) {
            client.gui.setScreen(new dihclient.gui.screen.DihTitleScreen());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void dih$swapMenuOnPanicChange(CallbackInfo ci) {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        Minecraft client = (Minecraft) (Object) this;

        if (PackHideState.isActive() && client.gui.screen() instanceof dihclient.gui.screen.DihTitleScreen) {
            client.gui.setScreen(new dihclient.gui.screen.DihPanicTitleScreen());
        } else if (!PackHideState.isActive()
                && client.gui.screen() instanceof dihclient.gui.screen.DihPanicTitleScreen
                && dihclient.util.DihMenuPrefs.customMainMenuEnabled()) {
            client.gui.setScreen(new dihclient.gui.screen.DihTitleScreen());
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dih$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PackHideState.isHardLocked()) return;

        if (dihclient.util.DihCombatClicker.attackInFlight()) {
            if (!dihclient.util.DihCombatClicker.beginAttack()) cir.setReturnValue(false);
            return;
        }
        Minecraft client = (Minecraft) (Object) this;

        if (dihclient.util.DihRemoteView.isActive()) {
            cir.setReturnValue(false);
            return;
        }

        if (dihclient.util.multi.MultiPilot.isActive()
                && dihclient.util.multi.MultiPilot.handleStartAttack(client)) {
            cir.setReturnValue(false);
            return;
        }
        if (DihSharedState.get().hasEntityCaptureCallback()) {
            cir.setReturnValue(false);
            return;
        }
        if (ModuleRegistry.shouldCancelAttack(client.hitResult)) {
            cir.setReturnValue(false);
            return;
        }

        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            dihclient.util.DihChamsHit.onAttack(entityHit.getEntity());
        }
        if (DihSharedState.get().hasAttackCaptureCallback()) {
            DihSharedState.get().consumeAttackCaptureCallback();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void dih$pilotContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (dihclient.util.DihRemoteView.isActive()) {
            ci.cancel();
            return;
        }

        if (dihclient.modules.AutoTotemModule.operationActive()
            || dihclient.modules.AutoArmorModule.operationActive()) {
            ci.cancel();
            return;
        }
        if (!dihclient.util.multi.MultiPilot.isActive()) return;
        dihclient.util.multi.MultiPilot.handleContinueAttack((Minecraft) (Object) this, leftClick);
        ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dih$cancelUseForModules(CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;

        if (dihclient.util.DihCombatClicker.useInFlight()) {
            if (!dihclient.util.DihCombatClicker.beginUse()) ci.cancel();
            return;
        }
        Minecraft client = (Minecraft) (Object) this;

        if (dihclient.util.DihRemoteView.isActive()) {
            ci.cancel();
            return;
        }

        if (dihclient.util.DihBlockNbtCapture.handleStartUse(client)) {
            ci.cancel();
            return;
        }

        if (dihclient.util.multi.MultiPilot.isActive()
                && dihclient.util.multi.MultiPilot.handleStartUseItem(client)) {
            ci.cancel();
            return;
        }

        DihSharedState captureState = DihSharedState.get();
        if (captureState.hasEntityCaptureCallback()) {
            if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
                captureState.consumeEntityCaptureCallback(entityHit.getEntity());
            }
            ci.cancel();
            return;
        }
        if (captureState.hasBlockCaptureCallback()) {
            if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                captureState.consumeBlockCaptureCallback(blockHit.getBlockPos(), blockHit.getDirection());
            }
            ci.cancel();
            return;
        }

        if (ModuleRegistry.shouldCancelUse(client.hitResult, net.minecraft.world.InteractionHand.MAIN_HAND)) {
            ci.cancel();
            return;
        }
        if (BuiltinModules.ownsManualFastUse()) {
            if (!BuiltinModules.beginManualFastUseClick()) ci.cancel();
            return;
        }

        if (dihclient.modules.ScaffoldModule.ownsGrimUseInput()) {
            if (!dihclient.modules.ScaffoldModule.beginGrimUseInput()) ci.cancel();
            return;
        }
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isWithinEntityInteractionRange(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean dih$freecamEntityUseReach(boolean original) {
        return original || (PackFreecamState.isActive() && PackFreecamState.interactEnabled());
    }

    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/component/AttackRange;isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean dih$freecamEntityAttackReach(boolean original) {
        return original || (PackFreecamState.isActive() && PackFreecamState.interactEnabled());
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;"))
    private HitResult dih$fastExpUseMissesTargets(HitResult original) {
        if (!DihInputClicker.isFastExpUseInProgress()) return original;
        Minecraft client = (Minecraft) (Object) this;
        if (client.player == null) return original;
        return net.minecraft.world.phys.BlockHitResult.miss(
            client.player.getEyePosition(), Direction.DOWN, client.player.blockPosition());
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void dih$cancelCaptureOnEscape(CallbackInfo ci) {

        dihclient.modules.ScaffoldModule.beforeHandleKeybinds();

        dihclient.util.DihCombatClicker.beforeHandleKeybinds();
        DihInputClicker.beforeHandleKeybinds();
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) {
            dihclient.util.DihCombatClicker.afterHandleKeybinds();
            DihInputClicker.afterHandleKeybinds();
            return;
        }
        if (PackHideState.isHardLocked()) {
            dihclient.util.DihCombatClicker.afterHandleKeybinds();
            DihInputClicker.afterHandleKeybinds();
            return;
        }

        if (dihclient.util.multi.MultiPilot.isActive()) {
            dihclient.util.multi.MultiPilot.drainKeybinds(client);
        }

        boolean escapeDown = DihKeys.isKeyDown(InputConstants.KEY_ESCAPE);
        boolean justPressed = escapeDown && !dih$escapeWasDown;
        dih$escapeWasDown = escapeDown;
        boolean inventoryDown = client.options != null && client.options.keyInventory.isDown();
        boolean inventoryJustPressed = inventoryDown && !dih$inventoryWasDown;
        dih$inventoryWasDown = inventoryDown;

        if (justPressed || inventoryJustPressed) {
            if (justPressed && DihPayloadStudySession.finishFromEscape()) {
                dihclient.util.DihCombatClicker.afterHandleKeybinds();
            DihInputClicker.afterHandleKeybinds();
                ci.cancel();
                return;
            }

            if (DihSharedState.get().consumeCaptureCancelCallback()) {
                dihclient.util.DihCombatClicker.afterHandleKeybinds();
            DihInputClicker.afterHandleKeybinds();
                ci.cancel();
                return;
            }

            ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                dihclient.util.DihCombatClicker.afterHandleKeybinds();
            DihInputClicker.afterHandleKeybinds();
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void dih$releaseQueuedClicks(CallbackInfo ci) {
        dihclient.util.DihCombatClicker.afterHandleKeybinds();
        DihInputClicker.afterHandleKeybinds();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void dih$cancelLostFocusPause(boolean suppressPauseMenuIfWeReallyArePausing, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        Minecraft client = (Minecraft) (Object) this;

        if (DihPayloadStudySession.finishFromEscape()) {
            ci.cancel();
            return;
        }

        if (DihSharedState.get().consumeCaptureCancelCallback()) {
            ci.cancel();
            return;
        }
        ActionEditorOverlay actionEditor = ActionEditorOverlay.getSharedOverlayIfExists();
        if (actionEditor != null && actionEditor.hasActiveCaptureSession()) {
            if (actionEditor.cancelCaptureIfActive()) {
                ci.cancel();
                return;
            }

            ci.cancel();
            return;
        }

        if (client.getWindow() != null && !client.getWindow().isFocused()) {
            DihModule module = DihModule.get();
            if (module != null && module.isActive() && module.isNoPauseOnLostFocus()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
    private void dih$applySpeedTimer(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {

        if (PackHideState.isHardLocked()) return;
        if (((Minecraft) (Object) this).player == null) return;
        float multiplier = ModuleMovementUtil.speedTimerMultiplier();
        if (multiplier != 1.0f) cir.setReturnValue(cir.getReturnValue() / multiplier);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.BEFORE))
    private void dih$onPacketProcessFrame(CallbackInfo ci) {
        ModuleRegistry.onPacketProcessFrame();
        DihBlinkManager.onPacketProcessFrame();
    }
}
