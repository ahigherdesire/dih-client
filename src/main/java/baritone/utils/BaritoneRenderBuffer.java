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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.render.mc.DihRenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dihclient.util.DihBufferSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs {@link IRenderer} on the MC 26.2 GPU-buffer pipeline.
 *
 * <p>Baritone's renderers are immediate-mode ({@code startLines → emit* → endLines}). The 26.2
 * port has no immediate mode, so during a render pass every emitted line is recorded here
 * — already transformed by the caller's {@link PoseStack}, so push/translate/rotate behave as in
 * upstream — and the whole frame is drawn once by {@link #flush()} straight after the pass, from
 * the same {@code LevelRenderer.render} RETURN point and with the same line render type the
 * client's tracers already use in-game.
 */
public final class BaritoneRenderBuffer {

    private static final Logger LOG = LoggerFactory.getLogger("Baritone/Render");

    /** Hard per-frame cap so a runaway overlay can't blow the vertex buffer. */
    private static final int MAX_LINES = 60_000;

    private static final DihBufferSource.Holder BUFFERS =
        new DihBufferSource.Holder(net.minecraft.client.renderer.rendertype.RenderType.BIG_BUFFER_SIZE);

    /** View-space line: positions and normal already transformed by the caller's pose. */
    private record Line(float ax, float ay, float az, float bx, float by, float bz,
                        float nx, float ny, float nz, int argb, float width) {
    }

    private static final List<Line> LINES = new ArrayList<>();
    private static boolean active;
    private static boolean warned;

    private BaritoneRenderBuffer() {
    }

    /** Start collecting a frame. Called by the LevelRenderer mixin before Baritone's render pass. */
    public static void begin() {
        LINES.clear();
        active = true;
    }

    /** Outside a frame (e.g. a stray call from a GUI) recording is a no-op, never a leak. */
    static boolean recording() {
        return active;
    }

    /**
     * Records one segment in camera-relative coordinates, transformed by {@code pose} (which, as in
     * upstream Baritone, includes the view rotation plus anything the caller pushed).
     */
    static void line(PoseStack.Pose pose, double x1, double y1, double z1, double x2, double y2, double z2,
                     int argb, float width) {
        if (!active || LINES.size() >= MAX_LINES || ((argb >>> 24) & 0xFF) == 0) return;
        Matrix4f m = pose.pose();
        Vector3f a = m.transformPosition((float) x1, (float) y1, (float) z1, new Vector3f());
        Vector3f b = m.transformPosition((float) x2, (float) y2, (float) z2, new Vector3f());
        Vector3f n = new Vector3f(b).sub(a);
        if (n.lengthSquared() <= 1.0E-10f) return;
        n.normalize();
        LINES.add(new Line(a.x, a.y, a.z, b.x, b.y, b.z, n.x, n.y, n.z, argb, Math.max(1f, width)));
    }

    /** Draws and clears the frame. Never throws — a render bug must not take the client down. */
    public static void flush() {
        active = false;
        if (LINES.isEmpty()) return;
        try {
            DihBufferSource source = BUFFERS.get();
            drawLines(source);
            source.uploadAndDraw();
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOG.warn("Baritone overlay draw failed; overlays disabled for this frame", t);
            }
        } finally {
            LINES.clear();
        }
    }

    private static void drawLines(DihBufferSource source) {
        VertexConsumer buffer = source.getBuffer(DihRenderTypes.tracerEspLines());
        PoseStack.Pose identity = new PoseStack().last();
        Vector3f normal = new Vector3f();
        for (Line l : LINES) {
            normal.set(l.nx, l.ny, l.nz);
            buffer.addVertex(identity, l.ax, l.ay, l.az).setColor(l.argb).setNormal(identity, normal).setLineWidth(l.width);
            // Split a segment that passes the camera at its closest point, like the client's tracers,
            // so the line shader doesn't smear it across the screen.
            float t = -(l.ax * l.nx + l.ay * l.ny + l.az * l.nz);
            float len = (float) Math.sqrt((l.bx - l.ax) * (l.bx - l.ax) + (l.by - l.ay) * (l.by - l.ay) + (l.bz - l.az) * (l.bz - l.az));
            if (t > 0 && t < len) {
                float cx = l.ax + l.nx * t, cy = l.ay + l.ny * t, cz = l.az + l.nz * t;
                buffer.addVertex(identity, cx, cy, cz).setColor(l.argb).setNormal(identity, normal).setLineWidth(l.width);
                buffer.addVertex(identity, cx, cy, cz).setColor(l.argb).setNormal(identity, normal).setLineWidth(l.width);
            }
            buffer.addVertex(identity, l.bx, l.by, l.bz).setColor(l.argb).setNormal(identity, normal).setLineWidth(l.width);
        }
    }
}
