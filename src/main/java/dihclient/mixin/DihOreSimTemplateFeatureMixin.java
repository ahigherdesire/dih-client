package dihclient.mixin;

import dihclient.util.worldgen.mc26_2.DihSyntheticFeatureBridge;
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TemplateFeature.class)
public abstract class DihOreSimTemplateFeatureMixin {
    @WrapMethod(method = "place")
    private boolean dih$placeInSyntheticWorld(FeaturePlaceContext<TemplateFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof DihSyntheticLevel synthetic) {
            return DihSyntheticFeatureBridge.placeTemplate(context, synthetic);
        }
        return original.call(context);
    }
}
