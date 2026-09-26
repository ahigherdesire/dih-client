package dihclient.mixin;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.ShaderType;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import java.util.Map;
*///?} else {
import java.util.List;
//?}

@Mixin(RenderPipeline.class)
public interface DihRenderPipelineInvoker {
    //? if >=26.3 {
    /*@Invoker("<init>")
    static RenderPipeline dih$create(Identifier location, Map<ShaderType, Identifier> shaders,
                                        ShaderDefines shaderDefines, Collection<BindGroupLayout> bindGroupLayouts,
                                        ColorTargetState[] colorTargetStates, DepthStencilState depthStencilState,
                                        PolygonMode polygonMode, boolean cull, VertexFormat[] vertexFormatPerBuffer,
                                        PrimitiveTopology primitiveTopology, int pushConstantSize, int sortKey) {
        throw new AssertionError();
    }

    @Accessor("pushConstantSize")
    int dih$pushConstantSize();
    *///?} else {
    @Invoker("<init>")
    static RenderPipeline dih$create(Identifier location, Identifier vertexShader, Identifier fragmentShader,
                                        ShaderDefines shaderDefines, List<BindGroupLayout> bindGroupLayouts,
                                        ColorTargetState[] colorTargetStates, DepthStencilState depthStencilState,
                                        PolygonMode polygonMode, boolean cull, VertexFormat[] vertexFormatPerBuffer,
                                        PrimitiveTopology primitiveTopology, int sortKey) {
        throw new AssertionError();
    }
    //?}
}
