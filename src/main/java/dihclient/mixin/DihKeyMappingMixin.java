package dihclient.mixin;

import dihclient.util.DihKeys;
import dihclient.mixin.accessor.DihKeyboardHandlerAccessor;
import dihclient.mixin.accessor.DihMouseHandlerAccessor;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihKeyMappingBridge;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class DihKeyMappingMixin implements DihKeyMappingBridge {
    @Shadow
    protected InputConstants.Key key;

    @Shadow
    public abstract void setDown(boolean down);

    @Inject(method = "releaseAll", at = @At("TAIL"))
    private static void dih$reassertModuleHeldKeys(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && ModuleRegistry.sneakHoldsShift()) {
            mc.options.keyShift.setDown(true);
        }

        dihclient.util.DihPathWalker.onExternalKeyRelease();
    }

    @Override
    @Unique
    public boolean dih$isActuallyDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        int code = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return DihKeys.isMouseDown(code);
        }
        return DihKeys.isKeyDown(code);
    }

    @Override
    @Unique
    public void dih$resetPressedState() {
        setDown(dih$isActuallyDown());
    }

    @Override
    @Unique
    public void dih$simulatePress(boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.keyboardHandler == null || mc.mouseHandler == null) return;
        Window window = mc.getWindow();
        int action = pressed ? InputConstants.PRESS : InputConstants.RELEASE;
        switch (key.getType()) {
            //? if >=26.3 {
            /*case KEYBOARD -> ((DihKeyboardHandlerAccessor) mc.keyboardHandler).dih$invokeKeyPress(
                window.handle(), action, new KeyEvent(key.getValue(), 0, 0)
            );
            *///?} else {
            case KEYSYM -> ((DihKeyboardHandlerAccessor) mc.keyboardHandler).dih$invokeKeyPress(
                window.handle(), action, new KeyEvent(key.getValue(), 0, 0)
            );
            case SCANCODE -> ((DihKeyboardHandlerAccessor) mc.keyboardHandler).dih$invokeKeyPress(
                window.handle(), action, new KeyEvent(DihKeys.UNKNOWN, key.getValue(), 0)
            );
            //?}
            case MOUSE -> ((DihMouseHandlerAccessor) mc.mouseHandler).dih$invokeOnButton(
                window.handle(), new MouseButtonInfo(key.getValue(), 0), action
            );
            default -> setDown(pressed);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void dih$consumeHotbarKeysDuringTotemOperation(CallbackInfoReturnable<Boolean> cir) {
        if (!dihclient.modules.AutoTotemModule.operationActive()
            && !dihclient.modules.AutoArmorModule.operationActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        KeyMapping self = (KeyMapping) (Object) this;
        for (KeyMapping hotbar : mc.options.keyHotbarSlots) {
            if (self == hotbar) {
                cir.setReturnValue(false);
                return;
            }
        }
        if (self == mc.options.keySwapOffhand || self == mc.options.keyDrop
            || self == mc.options.keyPickItem) {
            cir.setReturnValue(false);
        }
    }
}
