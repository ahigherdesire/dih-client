package baritone.acquire.model;

/**
 * What a plan is for: some of an item (what {@code #acquire} has always planned), the ender dragon dead, or being
 * somewhere ({@code #beat} phases like "reach the Nether").
 */
public sealed interface Goal permits Goal.ItemGoal, Goal.DragonDead, Goal.AtLocation {

    /** The plan's goal string: the item id, or a name for the other goals. */
    String label();

    /** How many of {@link #label()} the plan is for (1 for the goals that aren't items). */
    int count();

    record ItemGoal(String item, int count) implements Goal {
        @Override
        public String label() {
            return item;
        }
    }

    record DragonDead() implements Goal {
        public static final String LABEL = "dragon_dead";

        @Override
        public String label() {
            return LABEL;
        }

        @Override
        public int count() {
            return 1;
        }
    }

    record AtLocation(Location location) implements Goal {
        @Override
        public String label() {
            return "at_" + location.id();
        }

        @Override
        public int count() {
            return 1;
        }
    }
}
