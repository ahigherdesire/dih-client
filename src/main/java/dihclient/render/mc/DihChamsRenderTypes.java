package dihclient.render.mc;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DihChamsRenderTypes {
    private static final Map<Identifier, RenderType> VISIBLE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> OCCLUDED = new ConcurrentHashMap<>();

    private DihChamsRenderTypes() {
    }

    public static Identifier textureOf(RenderSetup setup) {
        try {
            Object binding = ((dihclient.mixin.accessor.DihRenderSetupAccessor) (Object) setup).dih$textures().get("Sampler0");
            return binding == null ? null : ((dihclient.mixin.accessor.DihTextureBindingAccessor) binding).dih$location();
        } catch (Throwable t) {
            return null;
        }
    }

    public static RenderType visible(Identifier texture, RenderPipeline pipeline) {
        return VISIBLE.computeIfAbsent(texture, tex -> RenderType.create(
            "dih_chams_visible",
            RenderSetup.builder(pipeline)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        ));
    }

    public static RenderType occluded(Identifier texture, RenderPipeline pipeline) {
        return OCCLUDED.computeIfAbsent(texture, tex -> RenderType.create(
            "dih_chams_occluded",
            RenderSetup.builder(pipeline)
                .withTexture("Sampler0", tex)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        ));
    }
}
