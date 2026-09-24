package dihclient.mixin;

import dihclient.util.worldgen.mc26_2.DihSyntheticFeatureBridge;
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FossilFeature.class)
public abstract class DihOreSimFossilFeatureMixin {
    @WrapMethod(method = "place")
    private boolean dih$placeInSyntheticWorld(FeaturePlaceContext<FossilFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof DihSyntheticLevel synthetic) {
            return DihSyntheticFeatureBridge.placeFossil(context, synthetic);
        }
        return original.call(context);
    }
}
