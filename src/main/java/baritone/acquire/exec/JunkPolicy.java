package baritone.acquire.exec;

import baritone.acquire.model.Step;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What may be thrown away when the inventory fills up mid-plan. Only used when the user opted in
 * ({@code acquireDropJunk}); only common filler blocks and trash drops qualify, and never an item the
 * rest of the plan still uses. Pure, so it is unit-tested.
 */
final class JunkPolicy {

    /** Filler that mining picks up in bulk and trash mob drops. */
    static final Set<String> JUNK = Set.of(
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:gravel",
            "minecraft:sand", "minecraft:red_sand", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
            "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff",
            "minecraft:calcite", "minecraft:netherrack", "minecraft:dripstone_block",
            "minecraft:rotten_flesh", "minecraft:poisonous_potato");

    private JunkPolicy() {
    }

    /** Every item id the goal and the steps from {@code from} on still produce, consume or place. */
    static Set<String> neededItems(String goal, List<Step> steps, int from) {
        Set<String> needed = new HashSet<>();
        needed.add(goal);
        for (int i = Math.max(0, from); i < steps.size(); i++) {
            Step step = steps.get(i);
            needed.add(step.item());
            switch (step) {
                case Step.Mine mine -> needed.addAll(mine.blocks());
                case Step.Craft craft -> {
                    needed.addAll(craft.inputs());
                    craft.recipe().ingredients().forEach(ing -> needed.addAll(ing.anyOf()));
                }
                case Step.Smelt smelt -> {
                    needed.add(smelt.input());
                    needed.add(smelt.fuel());
                }
                case Step.Kill kill -> { }
                case Step.PlaceStation station -> needed.add(station.station());
            }
        }
        return needed;
    }

    /**
     * How many of each item the steps from {@code from} on still use up (craft inputs, smelt inputs and fuel, a station
     * to place), plus {@code goalCount} of the goal: held up to these counts, an item is the plan's, not scaffolding.
     */
    static Map<String, Integer> reservedCounts(String goal, int goalCount, List<Step> steps, int from) {
        Map<String, Integer> reserved = new HashMap<>();
        reserved.merge(goal, goalCount, Integer::sum);
        for (int i = Math.max(0, from); i < steps.size(); i++) {
            switch (steps.get(i)) {
                case Step.Craft craft -> {
                    for (int j = 0; j < craft.inputs().size() && j < craft.recipe().ingredients().size(); j++) {
                        reserved.merge(craft.inputs().get(j), craft.recipe().ingredients().get(j).count() * craft.times(),
                                Integer::sum);
                    }
                }
                case Step.Smelt smelt -> {
                    reserved.merge(smelt.input(), smelt.times(), Integer::sum);
                    reserved.merge(smelt.fuel(), smelt.fuelCount(), Integer::sum);
                }
                case Step.PlaceStation station -> reserved.merge(station.station(), 1, Integer::sum);
                case Step.Mine mine -> { }
                case Step.Kill kill -> { }
            }
        }
        return reserved;
    }

    static boolean isJunk(String item, Set<String> needed) {
        return JUNK.contains(item) && !needed.contains(item);
    }

    /** Index of the biggest junk stack in {@code ids}/{@code counts} (parallel lists, null for empty), or -1. */
    static int pickStack(List<String> ids, List<Integer> counts, Set<String> needed) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || !isJunk(id, needed)) continue;
            int n = counts.get(i);
            if (n > bestCount) {
                best = i;
                bestCount = n;
            }
        }
        return best;
    }
}
