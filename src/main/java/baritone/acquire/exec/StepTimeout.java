package baritone.acquire.exec;

import baritone.acquire.model.Location;
import baritone.acquire.model.Step;

/** A generous step limit in ticks. Distance and vertical travel are more expensive than the direct line suggests. */
final class StepTimeout {
    private StepTimeout() {}

    static int ticks(Step step, double distance, double vertical, int floorSeconds) {
        double blocks = Double.isFinite(distance) ? Math.max(0, distance) : 64;
        double depth = Double.isFinite(vertical) ? Math.max(0, vertical) : 0;
        double work = switch (step) {
            // Obsidian nobody has seen is cast: a lava trip, two pours and a slow break per block.
            case Step.Mine mine -> mine.expectedBlocks() * (mine.item().equals("minecraft:obsidian") ? 1200.0 : 80.0);
            // Endermen spawn thinly anywhere: each is looked for first.
            case Step.Kill kill -> kill.expectedKills() * (KillRunner.roams(kill.entity()) ? 600.0 : 120.0);
            // A piglin looks each ingot over for six seconds; finding them comes on top.
            case Step.Barter barter -> barter.trades() * 80.0 + 1200.0;
            case Step.Smelt smelt -> smelt.times() * Math.max(1, smelt.recipe().cookTicks()) + 1200.0;
            case Step.Craft craft -> craft.times() * 60.0;
            case Step.PlaceStation place -> 600.0;
            case Step.RetrieveStation retrieve -> 900.0;
            // A portal cast on the way takes a lava trip for each of its ten frame blocks, as cast obsidian does.
            case Step.Travel travel -> 6000.0 + (casts(travel) ? 10 * 1200.0 : 0);
            case Step.Locate locate -> 12000.0;
            case Step.SlayDragon dragon -> 12000.0;
            case Step.CollectEgg egg -> 1200.0;
        };
        double scaled = 20.0 * (45 + blocks * 3 + depth * 6) + work;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(Math.max(1, floorSeconds) * 20.0, scaled));
    }

    /** Whether {@code travel} casts a nether portal from lava first: its kit is used up, and has no obsidian. */
    static boolean casts(Step.Travel travel) {
        return travel.to() == Location.NETHER && !travel.consumes().isEmpty() && !travel.consumes().containsKey("minecraft:obsidian");
    }
}
