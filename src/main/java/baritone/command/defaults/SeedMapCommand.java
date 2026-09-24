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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;
import baritone.util.JourneyMapHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

/**
 * In-client seed map: every structure from the world seed, <b>biome-validated</b>, drawn on the
 * built-in JourneyMap fullscreen map (mcseedmap.net-style, but inside the game).
 *
 * <p>Validation runs vanilla's own structure generation offline ({@link SeedStructureScanner}), so
 * a marker means the structure really generates there — not just that the grid allows it.
 *
 * <pre>
 *   #seedmap                         everything within 2000 blocks of you
 *   #seedmap 4000                    bigger radius (max 8000)
 *   #seedmap village monument        only these kinds (any part of the id matches)
 *   #seedmap 3000 at 1200 -800       centre the scan somewhere else
 *   #seedmap wp                      also drop JourneyMap waypoints on landmarks
 *   #seedmap strongholds             nearest strongholds + all 128 on the map
 *   #seedmap off                     hide (the map-icon toolbar button brings it back)
 * </pre>
 */
public class SeedMapCommand extends Command {

    private static final int DEFAULT_RADIUS = 2000;
    private static final int MAX_RADIUS = 8000;
    /** Most markers pushed to JourneyMap at once; nearest win. */
    private static final int MAX_MARKERS = 2500;
    /** Max auto-waypoints per run so {@code wp} can't flood the waypoint list. */
    private static final int MAX_WAYPOINTS = 24;

    /** Filter term that selects a whole family from the legend (e.g. every village variant). */
    private static final Map<String, String> FAMILY_FILTER = Map.ofEntries(
        Map.entry("Village", "village"),
        Map.entry("Ocean Ruin", "ocean_ruin"),
        Map.entry("Ruined Portal", "ruined_portal"),
        Map.entry("Shipwreck", "shipwreck"),
        Map.entry("Trail Ruins", "trail_ruins"),
        Map.entry("Trial Chambers", "trial_chambers"),
        Map.entry("Ancient City", "ancient_city"),
        Map.entry("Pillager Outpost", "pillager_outpost"),
        Map.entry("Buried Treasure", "buried_treasure"),
        Map.entry("Mineshaft", "mineshaft"));

    /** One scan at a time; a new request supersedes the running one. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SeedMapScan");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();
    /** Waypoints already created this session, so repeated scans don't duplicate them. */
    private static final Set<String> WAYPOINTS_MADE = new HashSet<>();

    public SeedMapCommand(IBaritone baritone) {
        super(baritone, "seedmap", "structures", "smap");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (ctx.world() == null || ctx.player() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        int radius = DEFAULT_RADIUS;
        Integer atX = null, atZ = null;
        boolean waypoints = false;
        boolean strongholds = false;
        List<String> filters = new ArrayList<>();

        while (args.hasAny()) {
            String tok = args.getString().toLowerCase(Locale.ROOT);
            switch (tok) {
                case "off", "clear", "hide" -> {
                    GENERATION.incrementAndGet(); // cancel a running scan too
                    JourneyMapHelper.clearStructures();
                    logDirect("Seed map hidden. The map-icon button on the JourneyMap fullscreen toolbar brings it back.");
                    return;
                }
                case "wp", "waypoint", "waypoints" -> waypoints = true;
                case "stronghold", "strongholds", "sh" -> strongholds = true;
                case "at" -> {
                    atX = parseInt(args.getString(), "X");
                    atZ = parseInt(args.getString(), "Z");
                }
                default -> {
                    if (tok.chars().allMatch(c -> Character.isDigit(c))) {
                        radius = parseInt(tok, "radius");
                    } else {
                        for (String part : tok.split(",")) if (!part.isBlank()) filters.add(normalizeFilter(part));
                    }
                }
            }
        }
        if (radius < 64) throw new CommandInvalidStateException("Radius must be at least 64 blocks.");
        if (radius > MAX_RADIUS) {
            logDirect("Radius clamped to " + MAX_RADIUS + " blocks.");
            radius = MAX_RADIUS;
        }

        long seed = resolveSeed();
        ResourceKey<Level> dim = ctx.world().dimension();
        BetterBlockPos feet = ctx.playerFeet();
        int ox = atX != null ? atX : feet.x;
        int oz = atZ != null ? atZ : feet.z;

        if (strongholds) {
            if (!Level.OVERWORLD.equals(dim)) {
                throw new CommandInvalidStateException("Strongholds are in the Overworld — run this there.");
            }
            startStrongholds(seed, ox, oz, waypoints);
            return;
        }

        startScan(seed, dim, ox, oz, radius, filters, waypoints);
    }

    // ── Area scan ─────────────────────────────────────────────────────────────

    private void startScan(long seed, ResourceKey<Level> dim, int ox, int oz, int radius,
                           List<String> filters, boolean waypoints) {
        final int gen = GENERATION.incrementAndGet();
        final Predicate<String> filter = filters.isEmpty() ? null
            : id -> filters.stream().anyMatch(id::contains);

        logDirect("Scanning " + (filters.isEmpty() ? "all structures" : String.join(", ", filters))
            + " within " + radius + " blocks of " + ox + ", " + oz + " (seed " + seed + ")…");
        if (!SeedStructureScanner.isWarm(seed, dim)) {
            logDirect("First scan for this seed loads vanilla worldgen (a few seconds).", ChatFormatting.GRAY);
        }

        WORKER.execute(() -> {
            if (GENERATION.get() != gen) return;
            long[] lastShown = {0L};
            SeedStructureScanner.Result result = SeedStructureScanner.scan(seed, dim, ox, oz, radius, filter,
                () -> GENERATION.get() != gen, (done, total) -> {
                    long now = System.currentTimeMillis();
                    if (total < 40 || now - lastShown[0] < 250) return;
                    lastShown[0] = now;
                    int pct = (int) (100L * done / Math.max(1, total));
                    actionBar("Seed map: checking structures… " + pct + "%  (" + done + "/" + total + ")");
                });
            actionBar(null);
            if (GENERATION.get() != gen) return; // superseded; its successor reports instead

            if (result.error != null) {
                Minecraft.getInstance().execute(() -> {
                    logDirect("Biome validation unavailable: " + result.error, ChatFormatting.RED);
                    logDirect("This dimension may use non-vanilla worldgen. Falling back is not reliable, so nothing was drawn.",
                        ChatFormatting.GRAY);
                });
                return;
            }

            List<SeedStructureScanner.Found> found = new ArrayList<>(result.found);
            found.sort(Comparator.comparingLong(f -> f.distSq(ox, oz)));
            boolean capped = found.size() > MAX_MARKERS;
            List<SeedStructureScanner.Found> drawn = capped ? found.subList(0, MAX_MARKERS) : found;

            Minecraft.getInstance().execute(() -> {
                JourneyMapHelper.showStructures(toMarkers(drawn), dim);
                report(result, found, drawn.size(), capped, ox, oz);
                noteMapAvailability();
                if (waypoints) addLandmarkWaypoints(found);
            });
        });
    }

    private void report(SeedStructureScanner.Result result, List<SeedStructureScanner.Found> found,
                        int drawn, boolean capped, int ox, int oz) {
        logDirect(Component.literal("══ Seed map: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(found.size() + " validated structure" + (found.size() == 1 ? "" : "s"))
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal("  (" + result.rejected + " grid spots failed biome/terrain checks, "
                + String.format(Locale.ROOT, "%.1fs", result.millis / 1000.0) + ")").withStyle(ChatFormatting.GRAY)));
        if (capped) logDirect("Drew the nearest " + drawn + "; narrow with a filter, e.g. #seedmap village", ChatFormatting.GRAY);
        if (result.truncated) logDirect("Scan stopped early (time budget or cancelled) — results are partial.", ChatFormatting.YELLOW);
        if (result.failures > 0) logDirect(result.failures + " candidate(s) errored and were skipped.", ChatFormatting.GRAY);
        if (found.isEmpty()) {
            logDirect("Nothing found. Try a bigger radius or a different filter.", ChatFormatting.GRAY);
            return;
        }

        // Legend: one coloured line per family, nearest instance clickable (#goto).
        Map<String, List<SeedStructureScanner.Found>> byFamily = new LinkedHashMap<>();
        for (SeedStructureScanner.Found f : found) {
            byFamily.computeIfAbsent(StructureStyle.of(f.id()).family, k -> new ArrayList<>()).add(f);
        }
        byFamily.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .forEach(e -> {
                SeedStructureScanner.Found nearest = e.getValue().get(0); // list is distance-sorted
                StructureStyle style = StructureStyle.of(nearest.id());
                int dist = (int) Math.sqrt(nearest.distSq(ox, oz));
                String only = FAMILY_FILTER.getOrDefault(e.getKey(), nearest.id());
                MutableComponent family = Component.literal(e.getValue().size() + "× " + e.getKey());
                family.setStyle(family.getStyle()
                    .withColor(ChatFormatting.WHITE)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click: show only " + e.getKey())))
                    .withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "seedmap " + only
                        + " at " + ox + " " + oz)));
                MutableComponent line = Component.literal(" ■ ").withStyle(s -> s.withColor(TextColor.fromRgb(style.rgb)))
                    .append(family)
                    .append(Component.literal("  nearest ").withStyle(ChatFormatting.GRAY))
                    .append(gotoLink(nearest, dist));
                logDirect(line);
            });
        logDirect("Open the fullscreen map to see them (toolbar map-icon button toggles the layer). "
            + "Click a coordinate to path there.", ChatFormatting.GRAY);
    }

    // ── Strongholds ───────────────────────────────────────────────────────────

    private void startStrongholds(long seed, int ox, int oz, boolean waypoints) {
        final int gen = GENERATION.incrementAndGet();
        logDirect("Locating strongholds for seed " + seed + " (first run takes a few seconds)…");
        WORKER.execute(() -> {
            List<SeedStructureScanner.Found> all;
            try {
                all = SeedStructureScanner.strongholds(seed, () -> GENERATION.get() != gen);
            } catch (Throwable t) {
                Minecraft.getInstance().execute(() ->
                    logDirect("Stronghold search failed: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : " — " + t.getMessage()), ChatFormatting.RED));
                return;
            }
            if (GENERATION.get() != gen) return;
            List<SeedStructureScanner.Found> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparingLong(f -> f.distSq(ox, oz)));
            Minecraft.getInstance().execute(() -> {
                JourneyMapHelper.showStructures(toMarkers(sorted), Level.OVERWORLD);
                logDirect(Component.literal("══ " + sorted.size() + " strongholds").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" — nearest:").withStyle(ChatFormatting.GRAY)));
                for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                    SeedStructureScanner.Found f = sorted.get(i);
                    int dist = (int) Math.sqrt(f.distSq(ox, oz));
                    logDirect(Component.literal(" " + (i + 1) + ". ").withStyle(ChatFormatting.GRAY).append(gotoLink(f, dist)));
                }
                logDirect("Positions are the stronghold's start room area; the portal room is nearby underground.",
                    ChatFormatting.GRAY);
                noteMapAvailability();
                if (waypoints) addLandmarkWaypoints(sorted.subList(0, Math.min(5, sorted.size())));
            });
        });
    }

    // ── Shared bits ───────────────────────────────────────────────────────────

    private static List<JourneyMapHelper.StructureMarker> toMarkers(List<SeedStructureScanner.Found> found) {
        List<JourneyMapHelper.StructureMarker> markers = new ArrayList<>(found.size());
        for (SeedStructureScanner.Found f : found) {
            StructureStyle s = StructureStyle.of(f.id());
            markers.add(new JourneyMapHelper.StructureMarker(StructureStyle.pretty(f.id()), s.family,
                f.x(), f.y(), f.z(), f.minX(), f.minZ(), f.maxX(), f.maxZ(),
                s.rgb, s.icon, s.iconTex, s.landmark));
        }
        return markers;
    }

    /** Tell the user once per scan when the map can't show results, instead of failing silently. */
    private void noteMapAvailability() {
        if (!JourneyMapHelper.isAvailable()) {
            logDirect("Map overlay off: " + JourneyMapHelper.unavailableReason()
                + " Results are still listed here.", ChatFormatting.YELLOW);
        }
    }

    private static MutableComponent gotoLink(SeedStructureScanner.Found f, int dist) {
        String text = StructureStyle.pretty(f.id()) + " @ " + f.x() + ", " + f.z() + " (" + dist + "m)";
        MutableComponent c = Component.literal(text);
        c.setStyle(c.getStyle()
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click: #goto " + f.x() + " " + f.z())))
            .withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "goto " + f.x() + " " + f.z())));
        return c;
    }

    private void addLandmarkWaypoints(List<SeedStructureScanner.Found> found) {
        int made = 0;
        for (SeedStructureScanner.Found f : found) {
            StructureStyle s = StructureStyle.of(f.id());
            if (!s.landmark) continue;
            String key = f.id() + "@" + f.x() + "," + f.z();
            synchronized (WAYPOINTS_MADE) {
                if (!WAYPOINTS_MADE.add(key)) continue;
            }
            JourneyMapHelper.addWaypoint(s.family, new BlockPos(f.x(), Math.max(f.y(), 64), f.z()), s.rgb);
            if (++made >= MAX_WAYPOINTS) break;
        }
        if (made > 0) logDirect("Added " + made + " landmark waypoint" + (made == 1 ? "" : "s") + " to JourneyMap.");
    }

    /** Progress on the action bar; {@code null} clears it. Safe from any thread. */
    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui == null || mc.gui.hud == null) return;
            mc.gui.hud.setOverlayMessage(text == null ? Component.empty()
                : Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        });
    }

    /** Leaving a world: stop any scan and drop the map layers (called from the disconnect hook). */
    public static void onDisconnect() {
        GENERATION.incrementAndGet();
        JourneyMapHelper.reset();
        synchronized (WAYPOINTS_MADE) {
            WAYPOINTS_MADE.clear();
        }
    }

    private long resolveSeed() throws CommandInvalidStateException {
        long detected = detectSingleplayerSeed();
        if (detected != Long.MIN_VALUE) {
            if (!ClientStructureFinder.hasSeed() || ClientStructureFinder.getSeed() != detected) {
                ClientStructureFinder.setSeed(detected);
            }
            return detected;
        }
        if (!ClientStructureFinder.hasSeed()) {
            throw new CommandInvalidStateException("No seed stored for " + ClientStructureFinder.scope()
                + ". Enter it first:  #seedinput <seed>");
        }
        long seed = ClientStructureFinder.getSeed();
        if (ClientStructureFinder.check(seed) == ClientStructureFinder.Verdict.MISMATCH) {
            logDirect("Warning: the stored seed doesn't match this server's seed hash — the map will be wrong. "
                + "Fix it with  #seedinput <seed>.", ChatFormatting.RED);
        }
        return seed;
    }

    /** The integrated server's seed, or MIN_VALUE when not in singleplayer. */
    private long detectSingleplayerSeed() {
        try {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return Long.MIN_VALUE;
            ServerLevel level = server.overworld();
            return level == null ? Long.MIN_VALUE : level.getSeed();
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    private static int parseInt(String s, String what) throws CommandInvalidStateException {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new CommandInvalidStateException("Expected a number for " + what + ", got '" + s + "'.");
        }
    }

    /** Friendly aliases → substrings of structure ids. */
    private static String normalizeFilter(String f) {
        return switch (f) {
            case "temple", "desert_temple" -> "desert_pyramid";
            case "witch", "witch_hut" -> "swamp_hut";
            case "outpost" -> "pillager_outpost";
            case "portal", "portals" -> "ruined_portal";
            case "treasure" -> "buried_treasure";
            case "city" -> "ancient_city";
            case "trial", "trials" -> "trial_chambers";
            case "villages" -> "village";
            case "mansions", "woodland_mansion" -> "mansion";
            case "monuments", "ocean_monument" -> "monument";
            case "fortresses", "nether_fortress" -> "fortress";
            case "bastions" -> "bastion";
            default -> f;
        };
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        String p = "";
        try {
            while (args.has(2)) args.get();
            p = args.hasAny() ? args.peekString().toLowerCase(Locale.ROOT) : "";
        } catch (Exception ignored) {
        }
        final String pf = p;
        return Stream.of("1000", "2000", "4000", "off", "wp", "strongholds", "at",
                "village", "monument", "mansion", "ancient_city", "trial_chambers", "outpost",
                "desert_pyramid", "jungle", "swamp_hut", "igloo", "ruined_portal", "shipwreck",
                "ocean_ruin", "buried_treasure", "mineshaft", "trail_ruins", "fortress", "bastion", "end_city")
            .filter(s -> s.startsWith(pf));
    }

    @Override
    public String getShortDesc() {
        return "Biome-validated seed map of every structure, drawn on JourneyMap";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Finds every structure from the world seed and draws it on the",
            "built-in JourneyMap fullscreen map, like mcseedmap.net.",
            "",
            "Each spot is validated by running vanilla's own structure",
            "generation offline (biomes, 3D biomes, terrain, exclusion",
            "zones), so a marker means the structure really spawns there.",
            "Assumes default vanilla worldgen (not amplified/large biomes).",
            "",
            "Seed: auto on singleplayer; on servers use #seedinput <seed>.",
            "",
            "Usage:",
            "> #seedmap                    - everything within 2000 blocks",
            "> #seedmap <radius>           - up to 8000 blocks",
            "> #seedmap <kinds...>         - e.g. #seedmap village monument",
            "> #seedmap <r> at <x> <z>     - centre elsewhere",
            "> #seedmap wp                 - also add waypoints on landmarks",
            "> #seedmap strongholds        - nearest strongholds (+all 128)",
            "> #seedmap off                - hide the overlay",
            "",
            "Right-click the JourneyMap fullscreen map → 'Seed map here'.",
            "Toggle the layer with the map-icon button on its toolbar.",
            "Right-click a structure → Baritone: go to / Add waypoint.",
            "",
            "Aliases: #structures, #smap"
        );
    }
}
