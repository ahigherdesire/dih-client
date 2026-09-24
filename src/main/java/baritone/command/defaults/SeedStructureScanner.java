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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import dihclient.util.worldgen.mc26_2.DihWorldgenContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Biome- and terrain-validated structure locator that runs entirely client-side from the seed.
 *
 * <p>This is what cubiomes does for real Minecraft versions, but instead of a re-implementation it
 * runs <b>vanilla's own code</b> against an offline copy of the vanilla worldgen registries
 * ({@link DihWorldgenContext}), so it is exact for this client's Minecraft version:
 * <ol>
 *   <li>{@code StructurePlacement.isStructureChunk} — grid spacing, frequency reduction and
 *       exclusion zones (e.g. outposts never spawn within 10 chunks of a village);</li>
 *   <li>the weighted pick between structures sharing a set (fortress vs bastion, the village
 *       variants), seeded exactly like {@code ChunkGenerator#createStructures};</li>
 *   <li>{@code Structure.generate(...).isValid()} — the real biome check at the real generation
 *       point, including 3D biomes (ancient cities, trial chambers) and terrain rules.</li>
 * </ol>
 * Strongholds come from the generator's ring placement ({@code getRingPositionsFor}).
 *
 * <p>Jigsaw structures (villages, outposts, trail ruins, trial chambers, bastions, ancient cities)
 * are validated with {@code Structure.findValidGenerationPoint}: vanilla decides validity (start
 * height, biome at the start piece) there and only assembles the pieces lazily afterwards, so
 * skipping the costly assembly gives the identical yes/no. Everything else is fully generated.
 * Candidates are evaluated in parallel.
 *
 * <p>Assumes a vanilla "default" world preset. Amplified / large-biomes / modded worldgen will not
 * match, the same limitation every seed map has.
 */
public final class SeedStructureScanner {

    /** One validated structure. Coordinates in blocks. */
    public record Found(String id, int x, int y, int z, int minX, int minZ, int maxX, int maxZ,
                        int chunkX, int chunkZ) {
        public long distSq(int ox, int oz) {
            long dx = x - ox, dz = z - oz;
            return dx * dx + dz * dz;
        }
    }

    /** Scan outcome; {@link #error} is non-null when validation could not run at all. */
    public static final class Result {
        public final List<Found> found = new ArrayList<>();
        public int candidates;
        public int rejected;
        public int failures;
        public boolean truncated;
        public long millis;
        public String error;
        /** First generation error, for diagnostics ({@link #failures} counts them all). */
        public String firstFailure;
    }

    /** Stop a scan after this long and return what we have. */
    private static final long TIME_BUDGET_MS = 120_000L;

    /**
     * Generation workers. Vanilla worldgen objects are thread-safe (the server generates chunks in
     * parallel with the same RandomState/BiomeSource/template manager). Kept below the core count
     * and at low priority so the game's own threads stay responsive.
     */
    private static final ExecutorService POOL;
    static {
        int threads = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
        AtomicInteger n = new AtomicInteger();
        POOL = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "SeedMapGen-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    private static final Object CONTEXT_LOCK = new Object();
    private static DihWorldgenContext cachedContext;
    private static long cachedSeed;
    private static ResourceKey<Level> cachedDim;

    /**
     * The scanner's own template manager: vanilla's StructureTemplate caches are plain HashMaps, so
     * sharing OreSim's manager across threads could corrupt them. Created lazily, once.
     */
    private static volatile StructureTemplateManager templates;

    /**
     * Structures whose generation touches shared mutable state are evaluated one at a time: template
     * structures (jigsaw, shipwrecks, ruins, portals…) read the non-thread-safe template caches, and
     * strongholds / nether fortresses keep their piece lists and weights in <i>static</i> fields
     * ({@code StrongholdPieces.currentPieces}, {@code NetherFortressPieces} weights). Everything in
     * {@link #PARALLEL_SAFE_TYPES} is self-contained and runs fully in parallel.
     */
    private static final Object TEMPLATE_LOCK = new Object();
    private static final Set<String> PARALLEL_SAFE_TYPES = Set.of(
        "buried_treasure", "desert_pyramid", "jungle_temple", "mineshaft",
        "ocean_monument", "swamp_hut");

    private SeedStructureScanner() {
    }

    /**
     * Builds (or reuses) the offline worldgen context. The first call per seed+dimension loads the
     * vanilla registries and resolves stronghold rings, which takes a few seconds — call it off the
     * client thread.
     */
    public static DihWorldgenContext context(long seed, ResourceKey<Level> dim) {
        synchronized (CONTEXT_LOCK) {
            if (cachedContext != null && cachedSeed == seed && dim.equals(cachedDim)) return cachedContext;
            DihWorldgenContext ctx = DihWorldgenContext.create(seed, dim);
            if (templates == null) templates = ctx.newIsolatedStructureTemplateManager();
            cachedContext = ctx;
            cachedSeed = seed;
            cachedDim = dim;
            return ctx;
        }
    }

    /** Whether {@link #context} is already built for this seed+dimension (next scan starts instantly). */
    public static boolean isWarm(long seed, ResourceKey<Level> dim) {
        synchronized (CONTEXT_LOCK) {
            return cachedContext != null && cachedSeed == seed && dim.equals(cachedDim);
        }
    }

    /**
     * Find every structure within {@code blockRadius} of ({@code originX}, {@code originZ}).
     *
     * @param idFilter  keep only structures whose id path passes; {@code null} keeps everything
     * @param cancelled polled between candidates so a newer scan can abort this one
     */
    public static Result scan(long seed, ResourceKey<Level> dim, int originX, int originZ, int blockRadius,
                              Predicate<String> idFilter, BooleanSupplier cancelled) {
        return scan(seed, dim, originX, originZ, blockRadius, idFilter, cancelled, false, null);
    }

    /** Receives (done, total) as candidates finish; called from the scanning thread. */
    public interface Progress {
        void update(int done, int total);
    }

    /** As {@link #scan}, reporting progress (throttled by the caller). */
    public static Result scan(long seed, ResourceKey<Level> dim, int originX, int originZ, int blockRadius,
                              Predicate<String> idFilter, BooleanSupplier cancelled, Progress progress) {
        return scan(seed, dim, originX, originZ, blockRadius, idFilter, cancelled, false, progress);
    }

    /**
     * As {@link #scan}; {@code exact} forces full piece assembly for jigsaw structures too (slow —
     * used by tests to prove the fast path decides validity identically).
     */
    static Result scan(long seed, ResourceKey<Level> dim, int originX, int originZ, int blockRadius,
                       Predicate<String> idFilter, BooleanSupplier cancelled, boolean exact) {
        return scan(seed, dim, originX, originZ, blockRadius, idFilter, cancelled, exact, null);
    }

    private static Result scan(long seed, ResourceKey<Level> dim, int originX, int originZ, int blockRadius,
                               Predicate<String> idFilter, BooleanSupplier cancelled, boolean exact,
                               Progress progress) {
        Result result = new Result();
        long start = System.currentTimeMillis();
        DihWorldgenContext ctx;
        try {
            ctx = context(seed, dim);
        } catch (Throwable t) {
            result.error = "offline worldgen unavailable for " + dim.identifier() + " (" + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()) + ")";
            result.millis = System.currentTimeMillis() - start;
            return result;
        }

        // 1. Enumerate grid candidates (cheap, sequential).
        List<Candidate> candidates = new ArrayList<>();
        ChunkGeneratorStructureState state = ctx.structureState();
        long radiusSq = (long) blockRadius * blockRadius;
        int chunkRadius = (blockRadius >> 4) + 1;
        int originChunkX = originX >> 4;
        int originChunkZ = originZ >> 4;

        for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
            StructureSet set = setHolder.value();
            if (idFilter != null && set.structures().stream().noneMatch(e -> idFilter.test(idOf(e.structure())))) {
                continue;
            }
            StructurePlacement placement = set.placement();

            if (placement instanceof RandomSpreadStructurePlacement rsp) {
                int spacing = rsp.spacing();
                int regMinX = Math.floorDiv(originChunkX - chunkRadius, spacing);
                int regMaxX = Math.floorDiv(originChunkX + chunkRadius, spacing);
                int regMinZ = Math.floorDiv(originChunkZ - chunkRadius, spacing);
                int regMaxZ = Math.floorDiv(originChunkZ + chunkRadius, spacing);
                for (int rx = regMinX; rx <= regMaxX; rx++) {
                    for (int rz = regMinZ; rz <= regMaxZ; rz++) {
                        // getPotentialStructureChunk takes a CHUNK coordinate and derives the region
                        // itself; passing the region's first chunk selects exactly that region.
                        ChunkPos chunk = rsp.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                        if (!inRange(chunk, originX, originZ, radiusSq)) continue;
                        if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) continue;
                        candidates.add(new Candidate(set, chunk, exact));
                    }
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                List<ChunkPos> positions = state.getRingPositionsFor(rings);
                if (positions == null) continue;
                for (ChunkPos chunk : positions) {
                    if (inRange(chunk, originX, originZ, radiusSq)) candidates.add(new Candidate(set, chunk, false));
                }
            }
        }

        // 2. Validate in parallel.
        result.candidates = candidates.size();
        // The budget covers validation only: building the offline worldgen context and the stronghold
        // rings is a one-off cost that can take a while on a slow or busy PC.
        final long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;
        List<Future<Outcome>> futures = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            futures.add(POOL.submit(() -> {
                if (cancelled.getAsBoolean() || System.currentTimeMillis() > deadline) return Outcome.SKIPPED;
                return evaluate(ctx, c, idFilter);
            }));
        }

        Set<String> seen = new HashSet<>();
        int done = 0;
        for (Future<Outcome> f : futures) {
            if (progress != null) progress.update(done++, futures.size());
            Outcome o;
            try {
                o = f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                futures.forEach(x -> x.cancel(true));
                result.truncated = true;
                break;
            } catch (ExecutionException e) {
                result.failures++;
                if (result.firstFailure == null) result.firstFailure = String.valueOf(e.getCause());
                continue;
            }
            switch (o.kind) {
                case FOUND -> {
                    // Two sets never share a start chunk in vanilla, but guard against it anyway.
                    if (seen.add(o.found.id() + "@" + o.found.x() + "," + o.found.z())) result.found.add(o.found);
                }
                case FILTERED -> { }
                case REJECTED -> result.rejected++;
                case FAILED -> {
                    result.failures++;
                    if (result.firstFailure == null) result.firstFailure = o.error;
                }
                case SKIPPED -> result.truncated = true;
            }
        }

        result.found.sort(Comparator.comparing(Found::id).thenComparingInt(Found::x).thenComparingInt(Found::z));
        result.millis = System.currentTimeMillis() - start;
        return result;
    }

    /**
     * All stronghold positions for the seed (overworld only), regardless of radius — there are only
     * 128, and "where is the nearest one" is the common question.
     */
    public static List<Found> strongholds(long seed, BooleanSupplier cancelled) {
        List<Found> out = new ArrayList<>();
        DihWorldgenContext ctx = context(seed, Level.OVERWORLD);
        ChunkGeneratorStructureState state = ctx.structureState();
        for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
            if (!(setHolder.value().placement() instanceof ConcentricRingsStructurePlacement rings)) continue;
            List<ChunkPos> positions = state.getRingPositionsFor(rings);
            if (positions == null) continue;
            for (ChunkPos chunk : positions) {
                if (cancelled.getAsBoolean()) return out;
                Outcome o = evaluate(ctx, new Candidate(setHolder.value(), chunk, true), null);
                if (o.kind == Kind.FOUND) out.add(o.found);
            }
        }
        return out;
    }

    // ── Candidate evaluation ──

    private record Candidate(StructureSet set, ChunkPos chunk, boolean exact) {
    }

    private enum Kind { FOUND, FILTERED, REJECTED, FAILED, SKIPPED }

    private record Outcome(Kind kind, Found found, String error) {
        static final Outcome REJECTED = new Outcome(Kind.REJECTED, null, null);
        static final Outcome FILTERED = new Outcome(Kind.FILTERED, null, null);
        static final Outcome SKIPPED = new Outcome(Kind.SKIPPED, null, null);
    }

    private static Outcome evaluate(DihWorldgenContext ctx, Candidate c, Predicate<String> idFilter) {
        try {
            Placed p = select(ctx, c.set(), c.chunk(), c.exact());
            if (p == null) return Outcome.REJECTED;
            String id = idOf(p.structure());
            if (idFilter != null && !idFilter.test(id)) return Outcome.FILTERED;
            ChunkPos ch = c.chunk();
            if (p.box() == null) {
                // Jigsaw fast path: the start piece position (village centre, outpost tower…).
                BlockPos at = p.start();
                return new Outcome(Kind.FOUND, new Found(id, at.getX(), at.getY(), at.getZ(),
                    at.getX(), at.getZ(), at.getX(), at.getZ(), ch.x(), ch.z()), null);
            }
            BoundingBox box = p.box();
            int x = (box.minX() + box.maxX()) / 2;
            int z = (box.minZ() + box.maxZ()) / 2;
            if (box.maxX() - box.minX() > 512 || box.maxZ() - box.minZ() > 512) {
                BlockPos locate = c.set().placement().getLocatePos(ch);
                x = locate.getX();
                z = locate.getZ();
            }
            return new Outcome(Kind.FOUND, new Found(id, x, box.minY(), z,
                box.minX(), box.minZ(), box.maxX(), box.maxZ(), ch.x(), ch.z()), null);
        } catch (Throwable t) {
            // One odd candidate (e.g. a template that fails to load) must not sink the whole scan.
            StringBuilder where = new StringBuilder(t.getClass().getSimpleName());
            if (t.getMessage() != null) where.append(": ").append(t.getMessage());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(8, st.length); i++) where.append(" <- ").append(st[i]);
            return new Outcome(Kind.FAILED, null, where.toString());
        }
    }

    // ── Vanilla structure selection, mirrored from ChunkGenerator#createStructures ──

    /** A structure that generates: either its full bounding box, or (jigsaw fast path) its start. */
    private record Placed(Holder<Structure> structure, BoundingBox box, BlockPos start) {
    }

    private static Placed select(DihWorldgenContext ctx, StructureSet set, ChunkPos chunk, boolean exact) {
        List<StructureSet.StructureSelectionEntry> entries = set.structures();
        if (entries.isEmpty()) return null;
        if (entries.size() == 1) return tryPlace(ctx, entries.get(0).structure(), chunk, exact);

        List<StructureSet.StructureSelectionEntry> pool = new ArrayList<>(entries);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(ctx.seed(), chunk.x(), chunk.z());
        int total = 0;
        for (StructureSet.StructureSelectionEntry e : pool) total += e.weight();

        while (!pool.isEmpty() && total > 0) {
            int roll = random.nextInt(total);
            int index = 0;
            for (StructureSet.StructureSelectionEntry e : pool) {
                roll -= e.weight();
                if (roll < 0) break;
                index++;
            }
            StructureSet.StructureSelectionEntry chosen = pool.get(index);
            Placed placed = tryPlace(ctx, chosen.structure(), chunk, exact);
            if (placed != null) return placed;
            pool.remove(index);
            total -= chosen.weight();
        }
        return null;
    }

    /** {@code ChunkGenerator#tryGenerateStructure} without writing anything. */
    private static Placed tryPlace(DihWorldgenContext ctx, Holder<Structure> holder, ChunkPos chunk, boolean exact) {
        if (!parallelSafe(holder.value())) {
            synchronized (TEMPLATE_LOCK) {
                return tryPlaceUnlocked(ctx, holder, chunk, exact);
            }
        }
        return tryPlaceUnlocked(ctx, holder, chunk, exact);
    }

    private static boolean parallelSafe(Structure structure) {
        Identifier type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        // Unknown (modded / future) types are assumed unsafe: correct, just not parallel.
        return type != null && PARALLEL_SAFE_TYPES.contains(type.getPath());
    }

    private static Placed tryPlaceUnlocked(DihWorldgenContext ctx, Holder<Structure> holder, ChunkPos chunk,
                                           boolean exact) {
        Structure structure = holder.value();
        StructureTemplateManager tm = templates != null ? templates : ctx.structureTemplates();
        HolderSet<Biome> biomes = structure.biomes();
        if (!exact && structure instanceof JigsawStructure) {
            Structure.GenerationContext gc = new Structure.GenerationContext(ctx.registryAccess(), ctx.generator(),
                ctx.biomeSource(), ctx.randomState(), tm, ctx.seed(), chunk,
                ctx.heightAccessor(), biomes::contains);
            Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(gc);
            return stub.map(st -> new Placed(holder, null, st.position())).orElse(null);
        }
        StructureStart start = structure.generate(holder, ctx.dimension(), ctx.registryAccess(), ctx.generator(),
            ctx.biomeSource(), ctx.randomState(), tm, ctx.seed(), chunk, 0,
            ctx.heightAccessor(), biomes::contains);
        return start != null && start.isValid() ? new Placed(holder, start.getBoundingBox(), null) : null;
    }

    // ── Helpers ──

    private static boolean inRange(ChunkPos chunk, int ox, int oz, long radiusSq) {
        long dx = ((long) chunk.x() << 4) + 8 - ox;
        long dz = ((long) chunk.z() << 4) + 8 - oz;
        return dx * dx + dz * dz <= radiusSq;
    }

    public static String idOf(Holder<Structure> holder) {
        return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
    }
}
