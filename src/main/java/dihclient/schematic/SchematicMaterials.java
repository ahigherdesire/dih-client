package dihclient.schematic;

import baritone.construct.BillOfMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The blocks a placement still needs, against what the player holds. Every position that is not right yet (missing,
 * a wrong block or a wrong state) costs its item, as Autism's placement material estimate counts it; item costs
 * (double slabs, candles, door and bed halves) come from {@link BillOfMaterials}.
 */
public final class SchematicMaterials {

    /**
     * One item: how many the placement still needs and how many are on hand.
     */
    public record Line(String item, int needed, int have) {
        public int missing() {
            return Math.max(0, this.needed - this.have);
        }
    }

    /**
     * @param lines        every item still needed, the biggest shortfalls first
     * @param unplaceable  blocks no item places (water, fire, portals), by block id
     * @param remaining    positions not right yet
     */
    public record Report(List<Line> lines, Map<String, Integer> unplaceable, int remaining) {
        public int missingTotal() {
            return this.lines.stream().mapToInt(Line::missing).sum();
        }

        public boolean ready() {
            return missingTotal() == 0;
        }
    }

    private SchematicMaterials() {}

    /**
     * @param world the world to compare with, or null for everything the placement holds
     * @param held  how many of an item id the player has
     */
    public static Report report(SchematicPlacement placement, BlockGetter world, ToIntFunction<String> held) {
        List<BlockState> toPlace = new ArrayList<>();
        BoundingBox box = placement.bounds();
        if (box != null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        BlockState want = placement.expected(x, y, z);
                        if (want == null || want.isAir()) {
                            continue;
                        }
                        if (world != null && SchematicVerifier.classify(want, world.getBlockState(pos.set(x, y, z)))
                            == SchematicVerifier.Mismatch.NONE) {
                            continue;
                        }
                        toPlace.add(want);
                    }
                }
            }
        }
        BillOfMaterials.Tally tally = BillOfMaterials.tally(toPlace);
        List<Line> lines = new ArrayList<>();
        tally.items().forEach((item, count) -> lines.add(new Line(item, count, Math.max(0, held.applyAsInt(item)))));
        lines.sort(Comparator.comparingInt(Line::missing).reversed()
            .thenComparing(Comparator.comparingInt(Line::needed).reversed())
            .thenComparing(Line::item));
        return new Report(List.copyOf(lines), tally.unplaceable(), toPlace.size());
    }
}
