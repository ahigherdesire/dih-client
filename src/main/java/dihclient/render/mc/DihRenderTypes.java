package dihclient.render.mc;

import net.minecraft.client.renderer.rendertype.LayeringTransform;
//? if <26.3
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.function.Function;

public final class DihRenderTypes {
    /** 26.3 compiles shaders to SPIR-V, which rejects the 26.2 GLSL (#moj_import, no locations, gl_VertexID). */
    private static final Identifier FOGLESS_LINES_SHADER = Identifier.fromNamespaceAndPath("dihclient",
        //? if >=26.3 {
        /*"core/fogless_lines_263");
        *///?} else {
        "core/fogless_lines");
        //?}

    private static final RenderPipeline STORAGE_ESP_FILL_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/storage_esp_fill_see_through"))
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderPipeline STORAGE_ESP_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/storage_esp_lines_see_through"))
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderPipeline TRAJECTORY_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/trajectory_lines"))
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRAJECTORY_LINES = RenderType.create(
        "dih_trajectory_lines",
        RenderSetup.builder(TRAJECTORY_LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType trajectoryLines() {
        return TRAJECTORY_LINES;
    }

    private static final RenderPipeline TRAJECTORY_THIN_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/trajectory_thin_lines"))
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRAJECTORY_THIN_LINES = RenderType.create(
        "dih_trajectory_thin_lines",
        RenderSetup.builder(TRAJECTORY_THIN_LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType trajectoryThinLines() {
        return TRAJECTORY_THIN_LINES;
    }

    private static final RenderType STORAGE_ESP_FILL = RenderType.create(
        "dih_storage_esp_fill_see_through",
        RenderSetup.builder(STORAGE_ESP_FILL_PIPELINE).createRenderSetup()
    );

    private static final RenderType STORAGE_ESP_LINES = RenderType.create(
        "dih_storage_esp_lines_see_through",
        RenderSetup.builder(STORAGE_ESP_LINES_PIPELINE).createRenderSetup()
    );

    private static final RenderPipeline TRACER_ESP_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/tracer_esp_lines"))
        .withVertexShader(FOGLESS_LINES_SHADER)
        .withFragmentShader(FOGLESS_LINES_SHADER)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType TRACER_ESP_LINES = RenderType.create(
        "dih_tracer_esp_lines",
        RenderSetup.builder(TRACER_ESP_LINES_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            //? if <26.3 {
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            //?}
            .createRenderSetup()
    );

    private static final RenderPipeline WAYPOINT_DISC_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.GUI_TEXTURED)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/waypoint_disc_see_through"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final Function<Identifier, RenderType> WAYPOINT_DISC_SEE_THROUGH = Util.memoize(
        (Function<Identifier, RenderType>) texture -> RenderType.create(
            "dih_waypoint_disc_see_through",
            RenderSetup.builder(WAYPOINT_DISC_PIPELINE)
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .createRenderSetup()
        )
    );

    private static final RenderPipeline ORE_GHOST_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.GUI_TEXTURED)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/ore_ghost_see_through"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    private static final RenderType ORE_GHOST = RenderType.create(
        "dih_ore_ghost_see_through",
        RenderSetup.builder(ORE_GHOST_PIPELINE)

            .withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet(),
                () -> RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.NEAREST, true))
            .createRenderSetup()
    );

    /** Pulls schematic overlays just in front of the block faces they sit on, as the crumbling overlay does. */
    private static final DepthStencilState SCHEMATIC_DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 10.0F);

    private static final RenderPipeline SCHEMATIC_GHOST_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.GUI_TEXTURED)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/schematic_ghost"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(SCHEMATIC_DEPTH)
        .build();

    private static final RenderType SCHEMATIC_GHOST = RenderType.create(
        "dih_schematic_ghost",
        RenderSetup.builder(SCHEMATIC_GHOST_PIPELINE)
            .withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet(),
                () -> RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.NEAREST, true))
            .sortOnUpload()
            .createRenderSetup()
    );

    private static final RenderPipeline SCHEMATIC_FILL_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.DEBUG_FILLED_BOX)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/schematic_fill"))
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(SCHEMATIC_DEPTH)
        .build();

    private static final RenderType SCHEMATIC_FILL = RenderType.create(
        "dih_schematic_fill",
        RenderSetup.builder(SCHEMATIC_FILL_PIPELINE).sortOnUpload().createRenderSetup()
    );

    private static final RenderPipeline SCHEMATIC_LINES_PIPELINE = copyLayouts(RenderPipeline.builder(), RenderPipelines.LINES)
        .withLocation(Identifier.fromNamespaceAndPath("dihclient", "pipeline/schematic_lines"))
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(SCHEMATIC_DEPTH)
        .build();

    private static final RenderType SCHEMATIC_LINES = RenderType.create(
        "dih_schematic_lines",
        RenderSetup.builder(SCHEMATIC_LINES_PIPELINE).createRenderSetup()
    );

    private static final Function<Identifier, RenderType> FEMALE_BODY_TRANSLUCENT_CULL = Util.memoize(
        (Function<Identifier, RenderType>) texture -> RenderType.create(
            "dih_female_body_translucent_cull",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        )
    );

    private DihRenderTypes() {
    }

    public static RenderType skinPreview(Identifier texture) {
        return RenderType.create(
            "dih_skin_preview",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup()
        );
    }

    public static RenderType storageEspFillSeeThrough() {
        return STORAGE_ESP_FILL;
    }

    public static RenderType storageEspLinesSeeThrough() {
        return STORAGE_ESP_LINES;
    }

    public static RenderType tracerEspLines() {
        return TRACER_ESP_LINES;
    }

    public static RenderType waypointDiscSeeThrough(Identifier texture) {
        return WAYPOINT_DISC_SEE_THROUGH.apply(texture);
    }

    public static RenderType oreGhostSeeThrough() {
        return ORE_GHOST;
    }

    /** Schematic ghost blocks: block-atlas textured, translucent, hidden behind real blocks. */
    public static RenderType schematicGhost() {
        return SCHEMATIC_GHOST;
    }

    public static RenderType schematicFill() {
        return SCHEMATIC_FILL;
    }

    public static RenderType schematicLines() {
        return SCHEMATIC_LINES;
    }

    public static RenderType femaleBodyTranslucentCull(Identifier texture) {
        return FEMALE_BODY_TRANSLUCENT_CULL.apply(texture);
    }

    private static RenderPipeline.Builder copyLayouts(RenderPipeline.Builder builder, RenderPipeline template) {
        for (BindGroupLayout layout : template.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        return builder;
    }
}
