package dihclient.schematic;

import baritone.utils.schematic.LoadedSchematic;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SchematicPlacementTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // ---- transform ----

    @Test
    void aQuarterTurnIsClockwiseSeenFromAbove() {
        SchematicTransform turn = SchematicTransform.IDENTITY.rotated(1);
        assertEquals(90, turn.rotation());
        assertEquals(new BlockPos(0, 0, 1), turn.offset(1, 0, 0));   // east becomes south
        assertEquals(new BlockPos(-1, 0, 0), turn.offset(0, 0, 1));  // south becomes west
        assertEquals(new BlockPos(0, 5, 0), turn.offset(0, 5, 0));
    }

    @Test
    void mirrorsFlipOneAxisAcrossTheOrigin() {
        SchematicTransform x = SchematicTransform.IDENTITY.withMirror(SchematicTransform.Mirroring.X);
        SchematicTransform z = SchematicTransform.IDENTITY.withMirror(SchematicTransform.Mirroring.Z);
        assertEquals(new BlockPos(-3, 1, 2), x.offset(3, 1, 2));
        assertEquals(new BlockPos(3, 1, -2), z.offset(3, 1, 2));
    }

    @Test
    void localUndoesOffsetForEveryTransform() {
        for (SchematicTransform.Mirroring mirror : SchematicTransform.Mirroring.values()) {
            for (int turns = 0; turns < 4; turns++) {
                SchematicTransform t = new SchematicTransform(turns * 90, mirror);
                for (int[] p : new int[][]{{0, 0, 0}, {3, 1, 2}, {-4, 7, 9}, {5, 0, -6}}) {
                    BlockPos o = t.offset(p[0], p[1], p[2]);
                    assertEquals(new BlockPos(p[0], p[1], p[2]), t.local(o.getX(), o.getY(), o.getZ()), t + " " + o);
                }
            }
        }
    }

    @Test
    void boundsCoverExactlyTheTransformedCells() {
        Vec3i size = new Vec3i(3, 2, 5);
        for (SchematicTransform.Mirroring mirror : SchematicTransform.Mirroring.values()) {
            for (int turns = 0; turns < 4; turns++) {
                SchematicTransform t = new SchematicTransform(turns * 90, mirror);
                BlockPos min = t.minCorner(size);
                Vec3i s = t.size(size);
                for (int x = 0; x < 3; x++) {
                    for (int y = 0; y < 2; y++) {
                        for (int z = 0; z < 5; z++) {
                            BlockPos o = t.offset(x, y, z);
                            assertTrue(o.getX() >= min.getX() && o.getX() < min.getX() + s.getX()
                                && o.getY() >= min.getY() && o.getY() < min.getY() + s.getY()
                                && o.getZ() >= min.getZ() && o.getZ() < min.getZ() + s.getZ(), t + " " + o);
                        }
                    }
                }
                assertEquals(30, s.getX() * s.getY() * s.getZ());
            }
        }
    }

    @Test
    void statesMirrorThenRotate() {
        BlockState east = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        assertEquals(Direction.SOUTH, SchematicTransform.IDENTITY.rotated(1).apply(east).getValue(BlockStateProperties.HORIZONTAL_FACING));
        SchematicTransform mirrored = SchematicTransform.IDENTITY.withMirror(SchematicTransform.Mirroring.X);
        assertEquals(Direction.WEST, mirrored.apply(east).getValue(BlockStateProperties.HORIZONTAL_FACING));
        // mirrored to west first, then a clockwise turn: north
        assertEquals(Direction.NORTH, mirrored.rotated(1).apply(east).getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    @Test
    void rotationsWrapAndRound() {
        assertEquals(270, SchematicTransform.IDENTITY.rotated(-1).rotation());
        assertEquals(0, SchematicTransform.IDENTITY.rotated(4).rotation());
        assertEquals(90, new SchematicTransform(450, null).rotation());
        assertEquals(90, new SchematicTransform(100, SchematicTransform.Mirroring.NONE).rotation());
        assertEquals(SchematicTransform.Mirroring.NONE, new SchematicTransform(0, null).mirror());
        assertEquals(SchematicTransform.Mirroring.X, SchematicTransform.Mirroring.NONE.next());
        assertEquals(SchematicTransform.Mirroring.Z, SchematicTransform.Mirroring.X.next());
        assertEquals(SchematicTransform.Mirroring.NONE, SchematicTransform.Mirroring.Z.next());
        assertEquals(SchematicTransform.Mirroring.Z, SchematicTransform.Mirroring.parse("LEFT_RIGHT"));
        assertEquals(SchematicTransform.Mirroring.NONE, SchematicTransform.Mirroring.parse("sideways"));
    }

    // ---- placement ----

    /**
     * 3 x 2 x 2: stone floor except one glass, a stair facing east above (0,0,0), air elsewhere; (2,1,1) is void.
     */
    private static final class Fixture extends LoadedSchematic {
        Fixture() {
            allocate(3, 2, 2, true);
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 2; z++) {
                    setBlock(x, 0, z, Blocks.STONE.defaultBlockState());
                    if (!(x == 2 && z == 1)) {
                        setBlock(x, 1, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            setBlock(1, 0, 1, Blocks.GLASS.defaultBlockState());
            setBlock(0, 1, 0, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            this.name = "Fixture";
        }
    }

    private static SchematicPlacement placed(BlockPos origin, SchematicTransform transform) {
        SchematicPlacement p = new SchematicPlacement("p1", "fixture.litematic", "", "minecraft:overworld", origin, transform, true);
        p.attach(new Fixture());
        return p;
    }

    @Test
    void anUnturnedPlacementPutsTheSchematicAtItsOrigin() {
        SchematicPlacement p = placed(new BlockPos(100, 64, -20), SchematicTransform.IDENTITY);
        assertEquals("Fixture", p.name());
        assertSame(Blocks.STONE, p.expected(100, 64, -20).getBlock());
        assertSame(Blocks.GLASS, p.expected(101, 64, -19).getBlock());
        assertSame(Blocks.OAK_STAIRS, p.expected(100, 65, -20).getBlock());
        assertTrue(p.expected(101, 65, -20).isAir());
        assertNull(p.expected(102, 65, -19), "void cell");
        assertNull(p.expected(99, 64, -20), "outside");
        assertEquals(new BoundingBox(100, 64, -20, 102, 65, -19), p.bounds());
    }

    @Test
    void aTurnedPlacementTurnsItsBlocksAndTheirStates() {
        SchematicPlacement p = placed(BlockPos.ZERO, SchematicTransform.IDENTITY.rotated(1));
        // local (1,0,1) glass lands at offset(1,0,1) = (-1,0,1)
        assertSame(Blocks.GLASS, p.expected(-1, 0, 1).getBlock());
        BlockState stair = p.expected(0, 1, 0);
        assertEquals(Direction.SOUTH, stair.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertEquals(new Vec3i(2, 2, 3), p.size());
        assertEquals(new BlockPos(-1, 0, 0), p.minCorner());
        int revision = p.revision();
        p.nudge(1, 0, 0);
        assertTrue(p.revision() > revision);
        assertSame(Blocks.GLASS, p.expected(0, 0, 1).getBlock());
    }

    @Test
    void theBuildSchematicIsTheTurnedBoxFromItsLowestCorner() {
        SchematicPlacement p = placed(new BlockPos(10, 5, 10), SchematicTransform.IDENTITY.rotated(2));
        PlacementBuildSchematic build = new PlacementBuildSchematic(p);
        assertEquals(p.minCorner(), build.origin());
        assertEquals(3, build.widthX());
        assertEquals(2, build.heightY());
        assertEquals(2, build.lengthZ());
        int inside = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos world = build.origin().offset(x, y, z);
                    BlockState want = p.expected(world);
                    assertEquals(want != null, build.inSchematic(x, y, z, null), "at " + world);
                    if (want != null) {
                        inside++;
                        assertSame(want, build.desiredState(x, y, z, null, List.of()));
                    }
                }
            }
        }
        assertEquals(11, inside);
    }

    // ---- verifier ----

    @Test
    void mismatchesAreClassifiedTheWayLitematicaDoes() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState east = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState west = east.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        assertEquals(SchematicVerifier.Mismatch.NONE, SchematicVerifier.classify(stone, stone));
        assertEquals(SchematicVerifier.Mismatch.NONE, SchematicVerifier.classify(air, Blocks.CAVE_AIR.defaultBlockState()));
        assertEquals(SchematicVerifier.Mismatch.NONE, SchematicVerifier.classify(null, stone));
        assertEquals(SchematicVerifier.Mismatch.MISSING, SchematicVerifier.classify(stone, air));
        assertEquals(SchematicVerifier.Mismatch.EXTRA, SchematicVerifier.classify(air, stone));
        assertEquals(SchematicVerifier.Mismatch.WRONG_BLOCK, SchematicVerifier.classify(stone, Blocks.DIRT.defaultBlockState()));
        assertEquals(SchematicVerifier.Mismatch.WRONG_STATE, SchematicVerifier.classify(east, west));
        assertTrue(SchematicVerifier.Mismatch.WRONG_BLOCK.priority() > SchematicVerifier.Mismatch.WRONG_STATE.priority());
        assertTrue(SchematicVerifier.Mismatch.WRONG_STATE.priority() > SchematicVerifier.Mismatch.EXTRA.priority());
        assertTrue(SchematicVerifier.Mismatch.EXTRA.priority() > SchematicVerifier.Mismatch.MISSING.priority());
    }

    @Test
    void verifyCountsEveryPositionThePlacementWants() {
        SchematicPlacement p = placed(BlockPos.ZERO, SchematicTransform.IDENTITY);
        FakeWorld world = new FakeWorld();
        world.put(0, 0, 0, Blocks.STONE.defaultBlockState());          // right
        world.put(1, 0, 0, Blocks.DIRT.defaultBlockState());           // wrong block
        world.put(1, 0, 1, Blocks.GLASS.defaultBlockState());          // right
        world.put(0, 1, 0, Blocks.OAK_STAIRS.defaultBlockState());     // wrong state (faces north)
        world.put(1, 1, 1, Blocks.COBBLESTONE.defaultBlockState());    // extra
        world.put(2, 1, 1, Blocks.COBBLESTONE.defaultBlockState());    // void: not counted
        SchematicVerifier.Counts counts = SchematicVerifier.verify(p, world);
        assertEquals(11, counts.total());
        assertEquals(1, counts.of(SchematicVerifier.Mismatch.WRONG_BLOCK));
        assertEquals(1, counts.of(SchematicVerifier.Mismatch.WRONG_STATE));
        assertEquals(1, counts.of(SchematicVerifier.Mismatch.EXTRA));
        assertEquals(3, counts.of(SchematicVerifier.Mismatch.MISSING));   // the other floor blocks
        assertEquals(5, counts.correct());                                 // two floor blocks, three air
        assertFalse(counts.complete());
    }

    // ---- materials ----

    @Test
    void materialsAreWhatIsNotRightYetLessWhatIsHeld() {
        SchematicPlacement p = placed(BlockPos.ZERO, SchematicTransform.IDENTITY);
        FakeWorld world = new FakeWorld();
        world.put(0, 0, 0, Blocks.STONE.defaultBlockState());
        world.put(1, 0, 0, Blocks.DIRT.defaultBlockState());
        Map<String, Integer> held = Map.of("minecraft:stone", 2, "minecraft:oak_stairs", 5);
        SchematicMaterials.Report report = SchematicMaterials.report(p, world, id -> held.getOrDefault(id, 0));
        // stone at (1,0,0) (dirt there), (2,0,0), (0,0,1), (2,0,1); glass (1,0,1); the stair
        assertEquals(6, report.remaining());
        SchematicMaterials.Line stone = report.lines().stream().filter(l -> l.item().equals("minecraft:stone")).findFirst().orElseThrow();
        assertEquals(4, stone.needed());
        assertEquals(2, stone.missing());
        SchematicMaterials.Line glass = report.lines().stream().filter(l -> l.item().equals("minecraft:glass")).findFirst().orElseThrow();
        assertEquals(1, glass.missing());
        assertEquals(0, report.lines().stream().filter(l -> l.item().equals("minecraft:oak_stairs")).findFirst().orElseThrow().missing());
        assertEquals("minecraft:stone", report.lines().getFirst().item(), "biggest shortfall first");
        assertEquals(3, report.missingTotal());

        SchematicMaterials.Report whole = SchematicMaterials.report(p, null, id -> 0);
        assertEquals(7, whole.remaining());
    }

    // ---- store ----

    @Test
    void placementsRoundTripPerScope() {
        SchematicPlacement a = new SchematicPlacement("a", "house.litematic", "House", "minecraft:overworld",
            new BlockPos(10, 64, -3), new SchematicTransform(90, SchematicTransform.Mirroring.Z), true);
        SchematicPlacement b = new SchematicPlacement("b", "sub/tower.schem", "Tower", "minecraft:the_nether",
            new BlockPos(-5, 30, 7), SchematicTransform.IDENTITY, false);
        Map<String, SchematicPlacementStore.Scope> scopes = new HashMap<>();
        scopes.put("play.example.net", new SchematicPlacementStore.Scope("b", List.of(a, b)));
        scopes.put("My World", new SchematicPlacementStore.Scope("", List.of()));

        Map<String, SchematicPlacementStore.Scope> back = SchematicPlacementStore.read(SchematicPlacementStore.write(scopes));
        assertEquals(Set.of("play.example.net"), back.keySet(), "empty scopes are not written");
        SchematicPlacementStore.Scope scope = back.get("play.example.net");
        assertEquals("b", scope.selected());
        assertEquals(2, scope.placements().size());
        SchematicPlacement a2 = scope.placements().get(0);
        assertEquals("a", a2.id());
        assertEquals("house.litematic", a2.file());
        assertEquals("House", a2.name());
        assertEquals("minecraft:overworld", a2.dimension());
        assertEquals(new BlockPos(10, 64, -3), a2.origin());
        assertEquals(new SchematicTransform(90, SchematicTransform.Mirroring.Z), a2.transform());
        assertTrue(a2.visible());
        assertFalse(scope.placements().get(1).visible());
        assertEquals("sub/tower.schem", scope.placements().get(1).file());
    }

    /**
     * The first format written, kept as written, so a future change cannot quietly stop reading it.
     */
    private static final String VERSION_1_FILE = """
        {
          "version": 1,
          "scopes": {
            "mc.example.org": {
              "selected": "1f0c",
              "placements": [
                {"id": "1f0c", "file": "base.litematic", "name": "Base", "dimension": "minecraft:overworld",
                 "x": 1, "y": 70, "z": 2, "rotation": 270, "mirror": "x", "visible": true}
              ]
            }
          }
        }""";

    @Test
    void theFirstFormatStillLoads() {
        SchematicPlacementStore.Scope scope = SchematicPlacementStore.read(VERSION_1_FILE).get("mc.example.org");
        SchematicPlacement p = scope.placements().getFirst();
        assertEquals("1f0c", scope.selected());
        assertEquals(new BlockPos(1, 70, 2), p.origin());
        assertEquals(new SchematicTransform(270, SchematicTransform.Mirroring.X), p.transform());
    }

    @Test
    void damagedOrPartialFilesLoadWhatTheyCan() {
        assertTrue(SchematicPlacementStore.read("").isEmpty());
        assertTrue(SchematicPlacementStore.read("not json {").isEmpty());
        assertTrue(SchematicPlacementStore.read("[1, 2]").isEmpty());
        assertTrue(SchematicPlacementStore.read("{\"scopes\": 5}").isEmpty());
        String partial = """
            {"scopes": {"s": {"placements": [
              {"file": "a.schem"},
              {"name": "no file, skipped"},
              7,
              {"file": "b.schematic", "x": "12", "rotation": "abc", "mirror": 3, "visible": "false"}
            ]}, "bad": 1}}""";
        SchematicPlacementStore.Scope s = SchematicPlacementStore.read(partial).get("s");
        assertEquals(2, s.placements().size());
        SchematicPlacement a = s.placements().get(0);
        assertEquals(BlockPos.ZERO, a.origin());
        assertEquals(SchematicTransform.IDENTITY, a.transform());
        assertTrue(a.visible());
        assertFalse(a.id().isBlank(), "a missing id gets a new one");
        SchematicPlacement b = s.placements().get(1);
        assertEquals(12, b.origin().getX());
        assertFalse(b.visible());
        assertEquals("", s.selected());
    }

    /**
     * A world of a few blocks; air elsewhere.
     */
    static final class FakeWorld implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void put(int x, int y, int z, BlockState state) {
            this.blocks.put(new BlockPos(x, y, z), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }
}
