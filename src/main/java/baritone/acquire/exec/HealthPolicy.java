package baritone.acquire.exec;

/**
 * When to eat and when to go and get food. Pure (health and food as plain numbers) so it is unit-tested.
 *
 * <p>Healing in vanilla: natural regeneration only runs while the food level is at least
 * {@link #REGEN_FOOD}, so "heal" mostly means keeping food up while hurt. Golden apples heal directly
 * (Regeneration and Absorption) and can be eaten at full food; they are kept for emergencies.
 * Health is in half-hearts (20 = full), food in food points (20 = full).
 */
public final class HealthPolicy {

    public static final int MAX_FOOD = 20;
    /** Natural regeneration needs at least this much food. */
    public static final int REGEN_FOOD = 18;
    /** At or below this, eat whatever the health. At 6 and below you can't sprint either. */
    public static final int HUNGRY_FOOD = 6;
    /** At or below this, harmful food (rotten flesh, ...) is allowed when nothing else is held. */
    public static final int STARVING_FOOD = 3;

    public enum Need {
        /** Nothing to do. */
        NONE,
        /** Eat a normal meal. */
        EAT,
        /** Health at or below the emergency threshold: a golden apple even at full food, any food otherwise. */
        EMERGENCY
    }

    private HealthPolicy() {
    }

    /**
     * Whether to eat now.
     * <ul>
     *   <li>{@link Need#EMERGENCY} when hurt and health plus absorption is at or below {@code emergencyHealth}
     *       (the absorption a golden apple just gave counts, so a second one isn't eaten straight after);</li>
     *   <li>{@link Need#EAT} when food is at or below {@link #HUNGRY_FOOD}, or when hurt with food below
     *       {@link #REGEN_FOOD} (no regeneration until it is topped up);</li>
     *   <li>{@link Need#NONE} otherwise.</li>
     * </ul>
     * A dead player (health 0) needs nothing.
     */
    public static Need need(float health, float absorption, float maxHealth, int food, int emergencyHealth) {
        if (health <= 0) return Need.NONE;
        boolean hurt = health < maxHealth;
        if (hurt && health + Math.max(0, absorption) <= emergencyHealth) return Need.EMERGENCY;
        if (food <= HUNGRY_FOOD) return Need.EAT;
        if (hurt && food < REGEN_FOOD) return Need.EAT;
        return Need.NONE;
    }

    /**
     * Whether a food detour is wanted: health at or below {@code healHealth} or food at or below
     * {@link #HUNGRY_FOOD}, with no safe food held.
     */
    public static boolean wantsFood(float health, int food, int healHealth, boolean safeFoodHeld) {
        if (safeFoodHeld || health <= 0) return false;
        return health <= healHealth || food <= HUNGRY_FOOD;
    }

    /** Food points worth getting on a detour: the missing points plus a spare meal, between 10 and 20. */
    public static int detourPoints(int food) {
        return Math.max(10, Math.min(MAX_FOOD, MAX_FOOD - food + 8));
    }

    /** "6 hearts" or "4.5 hearts". */
    public static String hearts(float health) {
        float h = Math.max(0, Math.round(health)) / 2.0F;
        return (h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h)) + (h == 1 ? " heart" : " hearts");
    }
}
