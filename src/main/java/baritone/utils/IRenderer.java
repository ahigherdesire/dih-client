/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.utils.accessor.IEntityRenderManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

/**
 * Baritone's immediate-mode drawing API, on the MC 26.2 pipeline.
 *
 * <p>26.2 removed the Tesselator / {@code RenderType.draw(MeshData)} stack this API was built on.
 * The public surface is unchanged so every Baritone renderer (paths, goals, selections, ESP, click
 * GUI, elytra) works as upstream, but emitted lines are recorded into {@link BaritoneRenderBuffer}
 * and drawn once per frame through the client's proven GPU-buffer line path. {@code startLines}
 * therefore returns {@code null}; callers only pass it back to {@code emit*}/{@code endLines}.
 *
 * <p>Coordinate conventions match upstream: {@code emitLine(double...)} takes camera-relative
 * coordinates; {@code emitLine(Vec3, Vec3)} and {@code emitAABB} take world coordinates. Textured
 * beacon beams are not supported (the goal beacon is drawn by {@code DihBaritonePathRenderer}).
 */
public interface IRenderer {

    IEntityRenderManager renderManager = (IEntityRenderManager) Minecraft.getInstance().getEntityRenderDispatcher();
    Settings settings = BaritoneAPI.getSettings();

    float[] color = new float[]{1.0F, 1.0F, 1.0F, 255.0F};

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    /** Width used when a caller doesn't pass one. */
    float DEFAULT_LINE_WIDTH = 2.0F;

    static int currentArgb() {
        int a = Math.round(Math.max(0f, Math.min(1f, color[3])) * 255f);
        int r = Math.round(Math.max(0f, Math.min(1f, color[0])) * 255f);
        int g = Math.round(Math.max(0f, Math.min(1f, color[1])) * 255f);
        int b = Math.round(Math.max(0f, Math.min(1f, color[2])) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -- Draw entry points --

    static BufferBuilder startLines(Color color, float alpha) {
        glColor(color, alpha);
        return null;
    }

    static BufferBuilder startLines(Color color) {
        return startLines(color, .4f);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoredDepth) {
        // Lines are drawn together by BaritoneRenderBuffer.flush() after the render pass.
    }

    static BufferBuilder startBlockQuads() {
        return null;
    }

    static void endBuffer(BufferBuilder bufferBuilder, RenderType renderType) {
        // Textured quads (goal beacon) are not supported on 26.2.
    }

    /** Camera-relative segment. */
    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, double x1, double y1, double z1, double x2, double y2, double z2, float lineWidth) {
        BaritoneRenderBuffer.line(stack.last(), x1, y1, z1, x2, y2, z2, currentArgb(), lineWidth);
    }

    /** Camera-relative segment; the explicit normal is recomputed from the direction. */
    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double nx, double ny, double nz,
                         float lineWidth
    ) {
        BaritoneRenderBuffer.line(stack.last(), x1, y1, z1, x2, y2, z2, currentArgb(), lineWidth);
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float nx, float ny, float nz,
                         float lineWidth
    ) {
        BaritoneRenderBuffer.line(stack.last(), x1, y1, z1, x2, y2, z2, currentArgb(), lineWidth);
    }

    /** World-space box outline. */
    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, float lineWidth) {
        AABB b = aabb.move(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());
        PoseStack.Pose pose = stack.last();
        int argb = currentArgb();
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        BaritoneRenderBuffer.line(pose, x1, y1, z1, x2, y1, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y1, z1, x2, y1, z2, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y1, z2, x1, y1, z2, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x1, y1, z2, x1, y1, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x1, y2, z1, x2, y2, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y2, z1, x2, y2, z2, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y2, z2, x1, y2, z2, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x1, y2, z2, x1, y2, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x1, y1, z1, x1, y2, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y1, z1, x2, y2, z1, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x2, y1, z2, x2, y2, z2, argb, lineWidth);
        BaritoneRenderBuffer.line(pose, x1, y1, z2, x1, y2, z2, argb, lineWidth);
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, double expand, float lineWidth) {
        emitAABB(bufferBuilder, stack, aabb.inflate(expand, expand, expand), lineWidth);
    }

    /** World-space segment. */
    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, Vec3 start, Vec3 end, float lineWidth) {
        double vx = renderManager.renderPosX(), vy = renderManager.renderPosY(), vz = renderManager.renderPosZ();
        emitLine(bufferBuilder, stack, start.x - vx, start.y - vy, start.z - vz, end.x - vx, end.y - vy, end.z - vz, lineWidth);
    }

    static void emitTexturedVertex(BufferBuilder bufferBuilder, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
        // Textured quads are not supported on 26.2.
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl) {
        return null;
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl, boolean ignoreDepth) {
        return null;
    }
}
