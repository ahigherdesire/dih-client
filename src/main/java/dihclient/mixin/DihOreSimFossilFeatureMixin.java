package dihclient.mixin;

//? if <26.3 {
import dihclient.util.worldgen.mc26_2.DihSyntheticFeatureBridge;
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
//?}
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FossilFeature.class)
public abstract class DihOreSimFossilFeatureMixin {
    // OreSim terrain regeneration is 26.2-only for now; the mixin stays empty on 26.3.
    //? if <26.3 {
    @WrapMethod(method = "place")
    private boolean dih$placeInSyntheticWorld(FeaturePlaceContext<FossilFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof DihSyntheticLevel synthetic) {
            return DihSyntheticFeatureBridge.placeFossil(context, synthetic);
        }
        return original.call(context);
    }
    //?}
}
