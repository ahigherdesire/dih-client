package baritone.acquire.exec;

import baritone.acquire.exec.Meal.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The eat-state machine: every way out of a meal lets go of "use". */
final class MealTest {

    private static final String BEEF = "minecraft:cooked_beef";

    /** tick(dead, screenOpen, count, using) */
    private static Action eat(Meal meal, int count, boolean using) {
        return meal.tick(false, false, count, using);
    }

    @Test
    void holdsUntilTheCountDrops() {
        Meal meal = new Meal(BEEF, 32);
        assertEquals(Action.START, eat(meal, 5, false));
        for (int i = 0; i < 32; i++) assertEquals(Action.HOLD, eat(meal, 5, true));
        assertEquals(Action.RELEASE, eat(meal, 4, false));
        assertTrue(meal.finished());
        assertTrue(meal.ate());
        assertNull(meal.failure());
        assertEquals(Action.RELEASE, eat(meal, 4, false), "a finished meal only ever releases");
    }

    @Test
    void timesOutAndReleases() {
        Meal meal = new Meal(BEEF, 32);
        eat(meal, 5, false);
        Action last = null;
        for (int i = 0; i < 32 + Meal.TIMEOUT_MARGIN + 1; i++) last = eat(meal, 5, true);
        assertEquals(Action.RELEASE, last);
        assertFalse(meal.ate());
        assertEquals("timed out", meal.failure());
    }

    @Test
    void notUsingForAWhileMeansItCouldNotEat() {
        Meal meal = new Meal(BEEF, 32);
        eat(meal, 5, false);
        Action last = null;
        for (int i = 0; i <= Meal.IDLE_TICKS; i++) last = eat(meal, 5, false);
        assertEquals(Action.RELEASE, last);
        assertEquals("couldn't eat it", meal.failure());
    }

    @Test
    void aShortGapWhileTheCountSyncsIsFine() {
        Meal meal = new Meal(BEEF, 32);
        eat(meal, 5, false);
        eat(meal, 5, true);
        for (int i = 0; i < 3; i++) {
            assertEquals(Action.WAIT, eat(meal, 5, false), "server finished, slot update in flight: let go, don't fail");
        }
        assertEquals(Action.RELEASE, eat(meal, 4, false));
        assertTrue(meal.ate());
    }

    @Test
    void deathReleases() {
        Meal meal = new Meal(BEEF, 32);
        eat(meal, 5, false);
        assertEquals(Action.RELEASE, meal.tick(true, false, 5, true));
        assertEquals("you died", meal.failure());
    }

    @Test
    void cancelReleasesAndKeepsTheFirstReason() {
        Meal meal = new Meal(BEEF, 32);
        eat(meal, 5, false);
        assertEquals(Action.RELEASE, meal.cancel("stopped"));
        assertEquals(Action.RELEASE, meal.cancel("again"));
        assertEquals("stopped", meal.failure());
        assertEquals(Action.RELEASE, eat(meal, 5, true));
    }

    @Test
    void aScreenPausesTheMealAndItStartsOverOnceClosed() {
        Meal meal = new Meal(BEEF, 32);
        assertEquals(Action.WAIT, meal.tick(false, true, 5, false), "never starts with a screen open");
        assertEquals(Action.START, eat(meal, 5, false));
        assertEquals(Action.HOLD, eat(meal, 5, true));
        assertEquals(Action.RELEASE, meal.tick(false, true, 5, true), "a screen opened mid-meal: let go");
        assertEquals(Action.WAIT, meal.tick(false, true, 5, false));
        assertEquals(Action.START, eat(meal, 5, false));
        assertFalse(meal.finished());
    }

    @Test
    void aScreenThatStaysOpenEndsTheMeal() {
        Meal meal = new Meal(BEEF, 32);
        Action last = null;
        for (int i = 0; i <= Meal.SCREEN_WAIT_TICKS; i++) last = meal.tick(false, true, 5, false);
        assertEquals(Action.RELEASE, last);
        assertEquals("a screen stayed open", meal.failure());
    }

    @Test
    void nothingLeftFailsAtTheStart() {
        Meal meal = new Meal(BEEF, 32);
        assertEquals(Action.RELEASE, eat(meal, 0, false));
        assertEquals("no cooked_beef left", meal.failure());
    }
}
