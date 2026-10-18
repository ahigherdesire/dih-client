package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.render.mc.DihRenderTypes;
import dihclient.util.DihEntities;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihRotationUtil;
import dihclient.util.DihUiScale;
import dihclient.util.DihWorldGeometry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Warns about projectiles that will hit you: each nearby projectile not fired by you is
 * simulated tick by tick (its own gravity, air drag, block collisions) and flagged when its
 * path meets your hitbox. Optionally hits incoming fireballs back the way they came.
 */
public final class DangerModule extends Module {
    private static final String[] DIRECTION_ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int MAX_HUD_LINES = 3;
    private static final int URGENT_TICKS = 10;
    private static final int DEFLECT_COOLDOWN_TICKS = 4;
    private static final int ALERT_MEMORY_TICKS = 100;
    private static final float PATH_LINE_WIDTH = 2.0f;

    private static volatile boolean renderHookInstalled;

    private volatile List<Threat> threats = List.of();
    private final Map<Integer, Integer> alertedAt = new HashMap<>();
    private int tickCounter;
    private int pendingDeflectId = -1;
    private int deflectCooldown;

    private int cachedRevision = -1;
    private boolean highlight = true;
    private int arrowColor;
    private int fireballColor;
    private int thrownColor;

    DangerModule() {
        super("danger", "Danger", ModuleCategory.COMBAT,
            "Warns about incoming projectiles and can hit fireballs back.");

        add(new IntSetting("range", "Range", 32, 4, 96, 4)
            .description("How far away to watch projectiles.").build());
        add(new IntSetting("look-ahead", "Look Ahead", 40, 5, 120, 5)
            .description("Ticks of flight to predict.").build());
        add(new DoubleSetting("margin", "Hit Margin", 0.5, 0.0, 3.0, 0.1)
            .description("Extra space around you that counts as a hit.").build());

        add(new BoolSetting("arrows", "Arrows", true)
            .group("Projectiles").description("Arrows and tridents.").build());
        add(new BoolSetting("fireballs", "Fireballs", true)
            .group("Projectiles").description("Fireballs, wither skulls and wind charges.").build());
        add(new BoolSetting("thrown", "Thrown", true)
            .group("Projectiles").description("Snowballs, eggs, potions and pearls.").build());

        add(new BoolSetting("hud", "HUD Warning", true)
            .group("Alerts").description("Show incoming projectiles under the crosshair.").build());
        add(new BoolSetting("sound", "Sound", true)
            .group("Alerts").description("Ping once when a new projectile is coming at you.").build());
        add(new BoolSetting("highlight", "Highlight", true)
            .group("Alerts").description("Draw incoming projectiles and their predicted path.").build());

        add(new BoolSetting("deflect", "Deflect Fireballs", false)
            .group("Deflect").description("Hit incoming fireballs that come within reach.").build());
        add(new DoubleSetting("deflect-range", "Deflect Range", 3.0, 1.0, 6.0, 0.1)
            .group("Deflect").visibleWhen(() -> bool("deflect"))
            .description("How close a fireball must be (never beyond your reach).").build());
        add(new BoolSetting("rotate", "Return To Sender", true)
            .group("Deflect").visibleWhen(() -> bool("deflect"))
            .description("Look at the fireball before hitting it so it flies back the way it came.").build());

        add(new ColorSetting("color-arrow", "Arrow", 0xFFFFD166)
            .group("Colors").description("Arrows and tridents.").build());
        add(new ColorSetting("color-fireball", "Fireball", 0xFFFF6B35)
            .group("Colors").description("Fireballs and other hurting projectiles.").build());
        add(new ColorSetting("color-thrown", "Thrown", 0xFF4CC9F0)
            .group("Colors").description("Thrown items.").build());
    }

    private static DangerModule instance() {
        Module module = ModuleRegistry.get("danger");
        return module instanceof DangerModule danger ? danger : null;
    }

    static boolean hasHudWork() {
        DangerModule module = instance();
        return module != null && module.isEnabled() && module.bool("hud") && !module.threats.isEmpty();
    }

    static void renderHud(GuiGraphicsExtractor context) {
        DangerModule module = instance();
        if (module == null || !module.isEnabled() || !module.bool("hud") || MC.player == null) return;
        List<Threat> current = module.threats;
        if (current.isEmpty()) return;
        module.refreshSettings();
        Font font = MC.font;
        int screenWidth = DihUiScale.getVirtualScreenWidth();
        int y = DihUiScale.getVirtualScreenHeight() / 2 + 14;
        int shown = 0;
        for (Threat threat : current) {
            if (shown++ >= MAX_HUD_LINES) break;
            String text = String.format(Locale.ROOT, "⚠ %s  %.1fs  %s",
                threat.label(), threat.ticks() / 20.0, directionArrow(MC.player, threat.entity()));
            boolean urgent = threat.ticks() <= URGENT_TICKS;
            int color = urgent
                ? ((module.tickCounter / 3) % 2 == 0 ? 0xFFFF3B3B : 0xFFFFFFFF)
                : module.kindColor(threat.kind());
            context.text(font, text, (screenWidth - font.width(text)) / 2, y, color, true);
            y += font.lineHeight + 1;
        }
    }

    @Override
    public String info() {
        return threats.isEmpty() ? "" : Integer.toString(threats.size());
    }

    @Override
    public void onEnable() {
        cachedRevision = -1;
        installRenderHook();
    }

    @Override
    public void onDisable() {
        threats = List.of();
        alertedAt.clear();
        pendingDeflectId = -1;
    }

    @Override
    public void onGameLeft() {
        onDisable();
    }

    @Override
    public void tick() {
        tickCounter++;
        installRenderHook();
        refreshSettings();
        LocalPlayer player = MC.player;
        ClientLevel level = MC.level;
        if (player == null || level == null) {
            threats = List.of();
            return;
        }
        List<Threat> found = predict(player, level);
        threats = found;
        alertNew(found);
        if (bool("deflect") && MC.gameMode != null) deflect(player, level, found);
        else pendingDeflectId = -1;
        alertedAt.values().removeIf(at -> tickCounter - at > ALERT_MEMORY_TICKS);
    }

    private void refreshSettings() {
        int revision = ModuleRegistry.revision();
        if (revision == cachedRevision) return;
        cachedRevision = revision;
        highlight = bool("highlight");
        arrowColor = ModuleRenderUtil.color(this, "color-arrow", 0xFFFFD166);
        fireballColor = ModuleRenderUtil.color(this, "color-fireball", 0xFFFF6B35);
        thrownColor = ModuleRenderUtil.color(this, "color-thrown", 0xFF4CC9F0);
    }

    private List<Threat> predict(LocalPlayer player, ClientLevel level) {
        AABB area = player.getBoundingBox().inflate(integer("range"));
        int lookAhead = integer("look-ahead");
        double margin = decimal("margin");
        List<Threat> out = new ArrayList<>();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area)) {
            if (projectile.isRemoved() || projectile.getOwner() == player) continue;
            Kind kind = kindOf(projectile);
            if (kind == null || !watching(kind)) continue;
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-4) continue;

            double gravity = projectile.getGravity();
            double drag = kind == Kind.FIREBALL ? 1.0 : 0.99;
            AABB target = player.getBoundingBox().inflate(margin + projectile.getBbWidth() * 0.5);
            Vec3 position = projectile.getBoundingBox().getCenter();
            List<Vec3> path = new ArrayList<>();
            path.add(position);
            for (int tick = 1; tick <= lookAhead; tick++) {
                Vec3 next = position.add(velocity);
                BlockHitResult wall = level.clip(new ClipContext(position, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
                boolean blocked = wall.getType() != HitResult.Type.MISS;
                Vec3 end = blocked ? wall.getLocation() : next;
                if (target.contains(position) || target.clip(position, end).isPresent()) {
                    path.add(end);
                    out.add(new Threat(projectile, kind, projectile.getType().getDescription().getString(),
                        tick, List.copyOf(path)));
                    break;
                }
                if (blocked) break;
                path.add(next);
                position = next;
                velocity = velocity.scale(drag).subtract(0.0, gravity, 0.0);
            }
        }
        out.sort(Comparator.comparingInt(Threat::ticks));
        return List.copyOf(out);
    }

    private void alertNew(List<Threat> found) {
        boolean ping = false;
        for (Threat threat : found) {
            if (alertedAt.put(threat.entity().getId(), tickCounter) == null) ping = true;
        }
        if (ping && bool("sound")) {
            MC.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6F));
        }
    }

    /**
     * Fireballs fly off along the hitter's look direction as the server knows it, so with
     * Return To Sender on we turn this tick and hit on the next one.
     */
    private void deflect(LocalPlayer player, ClientLevel level, List<Threat> found) {
        if (deflectCooldown > 0) deflectCooldown--;
        double reach = Math.min(decimal("deflect-range"), player.entityInteractionRange());
        Vec3 eye = player.getEyePosition();
        boolean rotate = bool("rotate");

        if (pendingDeflectId >= 0) {
            Entity pending = level.getEntity(pendingDeflectId);
            pendingDeflectId = -1;
            if (pending instanceof AbstractHurtingProjectile fireball && !fireball.isRemoved()
                && fireball.getOwner() != player && withinReach(eye, fireball, reach)) {
                lookAt(player, fireball);
                hit(player, fireball);
                return;
            }
        }
        if (deflectCooldown > 0) return;

        for (Threat threat : found) {
            if (threat.kind() != Kind.FIREBALL) continue;
            if (!(threat.entity() instanceof AbstractHurtingProjectile fireball) || !fireball.isPickable()) continue;
            if (!withinReach(eye, fireball, reach)) continue;
            if (rotate) {
                lookAt(player, fireball);
                pendingDeflectId = fireball.getId();
            } else {
                hit(player, fireball);
            }
            return;
        }
    }

    private void hit(LocalPlayer player, Entity target) {
        if (!DihHandArbiter.beginHandPacketGroup(id())) return;
        try {
            MC.gameMode.attack(player, target);
            DihEntities.swing(player, InteractionHand.MAIN_HAND);
            deflectCooldown = DEFLECT_COOLDOWN_TICKS;
        } finally {
            DihHandArbiter.endHandPacketGroup(id());
        }
    }

    private static void lookAt(LocalPlayer player, Entity target) {
        DihRotationUtil.Rotation look = DihRotationUtil.lookingAt(target.getBoundingBox().getCenter(), player.getEyePosition());
        player.setYRot(look.yaw());
        player.setXRot(look.pitch());
    }

    private static boolean withinReach(Vec3 eye, Entity target, double reach) {
        AABB box = target.getBoundingBox();
        double x = Mth.clamp(eye.x, box.minX, box.maxX);
        double y = Mth.clamp(eye.y, box.minY, box.maxY);
        double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceToSqr(x, y, z) <= reach * reach;
    }

    private static String directionArrow(LocalPlayer player, Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Mth.wrapDegrees(bearing - player.getYRot());
        return DIRECTION_ARROWS[Math.floorMod((int) Math.round(relative / 45.0), DIRECTION_ARROWS.length)];
    }

    private static Kind kindOf(Projectile projectile) {
        if (projectile instanceof AbstractHurtingProjectile) return Kind.FIREBALL;
        if (projectile instanceof AbstractArrow) return Kind.ARROW;
        if (projectile instanceof ThrowableProjectile) return Kind.THROWN;
        return null;
    }

    private boolean watching(Kind kind) {
        return switch (kind) {
            case ARROW -> bool("arrows");
            case FIREBALL -> bool("fireballs");
            case THROWN -> bool("thrown");
        };
    }

    private int kindColor(Kind kind) {
        return switch (kind) {
            case ARROW -> arrowColor;
            case FIREBALL -> fireballColor;
            case THROWN -> thrownColor;
        };
    }

    private static synchronized void installRenderHook() {
        if (renderHookInstalled) return;
        renderHookInstalled = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                DangerModule module = instance();
                if (module == null || !module.isEnabled()) return;
                if (PackHideState.isActive()) return;
                if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
                if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
                module.refreshSettings();
                if (!module.highlight) return;
                List<Threat> current = module.threats;
                if (current.isEmpty()) return;
                Vec3 camera = context.levelState().cameraRenderState.pos;
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    DihRenderTypes.storageEspFillSeeThrough(),
                    (pose, buffer) -> module.emitFill(pose, buffer, current, camera));
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    DihRenderTypes.storageEspLinesSeeThrough(),
                    (pose, buffer) -> module.emitLines(pose, buffer, current, camera));
            } catch (Throwable ignored) {
            }
        });
    }

    private void emitFill(PoseStack.Pose pose, VertexConsumer buffer, List<Threat> current, Vec3 camera) {
        for (Threat threat : current) {
            AABB box = threat.entity().getBoundingBox().move(-camera.x, -camera.y, -camera.z).inflate(0.05);
            EspGeometry.fillBox(pose, buffer, box, EspGeometry.withAlpha(kindColor(threat.kind()), 0x50));
        }
    }

    private void emitLines(PoseStack.Pose pose, VertexConsumer buffer, List<Threat> current, Vec3 camera) {
        for (Threat threat : current) {
            int color = EspGeometry.withAlpha(kindColor(threat.kind()), 0xE0);
            AABB box = threat.entity().getBoundingBox().move(-camera.x, -camera.y, -camera.z).inflate(0.05);
            EspGeometry.edges(pose, buffer, box, color, PATH_LINE_WIDTH);
            List<Vec3> path = threat.path();
            for (int i = 0; i + 1 < path.size(); i++) {
                DihWorldGeometry.line(pose, buffer, path.get(i).subtract(camera), path.get(i + 1).subtract(camera),
                    color, PATH_LINE_WIDTH);
            }
        }
    }

    private enum Kind {
        ARROW, FIREBALL, THROWN
    }

    private record Threat(Projectile entity, Kind kind, String label, int ticks, List<Vec3> path) {
    }
}
