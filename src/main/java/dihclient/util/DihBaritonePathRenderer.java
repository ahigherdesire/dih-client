package dihclient.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.pathing.path.PathExecutor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dihclient.modules.PackHideState;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Baritone's path / goal overlay, rebuilt on the MC 26.2 submit pipeline.
 *
 * <p>{@code baritone.utils.IRenderer} is a no-op on this port (the immediate-mode Tesselator
 * stack it used is gone), so Baritone's own {@code PathRenderer} draws nothing. This class reads
 * the same state through the public Baritone API and draws it through
 * {@link dihclient.platform.DihPlatform#onCollectSubmits} with the see-through line/fill render types the rest of
 * the client already uses.
 *
 * <p>What it draws, honouring the usual Baritone settings ({@code renderPath}, {@code renderGoal},
 * {@code renderSelectionBoxes}, {@code fadePath}, {@code colorCurrentPath}, …):
 * <ul>
 *   <li>a guide line from the player to the next node of the current path,</li>
 *   <li>the current path (glow + bright core) and the queued next path,</li>
 *   <li>the best-path-so-far while a calculation is running,</li>
 *   <li>blocks Baritone will break / place,</li>
 *   <li>the goal: a box for block goals, a beacon column for XZ goals, recursively for composites.</li>
 * </ul>
 */
public final class DihBaritonePathRenderer {

    /** Path nodes are drawn just above the floor of the block they stand in. */
    private static final double PATH_Y = 0.12;
    /** Hard cap per path so a huge explore path can't stall the frame. */
    private static final int MAX_NODES = 4096;
    /** Composite goals (e.g. #mine targets) can hold hundreds of members. */
    private static final int MAX_GOALS = 128;
    /** Don't draw goal geometry further than this from the camera (blocks). */
    private static final double GOAL_RANGE = 512.0;
    /** Half-height of the XZ-goal beacon column around the camera. */
    private static final double BEAM_HALF_HEIGHT = 96.0;

    private static boolean installed;

    private DihBaritonePathRenderer() {
    }

    public static synchronized void initialize() {
        if (installed) return;
        installed = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                if (PackHideState.isHardLocked()) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;

                Frame frame = Frame.capture(mc);
                if (frame == null || frame.isEmpty()) return;

                Vec3 camera = context.levelState().cameraRenderState.pos;
                PoseStack poseStack = context.poseStack();
                if (!frame.fills.isEmpty()) {
                    context.submitNodeCollector().submitCustomGeometry(poseStack,
                        DihRenderTypes.storageEspFillSeeThrough(),
                        (pose, buffer) -> frame.emitFills(pose, buffer, camera));
                }
                context.submitNodeCollector().submitCustomGeometry(poseStack,
                    DihRenderTypes.storageEspLinesSeeThrough(),
                    (pose, buffer) -> frame.emitLines(pose, buffer, camera));
            } catch (Throwable ignored) {
                // Rendering must never take the client down; Baritone keeps working without it.
            }
        });
    }

    // ── Per-frame snapshot ────────────────────────────────────────────────────

    /**
     * Everything to draw this frame, captured on the render thread before the geometry callbacks
     * run so the pathing thread swapping executors mid-frame can't tear the picture.
     */
    private static final class Frame {
        final List<Seg> lines = new ArrayList<>();
        final List<Box> fills = new ArrayList<>();
        final float pathWidth;
        final float goalWidth;

        private Frame(float pathWidth, float goalWidth) {
            this.pathWidth = pathWidth;
            this.goalWidth = goalWidth;
        }

        boolean isEmpty() {
            return lines.isEmpty() && fills.isEmpty();
        }

        static Frame capture(Minecraft mc) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) return null;
            IPathingBehavior behavior = baritone.getPathingBehavior();
            if (behavior == null) return null;
            Settings s = BaritoneAPI.getSettings();

            Frame f = new Frame(clampWidth(s.pathRenderLineWidthPixels.value),
                clampWidth(s.goalRenderLineWidthPixels.value));
            Vec3 camPos = mc.player.position();

            Goal goal = behavior.getGoal();
            if (goal != null && s.renderGoal.value) {
                f.addGoal(goal, argb(s.colorGoalBox.value, 0.9f), camPos, new int[]{0});
            }

            if (!s.renderPath.value) return f;

            IPathExecutor current = behavior.getCurrent();
            IPathExecutor next = behavior.getNext();
            boolean fade = s.fadePath.value;

            if (current != null && current.getPath() != null) {
                List<BetterBlockPos> nodes = current.getPath().positions();
                int pos = Math.max(0, current.getPosition());
                int begin = Math.max(pos - 3, 0);
                int color = argb(s.colorCurrentPath.value, 1f);
                f.addPath(nodes, begin, color, fade, true);
                f.addGuide(mc, nodes, pos, color);

                if (s.renderSelectionBoxes.value && current instanceof PathExecutor exec) {
                    f.addBlocks(exec.toBreak(), argb(s.colorBlocksToBreak.value, 1f));
                    f.addBlocks(exec.toPlace(), argb(s.colorBlocksToPlace.value, 1f));
                }
            }
            if (next != null && next.getPath() != null) {
                f.addPath(next.getPath().positions(), 0, argb(s.colorNextPath.value, 1f), fade, false);
            }
            behavior.getInProgress().ifPresent(finder -> finder.bestPathSoFar().ifPresent(p ->
                f.addPath(p.positions(), 0, argb(s.colorBestPathSoFar.value, 0.6f), fade, false)));
            return f;
        }

        // ── Path ──────────────────────────────────────────────────────────────

        void addPath(List<BetterBlockPos> nodes, int start, int color, boolean fade, boolean glow) {
            if (nodes == null) return;
            int end = Math.min(nodes.size(), start + MAX_NODES);
            int fadeStart = start + 10;
            int fadeEnd = start + 20;
            for (int i = start, next; i < end - 1; i = next) {
                BetterBlockPos a = nodes.get(i);
                next = i + 1;
                BetterBlockPos b = nodes.get(next);
                int dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
                // Merge straight runs into one segment (same trick as Baritone's PathRenderer).
                while (next + 1 < end && (!fade || next + 1 < fadeStart)
                    && nodes.get(next + 1).x - b.x == dx
                    && nodes.get(next + 1).y - b.y == dy
                    && nodes.get(next + 1).z - b.z == dz) {
                    b = nodes.get(++next);
                }
                float alpha = 1f;
                if (fade && i > fadeStart) {
                    if (i > fadeEnd) break;
                    alpha = 1f - (float) (i - fadeStart) / (fadeEnd - fadeStart);
                }
                Vec3 va = nodeCenter(a), vb = nodeCenter(b);
                if (glow) lines.add(new Seg(va, vb, scaleAlpha(color, 0.28f * alpha), pathWidth * 2.6f));
                lines.add(new Seg(va, vb, scaleAlpha(color, 0.95f * alpha), pathWidth));
            }
        }

        /** The "where am I going" line: player's feet to the next node Baritone is walking to. */
        void addGuide(Minecraft mc, List<BetterBlockPos> nodes, int pos, int color) {
            if (nodes == null || nodes.isEmpty()) return;
            int target = Math.min(pos + 1, nodes.size() - 1);
            // Render-interpolated, so the line stays glued to the player at sprint/elytra speed.
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 feet = mc.player.getPosition(partialTick).add(0, PATH_Y, 0);
            Vec3 to = nodeCenter(nodes.get(target));
            lines.add(new Seg(feet, to, 0xFFFFFFFF, pathWidth * 0.8f));
            lines.add(new Seg(feet, to, scaleAlpha(color, 0.35f), pathWidth * 2.2f));
        }

        void addBlocks(Collection<BlockPos> blocks, int color) {
            int n = 0;
            for (BlockPos p : blocks) {
                if (++n > 256) break;
                AABB box = new AABB(p).inflate(0.002);
                fills.add(new Box(box, scaleAlpha(color, 0.18f)));
                addEdges(box, scaleAlpha(color, 0.9f), goalWidth);
            }
        }

        // ── Goal ──────────────────────────────────────────────────────────────

        void addGoal(Goal goal, int color, Vec3 cam, int[] count) {
            if (goal == null || count[0] >= MAX_GOALS) return;
            if (goal instanceof GoalComposite composite) {
                for (Goal g : composite.goals()) addGoal(g, color, cam, count);
                return;
            }
            count[0]++;
            if (goal instanceof IGoalRenderPos render) {
                BlockPos p = render.getGoalPos();
                if (p == null || cam.distanceToSqr(Vec3.atCenterOf(p)) > GOAL_RANGE * GOAL_RANGE) return;
                AABB box = new AABB(p);
                fills.add(new Box(box, scaleAlpha(color, 0.16f)));
                addEdges(box, color, goalWidth);
                // A short stem so the goal is findable from a distance.
                Vec3 c = Vec3.atBottomCenterOf(p);
                lines.add(new Seg(c.add(0, 1, 0), c.add(0, 6, 0), scaleAlpha(color, 0.7f), goalWidth));
            } else if (goal instanceof GoalXZ xz) {
                double x = xz.getX() + 0.5, z = xz.getZ() + 0.5;
                double y0 = cam.y - BEAM_HALF_HEIGHT, y1 = cam.y + BEAM_HALF_HEIGHT;
                lines.add(new Seg(new Vec3(x, y0, z), new Vec3(x, y1, z), scaleAlpha(color, 0.3f), goalWidth * 4f));
                lines.add(new Seg(new Vec3(x, y0, z), new Vec3(x, y1, z), color, goalWidth));
                // Ground ring at the player's height so it reads as a column, not a stray line.
                ring(x, cam.y, z, 1.5, color);
            } else if (goal instanceof GoalYLevel y) {
                double gy = y.level;
                double r = 3.0;
                double px = Math.floor(cam.x) + 0.5, pz = Math.floor(cam.z) + 0.5;
                AABB plane = new AABB(px - r, gy, pz - r, px + r, gy + 0.01, pz + r);
                fills.add(new Box(plane, scaleAlpha(color, 0.12f)));
                addEdges(plane, color, goalWidth);
            }
        }

        private void ring(double x, double y, double z, double r, int color) {
            final int steps = 24;
            Vec3 prev = null;
            for (int i = 0; i <= steps; i++) {
                double t = i * (Math.PI * 2.0 / steps);
                Vec3 v = new Vec3(x + Math.cos(t) * r, y, z + Math.sin(t) * r);
                if (prev != null) lines.add(new Seg(prev, v, color, goalWidth));
                prev = v;
            }
        }

        private void addEdges(AABB b, int color, float width) {
            double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
            seg(x1, y1, z1, x2, y1, z1, color, width);
            seg(x2, y1, z1, x2, y1, z2, color, width);
            seg(x2, y1, z2, x1, y1, z2, color, width);
            seg(x1, y1, z2, x1, y1, z1, color, width);
            seg(x1, y2, z1, x2, y2, z1, color, width);
            seg(x2, y2, z1, x2, y2, z2, color, width);
            seg(x2, y2, z2, x1, y2, z2, color, width);
            seg(x1, y2, z2, x1, y2, z1, color, width);
            seg(x1, y1, z1, x1, y2, z1, color, width);
            seg(x2, y1, z1, x2, y2, z1, color, width);
            seg(x2, y1, z2, x2, y2, z2, color, width);
            seg(x1, y1, z2, x1, y2, z2, color, width);
        }

        private void seg(double x1, double y1, double z1, double x2, double y2, double z2, int color, float width) {
            lines.add(new Seg(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), color, width));
        }

        // ── Emission (camera-relative) ────────────────────────────────────────

        void emitLines(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam) {
            for (Seg s : lines) {
                DihWorldGeometry.line(pose, buffer,
                    s.a.x - cam.x, s.a.y - cam.y, s.a.z - cam.z,
                    s.b.x - cam.x, s.b.y - cam.y, s.b.z - cam.z,
                    s.color, s.width);
            }
        }

        void emitFills(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam) {
            for (Box b : fills) {
                AABB r = b.box.move(-cam.x, -cam.y, -cam.z);
                fillBox(pose, buffer, r, b.color);
            }
        }
    }

    private record Seg(Vec3 a, Vec3 b, int color, float width) {
    }

    private record Box(AABB box, int color) {
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Vec3 nodeCenter(BetterBlockPos p) {
        return new Vec3(p.x + 0.5, p.y + PATH_Y, p.z + 0.5);
    }

    private static float clampWidth(Float w) {
        float v = w == null ? 3f : w;
        return Math.max(1f, Math.min(12f, v));
    }

    private static int argb(Color c, float alpha) {
        if (c == null) c = Color.WHITE;
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static int scaleAlpha(int argb, float mul) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(Math.max(0f, Math.min(1f, mul)) * a);
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        if (((color >>> 24) & 0xFF) <= 0) return;
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }
}
