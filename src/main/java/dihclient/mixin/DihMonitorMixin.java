package dihclient.mixin;

import com.mojang.blaze3d.platform.Monitor;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//?}
import org.spongepowered.asm.mixin.Mixin;

/** GLFW (26.2) only: "unknown" instead of a missing or empty monitor name. 26.3 reads monitor names through SDL. */
@Mixin(Monitor.class)
public class DihMonitorMixin {
    //? if <26.3 {
    @Redirect(
        method = "queryMonitorName",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetMonitorName(J)Ljava/lang/String;"),
        require = 0
    )
    private static String dih$safeMonitorName(long monitor) {
        try {
            Object name = GLFW.class.getMethod("glfwGetMonitorName", long.class).invoke(null, monitor);
            if (name instanceof String text && !text.isEmpty()) return text;
        } catch (Throwable ignored) {

        }
        return "unknown";
    }
    //?}
}
