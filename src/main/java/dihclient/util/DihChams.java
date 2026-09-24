package dihclient.util;

import dihclient.mixin.DihRenderTypeStateAccessor;
import net.minecraft.client.renderer.rendertype.DihChamsRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class DihChams {

    public static final int FULLBRIGHT = 0xF000F0;

    private DihChams() {
    }

    public static RenderType chamsVisible(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : DihChamsRenderTypes.visible(texture, DihChamsPipelines.visible());
    }

    public static RenderType chamsOccluded(RenderType original) {
        Identifier texture = textureOf(original);
        return texture == null ? null : DihChamsRenderTypes.occluded(texture, DihChamsPipelines.occluded());
    }

    private static Identifier textureOf(RenderType original) {
        try {
            RenderSetup setup = ((DihRenderTypeStateAccessor) (Object) original).dih$getState();
            return setup == null ? null : DihChamsRenderTypes.textureOf(setup);
        } catch (Throwable t) {
            return null;
        }
    }
}
