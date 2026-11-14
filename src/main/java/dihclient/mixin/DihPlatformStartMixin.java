package dihclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
//? if !fabric {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dihclient.platform.DihPlatform;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.nio.file.Path;
*///?}

/**
 * NeoForge and Forge: start DIH's client init from the Minecraft constructor, as early as the loader allows (Fabric
 * Loader runs client entrypoints right after the game directory is set). Empty on Fabric.
 */
@Mixin(Minecraft.class)
public abstract class DihPlatformStartMixin {
    //? if neoforge {
    /*// NeoForge constructs mods before Minecraft: start at the first File.toPath() in the constructor, right after
    // gameDirectory is set. (A wrap: not an @Inject inside a constructor, and not on the final field's write.)
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;toPath()Ljava/nio/file/Path;", ordinal = 0))
    private Path dih$clientStart(File file, Operation<Path> original) {
        DihPlatform.fireClientStart();
        return original.call(file);
    }
    *///?} elif forge {
    /*// Forge constructs mods in ClientModLoader.begin, inside the constructor: start right after it. (A wrap: Forge's
    // Mixin allows no @Inject inside a constructor.)
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/client/loading/ClientModLoader;begin(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V"))
    private void dih$clientStart(Minecraft minecraft, net.minecraft.server.packs.repository.PackRepository packs,
                                 net.minecraft.server.packs.resources.ReloadableResourceManager resources, Operation<Void> original) {
        original.call(minecraft, packs, resources);
        DihPlatform.fireClientStart();
    }
    *///?}
}
