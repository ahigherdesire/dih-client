package baritone.command.defaults;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import dihclient.util.worldgen.mc26_2.DihWorldgenContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless checks of the seed map's structure validation against vanilla worldgen (no game client).
 */
final class SeedStructureScannerTest {

    private static final long SEED = 1234567890123456789L;
    private static final int RADIUS = 2000;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.ITEM
            .get(Identifier.withDefaultNamespace("trial_key"))
            .orElseThrow()
            .bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        net.minecraft.server.packs.VanillaPackResources vanilla =
            net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource();
        net.minecraft.server.packs.resources.ResourceManager resources =
            new net.minecraft.server.packs.resources.MultiPackResourceManager(
                //? if >=26.3 {
                /*net.minecraft.server.packs.PackType.SERVER_DATA, java.util.List.of(vanilla.fullResources()));
                *///?} else {
                net.minecraft.server.packs.PackType.SERVER_DATA, java.util.List.of(vanilla));
                //?}
        net.minecraft.core.RegistryAccess builtIn =
            net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, builtIn)
            .forEach(net.minecraft.core.Registry.PendingTags::apply);
    }

    @Test
    void overworldScanIsValidatedDeterministicAndInRange() {
        SeedStructureScanner.Result a = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, RADIUS, null, () -> false);
        assertNull(a.error, a.error);
        assertFalse(a.truncated, "scan should finish inside its time budget");
        System.out.printf("overworld r=%d: %d found, %d candidates, %d rejected, %d failures, %d ms%n",
            RADIUS, a.found.size(), a.candidates, a.rejected, a.failures, a.millis);

        assertFalse(a.found.isEmpty(), "a 2000-block overworld radius always holds structures");
        assertEquals(0, a.failures, "no candidate should throw during generation, first: " + a.firstFailure);
        assertTrue(a.rejected > 0, "biome/terrain validation must reject some grid spots");
        assertEquals(a.candidates, a.found.size() + a.rejected, "every candidate is either kept or rejected");

        Set<String> families = new HashSet<>();
        for (SeedStructureScanner.Found f : a.found) {
            families.add(StructureStyle.of(f.id()).family);
            assertTrue(f.minX() <= f.x() && f.x() <= f.maxX() + 1, "centre inside its bounding box: " + f);
        }
        assertTrue(families.contains("Village"), "villages expected in 2000 blocks: " + families);
        assertFalse(families.contains("Nether Fortress"), "no nether structures in the overworld");
        assertFalse(families.contains("End City"), "no end structures in the overworld");

        SeedStructureScanner.Result b = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, RADIUS, null, () -> false);
        assertEquals(a.found, b.found, "same seed + area must give identical results");
    }

    @Test
    void noStructureIsReportedTwice() {
        SeedStructureScanner.Result r = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, RADIUS, null, () -> false);
        Set<String> spots = new HashSet<>();
        for (SeedStructureScanner.Found f : r.found) {
            assertTrue(spots.add(f.chunkX() + "," + f.chunkZ() + "," + f.id()), "duplicate " + f);
        }
    }

    @Test
    void jigsawFastPathMatchesFullGeneration() {
        // The fast path skips jigsaw piece assembly; it must accept/reject exactly what full
        // vanilla generation does, including which variant a multi-structure set picks.
        for (int[] origin : new int[][]{{0, 0}, {5000, -3000}, {-7000, 6000}, {12000, 9000}}) {
            SeedStructureScanner.Result fast = SeedStructureScanner.scan(SEED, Level.OVERWORLD, origin[0], origin[1], 1200,
                null, () -> false, false);
            SeedStructureScanner.Result exact = SeedStructureScanner.scan(SEED, Level.OVERWORLD, origin[0], origin[1], 1200,
                null, () -> false, true);
            Set<String> onlyExact = new java.util.TreeSet<>(keys(exact.found));
            onlyExact.removeAll(keys(fast.found));
            Set<String> onlyFast = new java.util.TreeSet<>(keys(fast.found));
            onlyFast.removeAll(keys(exact.found));
            assertTrue(onlyExact.isEmpty() && onlyFast.isEmpty(), "fast path differs near " + origin[0] + ","
                + origin[1] + ": only-full=" + onlyExact + " only-fast=" + onlyFast
                + " failures fast/full=" + fast.failures + "/" + exact.failures + " " + fast.firstFailure + " | " + exact.firstFailure);
            System.out.printf("fastpath @%d,%d: %d found, fast %d ms vs full %d ms%n",
                origin[0], origin[1], fast.found.size(), fast.millis, exact.millis);
        }
    }

    private static Set<String> keys(List<SeedStructureScanner.Found> found) {
        Set<String> out = new java.util.TreeSet<>();
        for (SeedStructureScanner.Found f : found) out.add(f.id() + "@" + f.chunkX() + "," + f.chunkZ());
        return out;
    }

    @Test
    void filterKeepsOnlyRequestedStructures() {
        SeedStructureScanner.Result r = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, 3000,
            id -> id.startsWith("village_"), () -> false);
        assertNull(r.error);
        assertFalse(r.found.isEmpty());
        for (SeedStructureScanner.Found f : r.found) assertTrue(f.id().startsWith("village_"), f.id());
    }

    @Test
    void pillagerOutpostsRespectVillageExclusionZone() {
        // Vanilla: outposts may not spawn within 10 chunks of a village *grid* position.
        DihWorldgenContext ctx = SeedStructureScanner.context(SEED, Level.OVERWORLD);
        ChunkGeneratorStructureState state = ctx.structureState();
        Holder<StructureSet> villages = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
            .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.VILLAGES);

        SeedStructureScanner.Result outposts = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, 6000,
            id -> id.equals("pillager_outpost"), () -> false);
        assertNull(outposts.error);
        for (SeedStructureScanner.Found o : outposts.found) {
            ChunkPos c = new ChunkPos(o.x() >> 4, o.z() >> 4);
            // Box centre can sit a chunk off the start chunk; allow for that.
            assertFalse(state.hasStructureChunkInRange(villages, c.x(), c.z(), 8),
                "outpost within village exclusion zone: " + o);
        }
        System.out.println("outposts within 6000: " + outposts.found.size());
        assertFalse(outposts.found.isEmpty(), "outposts are common; none in 6000 blocks means the search is broken");
    }

    @Test
    void strongholdsMatchVanillaRingLayout() {
        List<SeedStructureScanner.Found> all = SeedStructureScanner.strongholds(SEED, () -> false);
        assertEquals(128, all.size(), "vanilla generates 128 strongholds");
        assertEquals(128, new HashSet<>(all.stream().map(f -> f.x() + "," + f.z()).toList()).size());

        List<Double> d = new ArrayList<>();
        for (SeedStructureScanner.Found f : all) d.add(Math.sqrt(f.distSq(0, 0)));
        d.sort(Double::compare);
        // Ring 0 holds 3 strongholds at 128±40 chunks (≈1408–2688 blocks); allow for box-centre offset.
        for (int i = 0; i < 3; i++) {
            assertTrue(d.get(i) > 1250 && d.get(i) < 2850, "ring-0 stronghold at " + d.get(i));
        }
        assertTrue(d.get(3) > 3500, "4th stronghold belongs to ring 1: " + d.get(3));
    }

    @Test
    void netherScanFindsOnlyNetherStructures() {
        SeedStructureScanner.Result r = SeedStructureScanner.scan(SEED, Level.NETHER, 0, 0, 1500, null, () -> false);
        assertNull(r.error, r.error);
        assertEquals(0, r.failures, "nether generation errors, first: " + r.firstFailure);
        assertFalse(r.found.isEmpty(), "fortresses/bastions expected within 1500 nether blocks");
        for (SeedStructureScanner.Found f : r.found) {
            String id = f.id();
            assertTrue(id.equals("fortress") || id.equals("bastion_remnant") || id.equals("nether_fossil")
                || id.equals("ruined_portal_nether"), "unexpected nether structure " + id);
        }
    }

    @Test
    void everyVanillaStructureHasAStyleAndAnExistingIcon() {
        DihWorldgenContext ctx = SeedStructureScanner.context(SEED, Level.OVERWORLD);
        List<String> missing = new ArrayList<>();
        ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(h -> {
            String id = SeedStructureScanner.idOf(h);
            StructureStyle s = StructureStyle.of(id);
            if (s.rgb == 0xFF00FF) missing.add(id + " (no style)");
            if (SeedStructureScannerTest.class.getResource("/assets/minecraft/" + s.icon) == null) {
                missing.add(id + " (icon " + s.icon + " not in the game assets)");
            }
        });
        assertTrue(missing.isEmpty(), "unstyled structures: " + missing);
    }

    @Test
    void randomSpreadCandidatesOutnumberValidated() {
        // Sanity: the old grid-only approach (no frequency/biome) would draw far more.
        DihWorldgenContext ctx = SeedStructureScanner.context(SEED, Level.OVERWORLD);
        int grid = 0;
        for (Holder<StructureSet> set : ctx.structureState().possibleStructureSets()) {
            if (set.value().placement() instanceof RandomSpreadStructurePlacement rsp) {
                int regions = (RADIUS >> 4) * 2 / rsp.spacing() + 1;
                grid += regions * regions;
            }
        }
        SeedStructureScanner.Result r = SeedStructureScanner.scan(SEED, Level.OVERWORLD, 0, 0, RADIUS, null, () -> false);
        assertNotNull(r);
        assertNotEquals(0, grid);
        assertTrue(r.found.size() < grid / 5, "validated " + r.found.size() + " vs raw grid " + grid);
    }
}
