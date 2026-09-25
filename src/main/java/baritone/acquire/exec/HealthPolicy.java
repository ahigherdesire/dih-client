package baritone.acquire.exec;

/**
 * When {@code #acquire} (and {@code #eat}) should eat, and when it should go and get food. Pure, so the
 * thresholds are unit-tested; health is in half-hearts (20 = full), food in food points (20 = full).
 */
final class HealthPolicy {

    /** At or below this much food: eat even at full health (sprinting stops at 6). */
    static final int HUNGRY_FOOD = 6;
    /** Natural regeneration needs this much food. */
    static final int REGEN_FOOD = 18;
    static final int MAX_FOOD = 20;
    /** A food detour gets at least this many food points' worth, however full the bar is. */
    static final int MIN_DETOUR_POINTS = 10;

    enum Need {
        /** Nothing to eat for. */
        NONE,
        /** Hurt and too hungry to regenerate, or hungry: eat ordinary food. */
        EAT,
        /** Health at the emergency line: a golden apple first, and food the plan needs is fair game. */
        EMERGENCY
    }

    private HealthPolicy() {
    }

    /**
     * What eating would do for the player now. Absorption hearts count towards the emergency line, so
     * a golden apple already eaten does not trigger another.
     */
    static Need need(float health, float absorption, float maxHealth, int food, int emergencyHealth) {
        if (health + absorption <= emergencyHealth) return Need.EMERGENCY;
        if (food <= HUNGRY_FOOD) return Need.EAT;
        if (health < maxHealth && food < REGEN_FOOD) return Need.EAT;
        return Need.NONE;
    }

    /** With nothing safe to eat: whether to fetch food before carrying on (health at the line, or hungry). */
    static boolean wantsFood(float health, int food, int healHealth, boolean hasSafeFood) {
        return !hasSafeFood && (health <= healHealth || food <= HUNGRY_FOOD);
    }

    /** Food points a detour fetches: what the bar is missing, at least {@link #MIN_DETOUR_POINTS}. */
    static int detourPoints(int food) {
        return Math.max(MIN_DETOUR_POINTS, MAX_FOOD - Math.max(0, Math.min(MAX_FOOD, food)));
    }

    /** "3.5 hearts" for chat. */
    static String hearts(float health) {
        float hearts = Math.max(0, Math.round(health)) / 2f;
        String n = hearts == (int) hearts ? Integer.toString((int) hearts) : Float.toString(hearts);
        return n + (hearts == 1 ? " heart" : " hearts");
    }
}
