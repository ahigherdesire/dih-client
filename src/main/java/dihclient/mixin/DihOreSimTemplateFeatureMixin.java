package dihclient.mixin;

//? if >=26.1 {
//? if >=26.2 && <26.3 {
import dihclient.util.worldgen.mc26_2.DihSyntheticFeatureBridge;
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.TemplateFeatureConfiguration;
//?}
//? if >=26.2 {
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
//?}
import org.spongepowered.asm.mixin.Mixin;

// 26.1 has no TemplateFeature: the mixin is skipped there (DihMixinPlugin) and only needs a target that exists.
//? if >=26.2 {
@Mixin(TemplateFeature.class)
//?} else {
/*@Mixin(net.minecraft.world.level.levelgen.feature.Feature.class)
*///?}
public abstract class DihOreSimTemplateFeatureMixin {
    // OreSim terrain regeneration is 26.2-only for now; the mixin stays empty on 26.3.
    //? if >=26.2 && <26.3 {
    @WrapMethod(method = "place")
    private boolean dih$placeInSyntheticWorld(FeaturePlaceContext<TemplateFeatureConfiguration> context,
                                                  Operation<Boolean> original) {
        if (context.level() instanceof DihSyntheticLevel synthetic) {
            return DihSyntheticFeatureBridge.placeTemplate(context, synthetic);
        }
        return original.call(context);
    }
    //?}
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;

// Minecraft 1.21.11 has no target for this mixin; it is skipped there (DihMixinPlugin) and kept empty.
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class DihOreSimTemplateFeatureMixin {
}
*///?}
