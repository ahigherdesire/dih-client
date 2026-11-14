package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihScreenProjection;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Marks where players logged off. Ported from LiquidBounce's LogoffSpot (GPL-3.0, CCBlueX), which treats every player
 * that leaves the world as logged off and puts a copy of them into it. Here a player who vanishes counts only once
 * they also leave the tab list (anyone walking out of range stays in it), and the spot is a box and a label rather
 * than a fake entity that other modules could target.
 */
public final class LogoffSpotModule extends Module {
    /** Ticks a vanished player has to drop from the tab list; the two packets can arrive a tick or two apart. */
    static final int TAB_GRACE_TICKS = 40;
    private static final float LINE_WIDTH = 2.0F;

    record Seen(String name, AABB box, float health, boolean inTab) {
    }

    record Spot(String name, AABB box, float health, long time) {
    }

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Integer> vanished = new HashMap<>();
    private final Map<UUID, Seen> vanishedInfo = new HashMap<>();
    private final Map<UUID, Spot> spots = new LinkedHashMap<>();
    private Object trackedLevel;

    public LogoffSpotModule() {
        super("logoff-spot", "LogoffSpot", ModuleCategory.RENDER, "Marks where players logged off.");
        add(new ChoiceSetting("detection", "Detection", "Tab List", "Tab List", "Any Removal").group("General")
            .description("Tab List: only players who also left the tab list. Any Removal: every player who"
                + " disappears, as on servers without a tab list."));
        add(new BoolSetting("ignore-friends", "Ignore Friends", false).group("General")
            .description("Doesn't mark friends and teammates."));
        add(new IntSetting("expire", "Expire After", 0, 0, 120, 1).unit("min").group("General")
            .description("Removes a spot after this many minutes; 0 keeps it until they return."));
        add(new BoolSetting("chat", "Chat", true).group("Chat")
            .description("Say in chat when someone logs off or comes back."));
        add(new BoolSetting("chat-coords", "Coordinates", true).group("Chat")
            .description("Include where they logged off.").visibleWhen(() -> bool("chat")));
        add(new BoolSetting("chat-return", "Returns", true).group("Chat")
            .description("Also say when someone comes back.").visibleWhen(() -> bool("chat")));

        add(new ColorSetting("color", "Color", 0xC0FFAA00).group("Render").description("Box colour."));
        add(new BoolSetting("fill", "Fill", true).group("Render").description("Shade the box."));
        add(new IntSetting("fill-opacity", "Fill Opacity", 25, 5, 100, 5).unit("%").group("Render")
            .description("Fill strength, as a share of the colour's alpha.").visibleWhen(() -> bool("fill")));
        add(new BoolSetting("outline", "Outline", true).group("Render").description("Draw the box edges."));
        add(new DoubleSetting("line-width", "Line Width", LINE_WIDTH, 1.0D, 6.0D, 0.25D).group("Render")
            .description("Edge thickness.").visibleWhen(() -> bool("outline")));
        add(new IntSetting("max-distance", "Max Distance", 256, 16, 4096, 16).unit("blocks").group("Render")
            .description("Hides spots farther than this."));

        add(new BoolSetting("label", "Label", true).group("Label").description("Show a label above the spot."));
        add(new BoolSetting("label-health", "Health", true).group("Label")
            .description("Health when they left.").visibleWhen(() -> bool("label")));
        add(new BoolSetting("label-time", "Time", true).group("Label")
            .description("How long ago they left.").visibleWhen(() -> bool("label")));
        add(new BoolSetting("label-distance", "Distance", false).group("Label")
            .description("Your distance to the spot.").visibleWhen(() -> bool("label")));
        add(new BoolSetting("label-coords", "Coordinates", false).group("Label")
            .description("Block coordinates of the spot.").visibleWhen(() -> bool("label")));
        add(new BoolSetting("label-background", "Background", true).group("Label")
            .description("Dark box behind the text.").visibleWhen(() -> bool("label")));
        ModuleWorldHooks.install(this, this::draw);
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void onGameLeft() {
        clear();
    }

    private void clear() {
        seen.clear();
        vanished.clear();
        vanishedInfo.clear();
        spots.clear();
    }

    @Override
    public void tick() {
        if (MC.level == null || MC.player == null || MC.getConnection() == null) return;
        if (trackedLevel != MC.level) {
            clear();
            trackedLevel = MC.level;
        }
        ClientPacketListener connection = MC.getConnection();
        Map<UUID, Seen> now = new HashMap<>();
        for (Player player : MC.level.players()) {
            if (player == MC.player || DihAntiBot.suppress(player)) continue;
            if (bool("ignore-friends") && TeamsModule.isFriendOrTeam(player)) continue;
            UUID id = player.getUUID();
            now.put(id, new Seen(player.getScoreboardName(), player.getBoundingBox(), player.getHealth(),
                connection.getPlayerInfo(id) != null));
            vanished.remove(id);
            vanishedInfo.remove(id);
            Spot spot = spots.remove(id);
            if (spot != null) sayReturn(spot.name());
        }
        for (Map.Entry<UUID, Seen> entry : seen.entrySet()) {
            if (now.containsKey(entry.getKey())) continue;
            vanished.put(entry.getKey(), 0);
            vanishedInfo.put(entry.getKey(), entry.getValue());
        }
        seen.clear();
        seen.putAll(now);

        boolean anyRemoval = "Any Removal".equals(choice("detection"));
        for (Iterator<Map.Entry<UUID, Integer>> it = vanished.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID id = entry.getKey();
            Seen last = vanishedInfo.get(id);
            boolean leftTab = connection.getPlayerInfo(id) == null;
            Verdict verdict = verdict(anyRemoval, last != null && last.inTab(), leftTab, entry.getValue());
            if (verdict == Verdict.WAIT) {
                entry.setValue(entry.getValue() + 1);
                continue;
            }
            it.remove();
            vanishedInfo.remove(id);
            if (verdict == Verdict.LOGGED_OFF && last != null) {
                spots.put(id, new Spot(last.name(), last.box(), last.health(), System.currentTimeMillis()));
                Vec3 c = last.box().getBottomCenter();
                say(bool("chat-coords")
                    ? String.format(Locale.ROOT, "%s logged off at %d %d %d", last.name(),
                        (int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z))
                    : last.name() + " logged off");
            }
        }
        // Someone back in the tab list but out of sight has rejoined somewhere else.
        spots.entrySet().removeIf(entry -> !anyRemoval && connection.getPlayerInfo(entry.getKey()) != null
            && sayReturn(entry.getValue().name()));
        int expire = integer("expire");
        if (expire > 0) {
            long cutoff = System.currentTimeMillis() - expire * 60_000L;
            spots.values().removeIf(spot -> spot.time() < cutoff);
        }
    }

    enum Verdict { WAIT, LOGGED_OFF, LEFT_RANGE }

    /** What a player who vanished {@code ticks} ago did, from whether they were and still are in the tab list. */
    static Verdict verdict(boolean anyRemoval, boolean wasInTab, boolean leftTab, int ticks) {
        if (anyRemoval) return Verdict.LOGGED_OFF;
        if (!wasInTab) return Verdict.LEFT_RANGE;
        if (leftTab) return Verdict.LOGGED_OFF;
        return ticks >= TAB_GRACE_TICKS ? Verdict.LEFT_RANGE : Verdict.WAIT;
    }

    private boolean sayReturn(String name) {
        return !bool("chat-return") || say(name + " came back");
    }

    private boolean say(String message) {
        if (bool("chat")) DihClientMessaging.sendPrefixed("LogoffSpot: " + message);
        return true;
    }

    private boolean inRange(Spot spot) {
        double max = integer("max-distance");
        return MC.player.position().distanceToSqr(spot.box().getCenter()) <= max * max;
    }

    private void draw(LevelRenderContext context, Vec3 camera) {
        if (spots.isEmpty()) return;
        int color = ModuleRenderUtil.color(this, "color", 0xC0FFAA00);
        int fill = ModuleWorldHooks.withAlpha(color, integer("fill-opacity") / 100.0D);
        float width = (float) decimal("line-width");
        if (bool("fill")) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspFillSeeThrough(),
            (pose, buffer) -> {
                for (Spot spot : spots.values()) {
                    if (inRange(spot)) ModuleWorldHooks.fillBox(pose, buffer, spot.box().move(camera.scale(-1.0D)), fill);
                }
            });
        if (bool("outline")) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
            (pose, buffer) -> {
                for (Spot spot : spots.values()) {
                    if (inRange(spot)) ModuleWorldHooks.edges(pose, buffer, spot.box().move(camera.scale(-1.0D)), color, width);
                }
            });
    }

    @Override
    public void onRenderOverlay(GuiGraphicsExtractor context, float partialTick) {
        if (spots.isEmpty() || !bool("label") || ModuleRenderUtil.shouldSuppressEspForUi()) return;
        DihScreenProjection projection = DihScreenProjection.current(MC, context.guiWidth(), context.guiHeight());
        if (projection == null) return;
        long now = System.currentTimeMillis();
        for (Spot spot : spots.values()) {
            if (!inRange(spot)) continue;
            AABB box = spot.box();
            float[] at = projection.project((box.minX + box.maxX) / 2.0D, box.maxY + 0.5D, (box.minZ + box.maxZ) / 2.0D);
            if (at == null) continue;
            String label = label(spot, now);
            int width = MC.font.width(label);
            int x = Math.round(at[0]) - width / 2, y = Math.round(at[1]) - 4;
            if (bool("label-background")) context.fill(x - 2, y - 2, x + width + 2, y + 10, 0x90000000);
            context.text(MC.font, label, x, y, 0xFFFFFFFF, true);
        }
    }

    private String label(Spot spot, long now) {
        StringBuilder label = new StringBuilder(spot.name());
        if (bool("label-health")) label.append(String.format(Locale.ROOT, "  %.0f❤", spot.health()));
        if (bool("label-time")) label.append("  ").append(ago(now - spot.time()));
        Vec3 c = spot.box().getBottomCenter();
        if (bool("label-distance")) {
            label.append(String.format(Locale.ROOT, "  %.0fm", MC.player.position().distanceTo(c)));
        }
        if (bool("label-coords")) {
            label.append(String.format(Locale.ROOT, "  %d %d %d",
                (int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z)));
        }
        return label.toString();
    }

    /** "12s", "5m" or "2h" since the logoff. */
    static String ago(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        if (seconds < 60L) return seconds + "s";
        if (seconds < 3600L) return (seconds / 60L) + "m";
        return (seconds / 3600L) + "h";
    }
}
