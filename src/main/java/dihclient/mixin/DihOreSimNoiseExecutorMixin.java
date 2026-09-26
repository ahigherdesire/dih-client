package dihclient.mixin;

import dihclient.util.worldgen.mc26_2.DihOreSimGenerationScope;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class DihOreSimNoiseExecutorMixin {
    // OreSim terrain regeneration is 26.2-only for now; the mixin stays empty on 26.3.
    //? if <26.3 {
    @Unique
    private static final Executor DIH$DIRECT_EXECUTOR = Runnable::run;

    @ModifyArg(
        method = {
            "createBiomes(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
            "fillFromNoise(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
        },
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        ),
        index = 1,
        require = 2,
        allow = 2
    )
    private Executor dih$selectNoiseExecutor(Executor vanillaExecutor) {
        if (DihOreSimGenerationScope.isActive()) return DIH$DIRECT_EXECUTOR;
        return vanillaExecutor;
    }
    //?}
}
