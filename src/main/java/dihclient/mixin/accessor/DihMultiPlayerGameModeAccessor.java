package dihclient.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface DihMultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float dih$getDestroyProgress();

    @Accessor("destroyProgress")
    void dih$setDestroyProgress(float progress);

    @Accessor("destroyDelay")
    void dih$setDestroyDelay(int delay);

    @Accessor("isDestroying")
    boolean dih$isDestroying();

    @Accessor("destroyBlockPos")
    BlockPos dih$getDestroyBlockPos();

    @Invoker("startPrediction")
    void dih$startPrediction(ClientLevel level, PredictiveAction action);

    @Invoker("ensureHasSentCarriedItem")
    void dih$ensureHasSentCarriedItem();
}
