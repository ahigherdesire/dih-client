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
import baritone.util.JourneyMapHelper;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Locates the nearest named structure and navigates there.
 *
 * <p>In singleplayer the integrated server's ChunkGenerator is queried directly,
 * which covers every structure in the world generator regardless of whether
 * the chunks are loaded.
 *
 * <p>In multiplayer, a stored world seed (entered via {@code #seedinput}) is used
 * together with the client's registry data to find structures via their
 * RandomSpreadStructurePlacement grid math.
 *
 * <p>Each alias resolves to either {@code "tag:<tagPath>"} (a StructureTags entry that
 * exists in MC 26.1.2) or {@code "id:<structureId>"} (a direct structure registry ID for
 * structures that have no tag).  The tag names in MC 26.1.2 are singular, not plural.
 */
public class StructureCommand extends Command {

    /**
     * Maps user-facing names to a query string used internally.
     * Format: {@code "tag:<tagPath>"} or {@code "id:<structureId>"}.
     *
     * <p>Package-accessible so {@link WhereCommand} can share the table.
     */
    static final Map<String, String> ALIASES = new HashMap<>();
    static {
        // --- structures with a StructureTags entry in MC 26.1.2 (all singular) ---
        // Village: use direct structure IDs rather than the tag because in MC 26.1.2
        // the #minecraft:village tag may resolve to 0 members, causing silent search failure.
        // All five vanilla village variants are listed explicitly.
        ALIASES.put("village", "ids:village_plains,village_desert,village_savanna,village_snowy,village_taiga");
        ALIASES.put("mineshaft",         "tag:mineshaft");
        ALIASES.put("mine",              "tag:mineshaft");
        ALIASES.put("shipwreck",         "tag:shipwreck");
        ALIASES.put("ruined_portal",     "tag:ruined_portal");
        ALIASES.put("ocean_ruin",        "tag:ocean_ruin");
        ALIASES.put("ocean_ruins",       "tag:ocean_ruin");

        // these structures are identified by their explorer-map tags
        ALIASES.put("stronghold",        "tag:eye_of_ender_located");
        ALIASES.put("mansion",           "tag:on_woodland_explorer_maps");
        ALIASES.put("woodland_mansion",  "tag:on_woodland_explorer_maps");
        ALIASES.put("monument",          "tag:on_ocean_explorer_maps");
        ALIASES.put("ocean_monument",    "tag:on_ocean_explorer_maps");
        ALIASES.put("buried_treasure",   "tag:on_treasure_maps");
        ALIASES.put("jungle_pyramid",    "tag:on_jungle_explorer_maps");
        ALIASES.put("jungle_temple",     "tag:on_jungle_explorer_maps");
        ALIASES.put("swamp_hut",         "tag:on_swamp_explorer_maps");
        ALIASES.put("witch_hut",         "tag:on_swamp_explorer_maps");
        ALIASES.put("trial_chambers",    "tag:on_trial_chambers_maps");
        ALIASES.put("trial_chamber",     "tag:on_trial_chambers_maps");

        // --- structures with no StructureTags entry in MC 26.1.2 (use direct ID) ---
        ALIASES.put("nether_fortress",   "id:fortress");
        ALIASES.put("fortress",          "id:fortress");
        ALIASES.put("bastion",           "id:bastion_remnant");
        ALIASES.put("bastion_remnant",   "id:bastion_remnant");
        ALIASES.put("ancient_city",      "id:ancient_city");
        ALIASES.put("end_city",          "id:end_city");
        ALIASES.put("desert_pyramid",    "id:desert_pyramid");
        ALIASES.put("desert_temple",     "id:desert_pyramid");
        ALIASES.put("pillager_outpost",  "id:pillager_outpost");
        ALIASES.put("outpost",           "id:pillager_outpost");
        ALIASES.put("igloo",             "id:igloo");
        ALIASES.put("trail_ruins",       "id:trail_ruins");
    }

    public StructureCommand(IBaritone baritone) {
        super(baritone, "structure", "struct", "locate");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        args.requireMax(1);

        String input = args.getString().toLowerCase();
        // e.g. "tag:village" or "id:fortress"; default to tag lookup for unknown names
        String query = ALIASES.getOrDefault(input, "tag:" + input);

        final BlockPos searchOrigin = ctx.playerFeet();

        // ── Singleplayer path ────────────────────────────────────────────────
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ServerLevel serverLevel = server.getLevel(ctx.world().dimension());
            if (serverLevel == null) {
                throw new CommandInvalidStateException(
                    "Current dimension is not available on the integrated server.");
            }
            // Give immediate feedback, then dispatch to the server thread.
            // resolveStructures uses serverLevel.registryAccess() and
            // findNearestMapStructure may trigger chunk loading — both must run
            // on the integrated-server thread, not the client thread.
            logDirect("Searching for nearest '" + input + "'...");
            server.execute(() -> {
                HolderSet<Structure> holderSet = resolveStructures(query, serverLevel);
                if (holderSet == null) {
                    Minecraft.getInstance().execute(() ->
                        logDirect("Unknown structure: '" + input + "'. Check #help structure for a list."));
                    return;
                }
                int holderCount = 0;
                for (Holder<Structure> h : holderSet) holderCount++;
                if (holderCount == 0) {
                    // Tag resolved but is empty — findNearestMapStructure would return null
                    // immediately with no useful error. Catch it early.
                    Minecraft.getInstance().execute(() ->
                        logDirect("Structure tag resolved but contains 0 variants for '" + input
                            + "' in this MC version — cannot search. "
                            + "Try a specific variant name (e.g. 'village_plains')."));
                    return;
                }
                final int countSnap = holderCount;
                Minecraft.getInstance().execute(() ->
                    logDirect("Found " + countSnap + " variant" + (countSnap == 1 ? "" : "s") + ", searching..."));

                Pair<BlockPos, Holder<Structure>> found;
                try {
                    found = serverLevel.getChunkSource().getGenerator()
                        .findNearestMapStructure(serverLevel, holderSet, searchOrigin, 100, false);
                } catch (Throwable t) {
                    final String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                    Minecraft.getInstance().execute(() ->
                        logDirect("Structure search failed: " + msg));
                    return;
                }
                final BlockPos result = found == null ? null : found.getFirst();
                final String variantName;
                if (found == null) {
                    variantName = null;
                } else {
                    variantName = found.getSecond().unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("?");
                }
                Minecraft.getInstance().execute(() -> {
                    if (result == null) {
                        logDirect("No '" + input + "' found within search range. "
                            + "Try exploring further or increasing the search area.");
                        return;
                    }
                    int dx = result.getX() - searchOrigin.getX();
                    int dz = result.getZ() - searchOrigin.getZ();
                    int dist = (int) Math.sqrt(dx * dx + dz * dz);
                    logDirect("Found '" + variantName + "' (matched '" + input + "')  X="
                        + result.getX() + "  Z=" + result.getZ() + "  (~" + dist + " blocks away)");
                    JourneyMapHelper.addWaypoint(
                        input + " (" + variantName + ")", result, JourneyMapHelper.COLOR_STRUCTURE);
                    baritone.getCustomGoalProcess().setGoalAndPath(
                        new GoalXZ(result.getX(), result.getZ()));
                });
            });
            return;
        }

        // ── Multiplayer path: seed-based, biome-validated (SeedStructureScanner) ──
        if (!ClientStructureFinder.hasSeed()) {
            throw new CommandInvalidStateException(
                "You are on multiplayer. Enter your world seed first:  #seedinput <seed>. "
                + "Then try  #structure " + input + "  again.");
        }
        final long seed = ClientStructureFinder.getSeed();
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = ctx.world().dimension();
        logDirect("Searching for nearest '" + input + "' from seed " + seed
            + (SeedStructureScanner.isWarm(seed, dim) ? "…" : " (first search loads vanilla worldgen, a few seconds)…"));

        Thread seedThread = new Thread(() -> {
            SeedStructureScanner.Found best = null;
            String error = null;
            try {
                best = findNearestSeeded(query, seed, dim, searchOrigin.getX(), searchOrigin.getZ());
            } catch (IllegalStateException e) {
                error = e.getMessage();
            } catch (Throwable t) {
                error = "Structure search failed: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " — " + t.getMessage());
            }
            final SeedStructureScanner.Found found = best;
            final String err = error;
            Minecraft.getInstance().execute(() -> {
                if (err != null) {
                    logDirect(err);
                    return;
                }
                if (found == null) {
                    logDirect("No '" + input + "' within 8192 blocks for this seed (biome-validated).");
                    return;
                }
                int dx = found.x() - searchOrigin.getX();
                int dz = found.z() - searchOrigin.getZ();
                int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
                logDirect("Found '" + found.id() + "' (seed, biome-validated)  X=" + found.x()
                    + "  Z=" + found.z() + "  (~" + dist + " blocks away)");
                JourneyMapHelper.addWaypoint(input + " (" + found.id() + ")",
                    new BlockPos(found.x(), Math.max(found.y(), 64), found.z()), JourneyMapHelper.COLOR_STRUCTURE);
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(found.x(), found.z()));
            });
        }, "BaritoneStructureSeedSearch");
        seedThread.setDaemon(true);
        seedThread.start();
    }

    /**
     * Nearest biome-validated structure matching an {@link #ALIASES} query, searched from the seed in
     * growing rings (1.5k → 4k → 8k blocks). Strongholds use the generator's ring positions.
     *
     * @return the nearest match, or {@code null} if none within 8192 blocks
     * @throws IllegalStateException with a user-facing message for unknown names / unavailable worldgen
     */
    static SeedStructureScanner.Found findNearestSeeded(String query, long seed,
                                                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
                                                        int x, int z) {
        SeedStructureScanner.Found best = null;
        if (query.equals("tag:eye_of_ender_located")) {
            if (!net.minecraft.world.level.Level.OVERWORLD.equals(dim)) {
                throw new IllegalStateException("Strongholds are in the Overworld.");
            }
            for (SeedStructureScanner.Found f : SeedStructureScanner.strongholds(seed, () -> false)) {
                if (best == null || f.distSq(x, z) < best.distSq(x, z)) best = f;
            }
            return best;
        }
        java.util.Set<String> ids = resolveIds(query, seed, dim);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Unknown structure '" + query.replaceFirst("^[a-z]+:", "")
                + "'. Check #help structure for a list.");
        }
        for (int radius : new int[]{1536, 4096, 8192}) {
            SeedStructureScanner.Result r = SeedStructureScanner.scan(seed, dim, x, z, radius, ids::contains, () -> false);
            if (r.error != null) throw new IllegalStateException("Seed search unavailable: " + r.error);
            for (SeedStructureScanner.Found f : r.found) {
                if (best == null || f.distSq(x, z) < best.distSq(x, z)) best = f;
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Expands an {@link #ALIASES} query ({@code tag:…}, {@code id:…}, {@code ids:a,b}) to structure id
     * paths, resolving tags against the offline vanilla registries so it works on any server.
     */
    static java.util.Set<String> resolveIds(String query, long seed,
                                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (query.startsWith("ids:")) {
            for (String id : query.substring(4).split(",")) if (!id.isBlank()) ids.add(id.trim());
        } else if (query.startsWith("id:")) {
            ids.add(query.substring(3));
        } else if (query.startsWith("tag:")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(query.substring(4)));
            SeedStructureScanner.context(seed, dim).registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .get(tag).ifPresent(set -> set.forEach(h -> ids.add(SeedStructureScanner.idOf(h))));
        }
        return ids;
    }

    /**
     * Resolves a query string to a {@link HolderSet} of structures.
     *
     * @param query    {@code "tag:<tagPath>"} or {@code "id:<structureId>"}
     * @param level    server level (used for its registry access)
     * @return the holder set, or {@code null} if the tag/id does not exist
     */
    static HolderSet<Structure> resolveStructures(String query, ServerLevel level) {
        HolderLookup.RegistryLookup<Structure> lookup =
            level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        if (query.startsWith("tag:")) {
            String tagName = query.substring(4);
            TagKey<Structure> tag = TagKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace(tagName));
            var namedOpt = lookup.get(tag);
            if (namedOpt.isEmpty()) return null;
            return namedOpt.get();

        } else if (query.startsWith("id:")) {
            String idName = query.substring(3);
            ResourceKey<Structure> key = ResourceKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace(idName));
            var holderOpt = lookup.get(key);
            if (holderOpt.isEmpty()) return null;
            return HolderSet.direct(holderOpt.get());

        } else if (query.startsWith("ids:")) {
            // Comma-separated list of direct structure IDs — used when a tag is unreliable.
            // Example: "ids:village_plains,village_desert,village_savanna,village_snowy,village_taiga"
            String[] ids = query.substring(4).split(",");
            List<Holder<Structure>> holders = new ArrayList<>();
            for (String id : ids) {
                ResourceKey<Structure> key = ResourceKey.create(
                    Registries.STRUCTURE, Identifier.withDefaultNamespace(id.trim()));
                lookup.get(key).ifPresent(holders::add);
            }
            if (holders.isEmpty()) return null;
            return HolderSet.direct(holders);
        }

        return null;
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String p = prefix;
            return ALIASES.keySet().stream()
                .filter(k -> k.startsWith(p))
                .sorted();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Find and navigate to a nearby structure";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Finds the nearest named structure and tells Baritone to navigate there.",
            "",
            "Singleplayer: queries the integrated server's chunk generator — works even",
            "  for unexplored areas, no seed needed.",
            "Multiplayer: finds it from the world seed, biome-validated by running",
            "  vanilla structure generation offline (strongholds included).",
            "  Enter your world seed first with  #seedinput <seed>.",
            "See #seedmap to draw every structure on the JourneyMap map.",
            "",
            "Usage:",
            "> structure village          - nearest village (any type)",
            "> structure stronghold       - nearest stronghold (End portal room)",
            "> structure nether_fortress  - nearest Nether fortress",
            "> structure fortress         - same as nether_fortress",
            "> structure bastion          - nearest bastion remnant",
            "> structure mansion          - nearest woodland mansion",
            "> structure monument         - nearest ocean monument",
            "> structure ancient_city     - nearest ancient city",
            "> structure end_city         - nearest End city",
            "> structure mineshaft        - nearest mineshaft",
            "> structure buried_treasure  - nearest buried treasure",
            "> structure desert_pyramid   - nearest desert pyramid",
            "> structure jungle_pyramid   - nearest jungle temple",
            "> structure pillager_outpost - nearest pillager outpost",
            "> structure shipwreck        - nearest shipwreck",
            "> structure igloo            - nearest igloo",
            "> structure swamp_hut        - nearest swamp hut / witch hut",
            "> structure ocean_ruin       - nearest ocean ruin",
            "> structure ruined_portal    - nearest ruined portal",
            "> structure trial_chambers   - nearest trial chambers",
            "> structure trail_ruins      - nearest trail ruins"
        );
    }
}
