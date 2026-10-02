package dihclient.mixin;

import dihclient.util.SodiumTerrainPassGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * {@link DihSodiumDefaultChunkRendererMixin} for Sodium 0.9 on 26.3, whose {@code render} takes two more arguments
 * (an index buffer and the OIT stage). {@code DihMixinPlugin} applies whichever of the two matches the Sodium
 * installed: a {@code @WrapMethod} with the wrong arguments fails the moment the class loads, on joining a world.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class DihSodiumDefaultChunkRendererOitMixin {

    @WrapMethod(method = "render")
    private void dih$guardRender(@Coerce Object matrices, @Coerce Object lists, @Coerce Object pass,
                                    @Coerce Object camera, @Coerce Object fog, boolean useBlockFaceCulling,
                                    @Coerce Object renderPass, @Coerce Object sampler, @Coerce Object globalsBuffer,
                                    @Coerce Object indexBuffer, @Coerce Object oitStage, Operation<Void> original) {
        if (SodiumTerrainPassGuard.shouldSkip(lists, pass)) {
            return;
        }
        original.call(matrices, lists, pass, camera, fog, useBlockFaceCulling, renderPass, sampler, globalsBuffer,
            indexBuffer, oitStage);
    }
}
