package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Leaves a trail behind you, or behind every player, as a line or a ribbon. Ported from LiquidBounce's Breadcrumbs
 * (GPL-3.0, CCBlueX); here a trail also keeps a point only once it has moved a little and never grows past a cap,
 * so a permanent trail cannot eat memory.
 */
public final class BreadcrumbsModule extends Module {
    /** A point is kept only this far from the previous one (squared blocks). */
    static final double MIN_SPACING_SQ = 0.05D * 0.05D;
    private static final float LINE_WIDTH = 2.0F;

    record Point(double x, double y, double z, long time) {
    }

    private final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();
    private Object trackedLevel;

    public BreadcrumbsModule() {
        super("breadcrumbs", "Breadcrumbs", ModuleCategory.RENDER, "Draws a trail behind you or other players.");
        add(new BoolSetting("only-own", "Only Own", true).description("Trail only yourself, not every player."));
        add(new ColorSetting("color", "Color", 0x784677FF));
        add(new BoolSetting("rainbow", "Rainbow", false).description("Cycle the trail through the colours."));
        add(new DoubleSetting("height", "Height", 0.0D, 0.0D, 2.0D, 0.1D)
            .description("0 draws a line; above 0 a ribbon this tall."));
        add(new BoolSetting("temporary", "Temporary", true).description("Let old points expire."));
        add(new IntSetting("alive", "Alive", 3000, 100, 60000, 100).unit("ms")
            .description("How long a point lasts.").visibleWhen(() -> bool("temporary")));
        add(new BoolSetting("fade", "Fade", true).description("Fade points out as they expire.")
            .visibleWhen(() -> bool("temporary")));
        add(new IntSetting("max-points", "Max Points", 2000, 50, 20000, 50)
            .description("Most points kept per trail; the oldest go first."));
        ModuleWorldHooks.install(this, this::draw);
    }

    @Override
    public void onDisable() {
        trails.clear();
    }

    @Override
    public void onGameLeft() {
        trails.clear();
    }

    @Override
    public void tick() {
        if (MC.level == null || MC.player == null) return;
        if (trackedLevel != MC.level) {
            trails.clear();
            trackedLevel = MC.level;
        }
        long now = System.currentTimeMillis();
        int cap = integer("max-points");
        if (bool("only-own")) {
            trails.keySet().removeIf(id -> id != MC.player.getId());
            record(MC.player, now, cap);
        } else {
            Set<Integer> present = new HashSet<>();
            for (Player player : MC.level.players()) {
                if (!player.isAlive()) continue;
                present.add(player.getId());
                record(player, now, cap);
            }
            trails.keySet().retainAll(present);
        }
        if (bool("temporary")) {
            long expiry = now - integer("alive");
            for (Iterator<ArrayDeque<Point>> it = trails.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Point> trail = it.next();
                expire(trail, expiry);
                if (trail.isEmpty()) it.remove();
            }
        }
    }

    private void record(Player player, long now, int cap) {
        ArrayDeque<Point> trail = trails.computeIfAbsent(player.getId(), ignored -> new ArrayDeque<>());
        append(trail, new Point(player.getX(), player.getY(), player.getZ(), now), cap);
    }

    /** Appends {@code point} unless it sits on the last one, then drops the oldest points past {@code cap}. */
    static void append(ArrayDeque<Point> trail, Point point, int cap) {
        Point last = trail.peekLast();
        if (last != null) {
            double dx = point.x - last.x, dy = point.y - last.y, dz = point.z - last.z;
            if (dx * dx + dy * dy + dz * dz < MIN_SPACING_SQ) return;
        }
        trail.addLast(point);
        while (trail.size() > Math.max(2, cap)) trail.removeFirst();
    }

    /** Drops points made before {@code expiry}; points are in time order. */
    static void expire(ArrayDeque<Point> trail, long expiry) {
        while (!trail.isEmpty() && trail.peekFirst().time < expiry) trail.removeFirst();
    }

    /** Alpha multiplier for a point {@code age} ms old that lives {@code alive} ms. */
    static double fade(long age, long alive) {
        if (alive <= 0) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D, 1.0D - (double) age / alive));
    }

    private void draw(LevelRenderContext context, Vec3 camera) {
        if (trails.isEmpty()) return;
        long now = System.currentTimeMillis();
        int base = bool("rainbow")
            ? (ModuleRenderUtil.color(this, "color", 0x784677FF) & 0xFF000000)
                | (Mth.hsvToRgb((now % 4000L) / 4000.0F, 0.7F, 1.0F) & 0x00FFFFFF)
            : ModuleRenderUtil.color(this, "color", 0x784677FF);
        double height = decimal("height");
        boolean fade = bool("temporary") && bool("fade");
        long alive = integer("alive");
        float partial = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (height <= 0.0D) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
                (pose, buffer) -> emit(pose, buffer, camera, now, base, 0.0D, fade, alive, partial));
        } else {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspFillSeeThrough(),
                (pose, buffer) -> emit(pose, buffer, camera, now, base, height, fade, alive, partial));
        }
    }

    private void emit(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, long now, int base,
                      double height, boolean fade, long alive, float partial) {
        for (Map.Entry<Integer, ArrayDeque<Point>> entry : trails.entrySet()) {
            ArrayDeque<Point> trail = entry.getValue();
            if (trail.size() < 2) continue;
            // The newest point follows the entity between ticks so the trail stays attached to it.
            var entity = MC.level.getEntity(entry.getKey());
            Point previous = null;
            int index = 0;
            int last = trail.size() - 1;
            for (Point point : trail) {
                double x = point.x, y = point.y, z = point.z;
                if (index == last && entity != null) {
                    x = Mth.lerp(partial, entity.xOld, entity.getX());
                    y = Mth.lerp(partial, entity.yOld, entity.getY());
                    z = Mth.lerp(partial, entity.zOld, entity.getZ());
                    point = new Point(x, y, z, point.time);
                }
                if (previous != null) segment(pose, buffer, camera, previous, point, now, base, height, fade, alive);
                previous = point;
                index++;
            }
        }
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Point a, Point b,
                                long now, int base, double height, boolean fade, long alive) {
        int colorA = fade ? ModuleWorldHooks.withAlpha(base, fade(now - a.time, alive)) : base;
        int colorB = fade ? ModuleWorldHooks.withAlpha(base, fade(now - b.time, alive)) : base;
        if (colorA == 0 && colorB == 0) return;
        double ax = a.x - camera.x, ay = a.y - camera.y, az = a.z - camera.z;
        double bx = b.x - camera.x, by = b.y - camera.y, bz = b.z - camera.z;
        if (height <= 0.0D) {
            DihWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, colorB, LINE_WIDTH);
            return;
        }
        buffer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(colorA);
        buffer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(colorB);
        buffer.addVertex(pose, (float) bx, (float) (by + height), (float) bz).setColor(colorB);
        buffer.addVertex(pose, (float) ax, (float) (ay + height), (float) az).setColor(colorA);
        buffer.addVertex(pose, (float) ax, (float) (ay + height), (float) az).setColor(colorA);
        buffer.addVertex(pose, (float) bx, (float) (by + height), (float) bz).setColor(colorB);
        buffer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(colorB);
        buffer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(colorA);
    }
}
