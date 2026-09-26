package dihclient.mixin;

//? if <26.3 {
import dihclient.util.worldgen.mc26_2.DihSyntheticLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.configurations.EndSpikeConfiguration;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EndSpikeFeature.class)
public abstract class DihOreSimEndSpikeFeatureMixin {
    // OreSim terrain regeneration is 26.2-only for now; the mixin stays empty on 26.3.
    //? if <26.3 {
    @Inject(
        method = "placeSpike",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getLevel()Lnet/minecraft/server/level/ServerLevel;"
        ),
        cancellable = true
    )
    private void dih$skipSyntheticEndCrystal(ServerLevelAccessor level, RandomSource random,
                                                 EndSpikeConfiguration config, EndSpikeFeature.EndSpike spike,
                                                 CallbackInfo ci) {
        if (level instanceof DihSyntheticLevel) ci.cancel();
    }
    //?}
}
