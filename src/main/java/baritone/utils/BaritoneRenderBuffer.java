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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.DihRenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
 * client's tracers already use in-game. Labels are billboarded text, drawn like the client's
 * waypoint nameplates.
 */
public final class BaritoneRenderBuffer {

    private static final Logger LOG = LoggerFactory.getLogger("Baritone/Render");

    /** Hard per-frame caps so a runaway ESP scan can't blow the vertex buffer. */
    private static final int MAX_LINES = 60_000;
    private static final int MAX_LABELS = 256;
    private static final float LABEL_SCALE = 0.025f;

    private static final DihBufferSource.Holder BUFFERS =
        new DihBufferSource.Holder(net.minecraft.client.renderer.rendertype.RenderType.BIG_BUFFER_SIZE);

    /** View-space line: positions and normal already transformed by the caller's pose. */
    private record Line(float ax, float ay, float az, float bx, float by, float bz,
                        float nx, float ny, float nz, int argb, float width) {
    }

    private record Label(Vec3 pos, String text, int argb) {
    }

    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Label> LABELS = new ArrayList<>();
    private static boolean active;
    private static boolean warned;
    private static Vec3 camera = Vec3.ZERO;
    private static final Quaternionf ORIENTATION = new Quaternionf();
    private static final Matrix4f VIEW = new Matrix4f();

    private BaritoneRenderBuffer() {
    }

    /** Start collecting a frame. Called by the LevelRenderer mixin before Baritone's render pass. */
    public static void begin(Vec3 cameraPos, Quaternionf orientation, org.joml.Matrix4fc view) {
        LINES.clear();
        LABELS.clear();
        camera = cameraPos;
        ORIENTATION.set(orientation);
        VIEW.set(view);
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

    /** Records a billboarded text label at a world position. */
    public static void label(Vec3 worldPos, String text, int argb) {
        if (!active || LABELS.size() >= MAX_LABELS || text == null || text.isEmpty()) return;
        LABELS.add(new Label(worldPos, text, argb));
    }

    /** Draws and clears the frame. Never throws — a render bug must not take the client down. */
    public static void flush() {
        active = false;
        if (LINES.isEmpty() && LABELS.isEmpty()) return;
        try {
            DihBufferSource source = BUFFERS.get();
            if (!LINES.isEmpty()) drawLines(source);
            if (!LABELS.isEmpty()) drawLabels(source);
            source.uploadAndDraw();
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOG.warn("Baritone overlay draw failed; overlays disabled for this frame", t);
            }
        } finally {
            LINES.clear();
            LABELS.clear();
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

    private static void drawLabels(DihBufferSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Matrix4f textPose = new Matrix4f();
        Font.GlyphVisitor glyphs = new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                renderable.render(textPose, source.getBuffer(renderable.renderType(Font.DisplayMode.SEE_THROUGH)),
                    LightCoordsUtil.FULL_BRIGHT, false);
            }
        };
        for (Label label : LABELS) {
            double dx = label.pos.x - camera.x, dy = label.pos.y - camera.y, dz = label.pos.z - camera.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.0) continue;
            // Constant-ish screen size: grow with distance, like vanilla name tags seen far away.
            float scale = LABEL_SCALE * (float) Math.max(1.0, dist / 10.0);
            float width = mc.font.width(label.text);
            textPose.set(VIEW)
                .translate((float) dx, (float) dy, (float) dz)
                .rotate(ORIENTATION)
                .scale(scale, -scale, scale);
            int shadow = (label.argb & 0xFF000000) | 0x1A1A1A;
            mc.font.prepareText(label.text, -width / 2f + 1f, 1f, shadow, false, 0).visit(glyphs);
            mc.font.prepareText(label.text, -width / 2f, 0f, label.argb, false, 0).visit(glyphs);
        }
    }
}
