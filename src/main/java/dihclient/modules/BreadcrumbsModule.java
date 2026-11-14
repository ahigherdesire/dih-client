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
    private boolean wasDead;

    public BreadcrumbsModule() {
        super("breadcrumbs", "Breadcrumbs", ModuleCategory.RENDER, "Draws a trail behind you or other players.");
        add(new BoolSetting("only-own", "Only Own", true).group("General")
            .description("Trail only yourself, not every player."));
        add(new BoolSetting("include-self", "Include Self", true).group("General")
            .description("Also trail yourself when trailing every player.")
            .visibleWhen(() -> !bool("only-own")));
        add(new BoolSetting("ignore-friends", "Ignore Friends", false).group("General")
            .description("Leaves friends and teammates untrailed.")
            .visibleWhen(() -> !bool("only-own")));
        add(new BoolSetting("ignore-bots", "Ignore Bots", false).group("General")
            .description("Skips players AntiBot flags as bots.")
            .visibleWhen(() -> !bool("only-own")));
        add(new BoolSetting("pause-in-air", "Pause In Air", false).group("General")
            .description("Adds no points while the player is off the ground."));

        add(new BoolSetting("temporary", "Temporary", true).group("Trail").description("Let old points expire."));
        add(new IntSetting("alive", "Alive", 3000, 100, 60000, 100).unit("ms").group("Trail")
            .description("How long a point lasts.").visibleWhen(() -> bool("temporary")));
        add(new BoolSetting("fade", "Fade", true).group("Trail").description("Fade points out as they expire.")
            .visibleWhen(() -> bool("temporary")));
        add(new IntSetting("max-points", "Max Points", 2000, 50, 20000, 50).group("Trail")
            .description("Most points kept per trail; the oldest go first."));
        add(new DoubleSetting("spacing", "Spacing", 0.05D, 0.05D, 2.0D, 0.05D).unit("blocks").group("Trail")
            .description("How far a player moves before a new point is added."));
        add(new BoolSetting("clear-on-death", "Clear On Death", true).group("Trail")
            .description("Drops your trail when you die and respawn."));

        add(new ColorSetting("color", "Color", 0x784677FF).group("Render").description("Trail colour."));
        add(new ColorSetting("others-color", "Others Color", 0x784677FF).group("Render")
            .description("Colour for other players' trails.")
            .visibleWhen(() -> !bool("only-own") && !bool("rainbow")));
        add(new BoolSetting("rainbow", "Rainbow", false).group("Render").description("Cycle the trail through the colours."));
        add(new DoubleSetting("rainbow-speed", "Rainbow Speed", 4.0D, 0.5D, 20.0D, 0.5D).unit("s").group("Render")
            .description("Seconds for one full colour cycle.").visibleWhen(() -> bool("rainbow")));
        add(new DoubleSetting("height", "Height", 0.0D, 0.0D, 2.0D, 0.1D).group("Render")
            .description("0 draws a line; above 0 a ribbon this tall."));
        add(new DoubleSetting("line-width", "Line Width", LINE_WIDTH, 1.0D, 6.0D, 0.25D).group("Render")
            .description("Thickness of the line trail.").visibleWhen(() -> decimal("height") <= 0.0D));
        add(new DoubleSetting("y-offset", "Y Offset", 0.0D, -1.0D, 2.0D, 0.05D).unit("blocks").group("Render")
            .description("Raises or lowers the trail from the player's feet."));
        add(new IntSetting("render-distance", "Render Distance", 0, 0, 512, 8).unit("blocks").group("Render")
            .description("Hides points farther than this from the camera; 0 is no limit."));
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
        if (!MC.player.isAlive()) {
            wasDead = true;
        } else if (wasDead) {
            wasDead = false;
            if (bool("clear-on-death")) trails.remove(MC.player.getId());
        }
        if (bool("only-own")) {
            trails.keySet().removeIf(id -> id != MC.player.getId());
            record(MC.player, now, cap);
        } else {
            Set<Integer> present = new HashSet<>();
            for (Player player : MC.level.players()) {
                if (!player.isAlive()) continue;
                if (player == MC.player ? !bool("include-self") : skipsOther(player)) continue;
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

    private boolean skipsOther(Player player) {
        return bool("ignore-friends") && TeamsModule.isFriendOrTeam(player)
            || bool("ignore-bots") && DihAntiBot.suppress(player);
    }

    private void record(Player player, long now, int cap) {
        if (bool("pause-in-air") && !player.onGround()) return;
        ArrayDeque<Point> trail = trails.computeIfAbsent(player.getId(), ignored -> new ArrayDeque<>());
        double spacing = Math.max(0.05D, decimal("spacing"));
        append(trail, new Point(player.getX(), player.getY(), player.getZ(), now), cap, spacing * spacing);
    }

    /** Appends {@code point} unless it sits on the last one, then drops the oldest points past {@code cap}. */
    static void append(ArrayDeque<Point> trail, Point point, int cap) {
        append(trail, point, cap, MIN_SPACING_SQ);
    }

    /** As {@link #append(ArrayDeque, Point, int)}, keeping a point only {@code spacingSq} (squared blocks) from the last. */
    static void append(ArrayDeque<Point> trail, Point point, int cap, double spacingSq) {
        Point last = trail.peekLast();
        if (last != null) {
            double dx = point.x - last.x, dy = point.y - last.y, dz = point.z - last.z;
            if (dx * dx + dy * dy + dz * dz < spacingSq) return;
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
        int own = ModuleRenderUtil.color(this, "color", 0x784677FF);
        int others = ModuleRenderUtil.color(this, "others-color", own);
        if (bool("rainbow")) {
            long cycle = Math.max(500L, Math.round(decimal("rainbow-speed") * 1000.0D));
            int rgb = Mth.hsvToRgb((now % cycle) / (float) cycle, 0.7F, 1.0F) & 0x00FFFFFF;
            own = (own & 0xFF000000) | rgb;
            others = own;
        }
        int selfColor = own, otherColor = others;
        double height = decimal("height");
        boolean fade = bool("temporary") && bool("fade");
        long alive = integer("alive");
        float partial = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float width = (float) Mth.clamp(decimal("line-width"), 1.0D, 6.0D);
        double yOffset = decimal("y-offset");
        int limit = integer("render-distance");
        double limitSq = limit <= 0 ? Double.MAX_VALUE : (double) limit * limit;
        Trace trace = new Trace(camera, now, height, fade, alive, partial, width, yOffset, limitSq);
        if (height <= 0.0D) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
                (pose, buffer) -> emit(pose, buffer, trace, selfColor, otherColor));
        } else {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspFillSeeThrough(),
                (pose, buffer) -> emit(pose, buffer, trace, selfColor, otherColor));
        }
    }

    /** Per-frame draw parameters. */
    private record Trace(Vec3 camera, long now, double height, boolean fade, long alive, float partial,
                         float width, double yOffset, double limitSq) {
    }

    private void emit(PoseStack.Pose pose, VertexConsumer buffer, Trace trace, int selfColor, int otherColor) {
        for (Map.Entry<Integer, ArrayDeque<Point>> entry : trails.entrySet()) {
            ArrayDeque<Point> trail = entry.getValue();
            if (trail.size() < 2) continue;
            int base = entry.getKey() == MC.player.getId() ? selfColor : otherColor;
            // The newest point follows the entity between ticks so the trail stays attached to it.
            var entity = MC.level.getEntity(entry.getKey());
            Point previous = null;
            int index = 0;
            int last = trail.size() - 1;
            for (Point point : trail) {
                double x = point.x, y = point.y + trace.yOffset(), z = point.z;
                if (index == last && entity != null) {
                    x = Mth.lerp(trace.partial(), entity.xOld, entity.getX());
                    y = Mth.lerp(trace.partial(), entity.yOld, entity.getY()) + trace.yOffset();
                    z = Mth.lerp(trace.partial(), entity.zOld, entity.getZ());
                    point = new Point(x, y, z, point.time);
                } else if (trace.yOffset() != 0.0D) {
                    point = new Point(x, y, z, point.time);
                }
                if (previous != null && (trace.camera().distanceToSqr(x, y, z) <= trace.limitSq()
                    || trace.camera().distanceToSqr(previous.x, previous.y, previous.z) <= trace.limitSq())) {
                    segment(pose, buffer, trace, previous, point, base);
                }
                previous = point;
                index++;
            }
        }
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Trace trace, Point a, Point b, int base) {
        boolean fade = trace.fade();
        long now = trace.now();
        int colorA = fade ? ModuleWorldHooks.withAlpha(base, fade(now - a.time, trace.alive())) : base;
        int colorB = fade ? ModuleWorldHooks.withAlpha(base, fade(now - b.time, trace.alive())) : base;
        if (colorA == 0 && colorB == 0) return;
        Vec3 camera = trace.camera();
        double height = trace.height();
        double ax = a.x - camera.x, ay = a.y - camera.y, az = a.z - camera.z;
        double bx = b.x - camera.x, by = b.y - camera.y, bz = b.z - camera.z;
        if (height <= 0.0D) {
            DihWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, colorB, trace.width());
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
