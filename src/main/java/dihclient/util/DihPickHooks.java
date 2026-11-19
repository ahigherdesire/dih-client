package dihclient.util;

/**
 * Minecraft 1.21.11: crosshair picking runs in GameRenderer.pick rather than Minecraft.pick, but DIH's pick hooks keep
 * their state on Minecraft. DihGameRendererPickMixin calls these on Minecraft (DihMinecraftClientMixin implements them).
 */
public interface DihPickHooks {
    void dih$pickHead(float partialTicks);

    void dih$pickReturn(float partialTicks);
}
