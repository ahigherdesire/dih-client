package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

/**
 * Arrows around the crosshair pointing at nearby entities, flattened to read like a compass laid on the ground.
 * Ported from LiquidBounce's Radar (GPL-3.0, CCBlueX); the arrows are built from thin rows because the vanilla GUI
 * has no triangle, and the colour can follow distance, health or the Teams friend list.
 */
public final class RadarModule extends Module {
    /** Rows per GUI pixel when filling an arrow. */
    private static final int ROWS_PER_PIXEL = 2;

    public RadarModule() {
        super("radar", "Radar", ModuleCategory.RENDER, "Arrows around the crosshair pointing at nearby entities.");
        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living")
            .group("General").description("Which entities get an arrow."));
        add(new IntSetting("max-distance", "Max Distance", 128, 8, 512, 8).unit("blocks").group("General")
            .description("Farthest entity shown."));
        add(new IntSetting("y-range", "Height Range", 0, 0, 256, 4).unit("blocks").group("General")
            .description("Skips entities this far above or below you; 0 is no limit."));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false).group("General")
            .description("Skips invisible entities."));
        add(new BoolSetting("ignore-friends", "Ignore Friends", false).group("General")
            .description("Skips friends and teammates."));

        add(new IntSetting("radius", "Radius", 40, 10, 200, 1).group("Arrow").description("Distance of the arrows from the crosshair."));
        add(new IntSetting("width", "Width", 8, 2, 40, 1).group("Arrow").description("Arrow width in pixels."));
        add(new IntSetting("height", "Height", 10, 2, 40, 1).group("Arrow").description("Arrow length in pixels."));
        add(new IntSetting("notch", "Notch", 2, 0, 20, 1).group("Arrow").description("Depth of the cut in the arrow's tail."));
        add(new BoolSetting("distance-scale", "Distance Scale", false).group("Arrow")
            .description("Shrinks arrows for far entities, down to half size."));
        add(new ChoiceSetting("tilt", "Tilt", "By Pitch", "None", "Static", "By Pitch").group("Arrow")
            .description("Flatten the ring as if lying on the ground."));
        add(new IntSetting("tilt-angle", "Tilt Angle", 45, 5, 90, 1).unit("deg").group("Arrow")
            .description("How much the ring is flattened.").visibleWhen(() -> "Static".equals(choice("tilt"))));

        add(new ChoiceSetting("color-mode", "Color", "Distance", "Distance", "Health", "Static").group("Colors")
            .description("What the arrow colour shows."));
        add(new ColorSetting("color", "Static Color", 0xC8FFFFFF).group("Colors").description("Arrow colour.")
            .visibleWhen(() -> "Static".equals(choice("color-mode"))));
        add(new BoolSetting("fade", "Fade", true).group("Colors").description("Fade arrows for far entities."));
        add(new IntSetting("fade-strength", "Fade Strength", 70, 10, 100, 5).unit("%").group("Colors")
            .description("How faint the farthest arrows get.").visibleWhen(() -> bool("fade")));
        add(new BoolSetting("friend-color", "Friend Color", true).group("Colors")
            .description("Draw Teams friends in their own colour."));
        add(new ColorSetting("friend-color-value", "Friends", 0xFF55FF55).group("Colors").description("Colour for friends.")
            .visibleWhen(() -> bool("friend-color")));
    }

    @Override
    public void onRenderOverlay(GuiGraphicsExtractor context, float partialTick) {
        if (MC.level == null || MC.player == null || MC.gui.screen() != null) return;
        float yaw = MC.player.getViewYRot(partialTick);
        double px = Mth.lerp(partialTick, MC.player.xOld, MC.player.getX());
        double pz = Mth.lerp(partialTick, MC.player.zOld, MC.player.getZ());
        double maxDist = integer("max-distance");
        float radius = integer("radius");
        String targets = choice("targets");
        String colorMode = choice("color-mode");
        boolean fade = bool("fade");
        boolean friendColor = bool("friend-color");
        int friendArgb = ModuleRenderUtil.color(this, "friend-color-value", 0xFF55FF55);
        double fadeStrength = integer("fade-strength") / 100.0D;
        int yRange = integer("y-range");
        boolean ignoreInvisible = bool("ignore-invisible");
        boolean ignoreFriends = bool("ignore-friends");
        boolean distanceScale = bool("distance-scale");
        double py = MC.player.getY();
        int staticColor = ModuleRenderUtil.color(this, "color", 0xC8FFFFFF);
        int width = integer("width"), height = integer("height"), notch = Math.min(integer("notch"), height);

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        try {
            pose.translate(context.guiWidth() / 2.0F, context.guiHeight() / 2.0F);
            pose.scale(1.0F, tilt(partialTick));
            if (MC.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) pose.scale(-1.0F, 1.0F);
            pose.rotate((float) Math.toRadians(-yaw));
            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == MC.player || !living.isAlive()) continue;
                if (!isTarget(entity, targets) || DihAntiBot.suppress(entity)) continue;
                if (ignoreInvisible && entity.isInvisible()) continue;
                if (ignoreFriends && TeamsModule.isFriendOrTeam(entity)) continue;
                if (yRange > 0 && Math.abs(entity.getY() - py) > yRange) continue;
                double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                double dx = ex - px, dz = ez - pz;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > maxDist || distance < 0.5D) continue;
                int color = friendColor && TeamsModule.isFriendOrTeam(entity) ? friendArgb : switch (colorMode) {
                    case "Health" -> healthColor(living.getHealth() / Math.max(1.0F, living.getMaxHealth()));
                    case "Static" -> staticColor;
                    default -> ModuleRenderUtil.distanceHueColor(distance, maxDist / 2.0D);
                };
                if (fade) color = ModuleWorldHooks.withAlpha(color, 1.0D - fadeStrength * distance / maxDist);
                if (color == 0) continue;
                pose.pushMatrix();
                pose.rotate((float) (Math.atan2(dz, dx) + Math.PI / 2.0D));
                pose.translate(0.0F, radius);
                if (distanceScale) {
                    float scale = (float) (1.0D - 0.5D * distance / maxDist);
                    pose.scale(scale, scale);
                }
                arrow(context, width, height, notch, color);
                pose.popMatrix();
            }
        } finally {
            pose.popMatrix();
        }
    }

    private float tilt(float partialTick) {
        return switch (choice("tilt")) {
            case "Static" -> (float) Math.sin(Math.toRadians(integer("tilt-angle")));
            case "By Pitch" -> {
                float pitch = Math.abs(MC.player.getViewXRot(partialTick));
                yield (float) Math.sin(Math.toRadians(Mth.clamp(pitch, 45.0F, 90.0F)));
            }
            default -> 1.0F;
        };
    }

    private static boolean isTarget(Entity entity, String targets) {
        return switch (targets) {
            case "All Living" -> true;
            case "Players + Hostile" -> entity instanceof Player || entity instanceof Enemy;
            default -> entity instanceof Player;
        };
    }

    /** Red at no health through yellow to green at full. */
    static int healthColor(float fraction) {
        float t = Mth.clamp(fraction, 0.0F, 1.0F);
        return 0xFF000000 | (Mth.hsvToRgb(t / 3.0F, 1.0F, 1.0F) & 0xFFFFFF);
    }

    /**
     * An arrow with its tail on the ring and its tip pointing out (+y), drawn row by row: each row spans the arrow's
     * outer edges, minus the notch cut into the tail.
     */
    private static void arrow(GuiGraphicsExtractor context, int width, int height, int notch, int color) {
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.scale(1.0F / ROWS_PER_PIXEL, 1.0F / ROWS_PER_PIXEL);
        int rows = height * ROWS_PER_PIXEL;
        float half = width * ROWS_PER_PIXEL / 2.0F;
        for (int row = 0; row < rows; row++) {
            float[] span = rowSpan(row + 0.5F, rows, half, notch * ROWS_PER_PIXEL);
            int outer = Math.round(span[0]);
            int inner = Math.round(span[1]);
            if (outer <= 0) continue;
            if (inner <= 0) {
                context.fill(-outer, row, outer, row + 1, color);
            } else if (inner < outer) {
                context.fill(-outer, row, -inner, row + 1, color);
                context.fill(inner, row, outer, row + 1, color);
            }
        }
        pose.popMatrix();
    }

    /** Outer and notch half-widths of the arrow at height {@code y} of {@code height}. */
    static float[] rowSpan(float y, float height, float half, float notch) {
        float outer = half * (1.0F - y / height);
        float inner = notch > 0.0F && y < notch ? half * (1.0F - y / notch) : 0.0F;
        return new float[] {outer, inner};
    }
}
