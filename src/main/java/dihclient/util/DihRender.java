package dihclient.util;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionfc;
//? if >=26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Optional;
import java.util.OptionalDouble;
*///?}

/** Rendering calls that differ between Minecraft 26.2 and 26.3. */
public final class DihRender {

    private DihRender() {
    }

    /** Rotates the pose by {@code rotation} ({@code mulPose} on 26.2, {@code rotate} on 26.3). */
    public static void rotate(PoseStack pose, Quaternionfc rotation) {
        //? if >=26.3 {
        /*pose.rotate(rotation);
        *///?} else {
        pose.mulPose(rotation);
        //?}
    }

    /** Replays the first-person "item used" bob for {@code hand} (the hand renderer on 26.2, the player on 26.3). */
    public static void itemUsed(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.3 {
        /*if (mc.player != null) mc.player.firstPersonHandsAndItems().itemUsed(hand);
        *///?} else {
        mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        //?}
    }

    /**
     * Draws staged vertices with {@code type}. 26.2's {@code drawFromBuffer} opens its own render pass; 26.3 wants
     * one from the caller, so this opens it on the main render target, as 26.2 did.
     */
    public static void drawFromBuffer(RenderType type, StagedVertexBuffer.ExecuteInfo info) {
        //? if >=26.3 {
        /*RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "DIH " + type, target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            type.prepare().drawFromBuffer(info, pass);
        }
        *///?} else {
        type.prepare().drawFromBuffer(info);
        //?}
    }

    /** Draws {@code indexCount} indices from a vertex buffer DIH owns. */
    public static void drawFromBuffer(RenderType type, GpuBuffer vertices, GpuBuffer indices, IndexType indexType,
                                      int indexCount, PrimitiveTopology topology) {
        //? if >=26.3 {
        /*drawFromBuffer(type, new StagedVertexBuffer.ExecuteInfo(vertices, indices, indexType, 0, 0, indexCount, topology));
        *///?} else {
        type.prepare().drawFromBuffer(vertices, indices, indexType, 0, 0, indexCount);
        //?}
    }

    /** {@code info} with another layer and tint index; everything else (shading, emission, sprite) kept. */
    public static BakedQuad.MaterialInfo copyMaterial(BakedQuad.MaterialInfo info, ChunkSectionLayer layer, int tintIndex) {
        //? if >=26.3 {
        /*return new BakedQuad.MaterialInfo(info.sprite(), layer, info.itemRenderType(), info.itemGlintRenderType(),
            info.itemGlintSpecialRenderType(), tintIndex, info.shadeDirectionOverride(), info.lightEmission());
        *///?} else {
        return new BakedQuad.MaterialInfo(info.sprite(), layer, info.itemRenderType(), tintIndex, info.shade(),
            info.lightEmission());
        //?}
    }

    /**
     * The face {@code quad} is shaded as, or null when it isn't shaded. 26.2 has a shade flag; 26.3 an optional
     * override direction instead (an unshaded quad overrides to a full-bright face).
     */
    public static @Nullable Direction shadeFacing(BakedQuad quad) {
        //? if >=26.3 {
        /*Direction override = quad.materialInfo().shadeDirectionOverride();
        return override != null ? override : quad.direction();
        *///?} else {
        return quad.materialInfo().shade() ? quad.direction() : null;
        //?}
    }

    /** Submits a model with a tint {@code color} and outline, no sprite override or crumbling. */
    public static <S> void submitModel(OrderedSubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                                       RenderType type, int light, int overlay, int color, int outlineColor) {
        //? if >=26.3 {
        /*collector.submitModel(model, state, pose, type, light, overlay, color, null, outlineColor);
        *///?} else {
        collector.submitModel(model, state, pose, type, light, overlay, color, null, outlineColor, null);
        //?}
    }
}
