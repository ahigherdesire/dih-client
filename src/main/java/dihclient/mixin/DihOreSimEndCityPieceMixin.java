package dihclient.mixin;

import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCityPieces.EndCityPiece.class)
public abstract class DihOreSimEndCityPieceMixin {
    @Inject(method = "handleDataMarker", at = @At("HEAD"), cancellable = true)
    private void dih$skipSyntheticEntityMarker(String marker, BlockPos position, ServerLevelAccessor level,
                                                   RandomSource random, BoundingBox chunkBox, CallbackInfo ci) {
        if (level instanceof DihSyntheticLevel
            && (marker.startsWith("Sentry") || marker.startsWith("Elytra"))) {
            ci.cancel();
        }
    }
}
