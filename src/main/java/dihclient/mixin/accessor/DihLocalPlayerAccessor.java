package dihclient.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface DihLocalPlayerAccessor {
    @Accessor("positionReminder")
    void dih$setPositionReminder(int ticks);

    @Accessor("yRotLast")
    void dih$setYRotLast(float yaw);

    @Accessor("xRotLast")
    void dih$setXRotLast(float pitch);

    @Invoker("isSlowDueToUsingItem")
    boolean dih$isSlowDueToUsingItem();
}
