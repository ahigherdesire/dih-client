package dihclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
//? if neoforge {
/*import dihclient.platform.DihPlatform;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * NeoForge: starts DIH's client init right after the Minecraft constructor sets the game directory (where Fabric
 * Loader runs client entrypoints). Empty on Fabric.
 */
@Mixin(Minecraft.class)
public abstract class DihPlatformStartMixin {
    //? if neoforge {
    /*@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameDirectory:Ljava/io/File;",
        opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void dih$clientStart(CallbackInfo ci) {
        DihPlatform.fireClientStart();
    }
    *///?}
}
