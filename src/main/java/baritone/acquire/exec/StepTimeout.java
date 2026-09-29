package baritone.acquire.exec;

import baritone.acquire.model.Step;

/** A generous step limit in ticks. Distance and vertical travel are more expensive than the direct line suggests. */
final class StepTimeout {
    private StepTimeout() {}

    static int ticks(Step step, double distance, double vertical, int floorSeconds) {
        double blocks = Double.isFinite(distance) ? Math.max(0, distance) : 64;
        double depth = Double.isFinite(vertical) ? Math.max(0, vertical) : 0;
        double work = switch (step) {
            case Step.Mine mine -> mine.expectedBlocks() * 80.0;
            case Step.Kill kill -> kill.expectedKills() * 120.0;
            case Step.Smelt smelt -> smelt.times() * Math.max(1, smelt.recipe().cookTicks()) + 1200.0;
            case Step.Craft craft -> craft.times() * 60.0;
            case Step.PlaceStation place -> 600.0;
            case Step.RetrieveStation retrieve -> 900.0;
            case Step.Travel travel -> 6000.0;
            case Step.Locate locate -> 12000.0;
            case Step.SlayDragon dragon -> 12000.0;
            case Step.CollectEgg egg -> 1200.0;
        };
        double scaled = 20.0 * (45 + blocks * 3 + depth * 6) + work;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(Math.max(1, floorSeconds) * 20.0, scaled));
    }
}
