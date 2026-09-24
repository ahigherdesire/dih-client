package dihclient.mixin;

import dihclient.util.worldgen.mc26_2.DihSyntheticFeatureBridge;
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PaleMossDecorator.class)
public abstract class DihOreSimPaleMossDecoratorMixin {
    @Shadow @Final private float leavesProbability;
    @Shadow @Final private float trunkProbability;
    @Shadow @Final private float groundProbability;

    @WrapMethod(method = "place")
    private void dih$placeInSyntheticWorld(TreeDecorator.Context context, Operation<Void> original) {
        if (context.level() instanceof DihSyntheticLevel synthetic) {
            DihSyntheticFeatureBridge.placePaleMoss(
                context, synthetic, leavesProbability, trunkProbability, groundProbability);
            return;
        }
        original.call(context);
    }
}
