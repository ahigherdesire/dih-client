package dihclient.util;

//? if >=26.1 {
import dihclient.mixin.DihRenderPipelineInvoker;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class DihChamsPipelines {
    private static RenderPipeline visible;
    private static RenderPipeline occluded;
    private static boolean tried;

    private DihChamsPipelines() {
    }

    public static RenderPipeline visible() {
        ensureBuilt();
        return visible != null ? visible : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    public static RenderPipeline occluded() {
        ensureBuilt();
        return occluded != null ? occluded : RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    private static void ensureBuilt() {
        if (tried) return;
        tried = true;
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT_CULL;
        try {
            DepthStencilState baseDepth = base.getDepthStencilState();
            CompareOp visibleOp = baseDepth == null ? CompareOp.GREATER_THAN_OR_EQUAL : baseDepth.depthTest();
            CompareOp occludedOp = invertDepth(visibleOp);
            visible = clone(base, "chams_visible", new DepthStencilState(visibleOp, true));
            occluded = clone(base, "chams_occluded", new DepthStencilState(occludedOp, false));
        } catch (Throwable t) {

            visible = base;
            occluded = base;
        }
    }

    /** 26.3 compiles shaders to SPIR-V and needs the located-varyings variant. */
    private static final Identifier CHAMS_FRAGMENT = Identifier.fromNamespaceAndPath("dihclient",
        //? if >=26.3 {
        /*"core/chams_263");
        *///?} else {
        "core/chams");
        //?}

    private static RenderPipeline clone(RenderPipeline base, String name, DepthStencilState depth) {
        //? if >=26.3 {
        /*java.util.Map<com.mojang.renderpearl.api.pipeline.ShaderType, Identifier> shaders = new java.util.EnumMap<>(
            com.mojang.renderpearl.api.pipeline.ShaderType.class);
        shaders.putAll(base.getShaders());
        shaders.put(com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT, CHAMS_FRAGMENT);
        return DihRenderPipelineInvoker.dih$create(
            Identifier.fromNamespaceAndPath("dihclient", "pipeline/" + name),
            shaders,
            base.getShaderDefines(),
            base.getBindGroupLayouts(),
            base.getColorTargetStates().toArray(new com.mojang.blaze3d.pipeline.ColorTargetState[0]),
            depth,
            base.getPolygonMode(),
            true,
            base.getVertexFormatBindings().toArray(new com.mojang.blaze3d.vertex.VertexFormat[0]),
            base.getPrimitiveTopology(),
            ((DihRenderPipelineInvoker) base).dih$pushConstantSize(),
            base.getSortKey()
        );
        *///?} else if >=26.2 {
        return DihRenderPipelineInvoker.dih$create(
            Identifier.fromNamespaceAndPath("dihclient", "pipeline/" + name),
            base.getVertexShader(),
            CHAMS_FRAGMENT,
            base.getShaderDefines(),
            base.getBindGroupLayouts(),
            base.getColorTargetStates(),
            depth,
            base.getPolygonMode(),
            true,
            base.getVertexFormatBindings(),
            base.getPrimitiveTopology(),
            base.getSortKey()
        );
        //?} else {
        /*return DihRenderPipelineInvoker.dih$create(
            Identifier.fromNamespaceAndPath("dihclient", "pipeline/" + name),
            base.getVertexShader(),
            CHAMS_FRAGMENT,
            base.getShaderDefines(),
            base.getSamplers(),
            base.getUniforms(),
            base.getColorTargetState(),
            depth,
            base.getPolygonMode(),
            true,
            base.getVertexFormat(),
            base.getVertexFormatMode(),
            base.getSortKey()
        );
        *///?}
    }

    private static CompareOp invertDepth(CompareOp op) {
        return switch (op) {
            case GREATER_THAN_OR_EQUAL -> CompareOp.LESS_THAN;
            case GREATER_THAN -> CompareOp.LESS_THAN_OR_EQUAL;
            case LESS_THAN_OR_EQUAL -> CompareOp.GREATER_THAN;
            case LESS_THAN -> CompareOp.GREATER_THAN_OR_EQUAL;
            default -> CompareOp.LESS_THAN;
        };
    }
}
//?} else {
/*import dihclient.mixin.DihRenderPipelineInvoker;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// Minecraft 1.21.11: pipelines carry a depth test function and write flags instead of a DepthStencilState, and the
// entity translucent pipeline has no culling variant (the clones below enable culling themselves).
public final class DihChamsPipelines {
    private static RenderPipeline visible;
    private static RenderPipeline occluded;
    private static boolean tried;

    private static final Identifier CHAMS_FRAGMENT = Identifier.fromNamespaceAndPath("dihclient", "core/chams");

    private DihChamsPipelines() {
    }

    public static RenderPipeline visible() {
        ensureBuilt();
        return visible != null ? visible : RenderPipelines.ENTITY_TRANSLUCENT;
    }

    public static RenderPipeline occluded() {
        ensureBuilt();
        return occluded != null ? occluded : RenderPipelines.ENTITY_TRANSLUCENT;
    }

    private static void ensureBuilt() {
        if (tried) return;
        tried = true;
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT;
        try {
            visible = clone(base, "chams_visible", DepthTestFunction.LEQUAL_DEPTH_TEST, true);
            occluded = clone(base, "chams_occluded", DepthTestFunction.GREATER_DEPTH_TEST, false);
        } catch (Throwable t) {
            visible = base;
            occluded = base;
        }
    }

    private static RenderPipeline clone(RenderPipeline base, String name, DepthTestFunction depthTest, boolean writeDepth) {
        return DihRenderPipelineInvoker.dih$create(
            Identifier.fromNamespaceAndPath("dihclient", "pipeline/" + name),
            base.getVertexShader(),
            CHAMS_FRAGMENT,
            base.getShaderDefines(),
            base.getSamplers(),
            base.getUniforms(),
            base.getBlendFunction(),
            depthTest,
            base.getPolygonMode(),
            true,
            base.isWriteColor(),
            base.isWriteAlpha(),
            writeDepth,
            base.getColorLogic(),
            base.getVertexFormat(),
            base.getVertexFormatMode(),
            base.getDepthBiasScaleFactor(),
            base.getDepthBiasConstant(),
            base.getSortKey()
        );
    }
}
*///?}
