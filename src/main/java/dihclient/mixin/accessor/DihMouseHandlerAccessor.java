package dihclient.mixin.accessor;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface DihMouseHandlerAccessor {
    @Invoker("onButton")
    void dih$invokeOnButton(long handle, MouseButtonInfo buttonInfo, int action);
}
