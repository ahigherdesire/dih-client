package baritone.guardian;

import baritone.guardian.ThreatRanking.Config;
import baritone.guardian.ThreatRanking.Decision;
import baritone.guardian.ThreatRanking.Kind;
import baritone.guardian.ThreatRanking.Mob;
import baritone.guardian.ThreatRanking.Response;
import baritone.guardian.ThreatRanking.Sense;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThreatRankingTest {
    private static final Config CONFIG = Config.DEFAULT;

    /** A calm sense: full health, dry, standing, alone. Tests change one thing at a time. */
    private static final class S {
        boolean lava, fire, burning, water, falling, underwater, canEat, projectile, shield, bucket;
        double fall, stranger = Double.POSITIVE_INFINITY;
        float health = 20;
        int air = 300;
        final List<Mob> mobs = new ArrayList<>();

        Sense build() {
            return new Sense(lava, fire, burning, water, falling, fall, health, air, 300, underwater, canEat, mobs, projectile, stranger,
                    shield, bucket);
        }
    }

    private static Mob zombie(int id, double distance) {
        return new Mob(id, "minecraft:zombie", distance, distance, true, false, false, false);
    }

    private static Mob creeper(int id, double distance, boolean swelling) {
        return new Mob(id, "minecraft:creeper", distance, distance, false, false, true, swelling);
    }

    @Test
    void calmMeansNothingToDo() {
        assertNull(ThreatRanking.decide(new S().build(), CONFIG));
        assertTrue(ThreatRanking.threats(new S().build(), CONFIG).isEmpty());
    }

    @Test
    void everyThreatAtOnceRanksInSurvivalOrder() {
        S s = new S();
        s.lava = true;
        s.falling = true;
        s.fall = 12;
        s.mobs.add(zombie(2, 3));
        s.mobs.add(creeper(1, 3, false));
        s.projectile = true;
        s.underwater = true;
        s.air = 10;
        s.health = 8;
        s.canEat = true;
        s.stranger = 6;
        List<Kind> kinds = ThreatRanking.threats(s.build(), CONFIG).stream().map(ThreatRanking.Threat::kind).toList();
        assertEquals(List.of(Kind.LAVA_OR_FIRE, Kind.FALLING, Kind.CREEPER, Kind.HOSTILE, Kind.PROJECTILE,
                Kind.DROWNING, Kind.LOW_HEALTH, Kind.PLAYER_NEAR), kinds);
    }

    @Test
    void lavaBeatsEverythingElse() {
        S s = new S();
        s.lava = true;
        s.mobs.add(zombie(1, 2));
        assertEquals(Response.ESCAPE_HAZARD, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    @Test
    void burningIsPutOutOnlyWithWaterAtHand() {
        S s = new S();
        s.burning = true;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "nothing to put it out with");
        s.water = true;
        assertEquals(Response.EXTINGUISH, ThreatRanking.decide(s.build(), CONFIG).response());
        s.water = false;
        s.bucket = true;
        assertEquals(Response.EXTINGUISH, ThreatRanking.decide(s.build(), CONFIG).response());
        s.lava = true;
        assertEquals(Response.ESCAPE_HAZARD, ThreatRanking.decide(s.build(), CONFIG).response(), "out of the lava first");
        s.lava = false;
        s.mobs.add(zombie(1, 2));
        assertEquals(Kind.LAVA_OR_FIRE, ThreatRanking.decide(s.build(), CONFIG).threat().kind(), "fire outranks a zombie");
    }

    @Test
    void harmlessFallsAreIgnoredAndBucketlessFallsFallThrough() {
        S s = new S();
        s.falling = true;
        s.fall = 3;
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
        s.fall = 20;
        s.mobs.add(zombie(1, 3));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "no bucket: deal with the zombie");
        s.bucket = true;
        assertEquals(Response.WATER_CLUTCH, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    @Test
    void creeperIsHitUntilItHissesThenAvoided() {
        S s = new S();
        s.mobs.add(creeper(1, 3, false));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.set(0, creeper(1, 3, true));
        assertEquals(Response.BACK_OFF, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.set(0, creeper(1, 6, true));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "a creeper beyond 4 blocks can't hurt yet");
    }

    @Test
    void creeperOutranksACloserZombie() {
        S s = new S();
        s.mobs.add(zombie(1, 1.5));
        s.mobs.add(creeper(2, 3.5, true));
        Decision d = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Kind.CREEPER, d.threat().kind());
        assertEquals(Response.BACK_OFF, d.response());
    }

    @Test
    void nearestHostileFirst() {
        S s = new S();
        s.mobs.add(zombie(1, 7));
        s.mobs.add(zombie(2, 2));
        assertEquals(2, ThreatRanking.decide(s.build(), CONFIG).threat().mobId());
    }

    @Test
    void neutralOrCalmMobsAreLeftAlone() {
        S s = new S();
        s.mobs.add(new Mob(1, "minecraft:enderman", 2, 2, false, true, false, false));
        s.mobs.add(new Mob(2, "minecraft:zombie", 6, 6, false, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "a calm enderman, and a zombie that isn't coming");
        s.mobs.add(new Mob(3, "minecraft:spider", 3, 3, false, false, false, false));
        assertEquals(3, ThreatRanking.decide(s.build(), CONFIG).threat().mobId(), "non-neutral mobs this close count");
    }

    @Test
    void hostilesOutsideTheChaseLimitAreNotChased() {
        S s = new S();
        s.mobs.add(new Mob(1, "minecraft:skeleton", 6, ThreatRanking.CHASE_LIMIT + ThreatRanking.HOSTILE_RADIUS + 1,
                true, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
    }

    @Test
    void fightsAboveTheFleeThresholdAndRetreatsAtIt() {
        S s = new S();
        s.mobs.add(zombie(1, 3));
        s.health = CONFIG.fleeHealth() + 1;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.health = CONFIG.fleeHealth();
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.health = CONFIG.fleeHealth() - 4;
        s.mobs.set(0, creeper(1, 3, false));
        assertEquals(Response.BACK_OFF, ThreatRanking.decide(s.build(), CONFIG).response(), "never trade hits with a creeper when low");
    }

    /** From a real night run: retreating from a skeleton at 3 blocks cost 12 HP. Arrows outrange a retreat. */
    @Test
    void rangedAttackersAreFoughtNotFled() {
        S s = new S();
        s.health = CONFIG.fleeHealth() - 2;
        s.mobs.add(new Mob(1, "minecraft:skeleton", 3, 3, true, false, false, false));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.add(zombie(2, 4));
        s.mobs.add(zombie(3, 5));
        s.health = CONFIG.fleeHealth() + 2;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "outnumbered, but the nearest is a skeleton");
        s.mobs.set(0, zombie(1, 3));
        s.health = CONFIG.fleeHealth();
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response(), "melee mobs can still be outrun");
    }

    @Test
    void outnumberedRetreatsSooner() {
        S s = new S();
        s.health = CONFIG.fleeHealth() + 2;
        s.mobs.add(zombie(1, 3));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.add(zombie(2, 4));
        s.mobs.add(zombie(3, 5));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.health = 20;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    @Test
    void projectilesNeedAShield() {
        S s = new S();
        s.projectile = true;
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
        s.shield = true;
        assertEquals(Response.SHIELD, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    @Test
    void drowningSwimsUp() {
        S s = new S();
        s.underwater = true;
        s.air = 150;
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
        s.air = 90;
        assertEquals(Response.SURFACE, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    @Test
    void lowHealthEatsOnlyWithFood() {
        S s = new S();
        s.health = CONFIG.fleeHealth();
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
        s.canEat = true;
        assertEquals(Response.EAT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.health = CONFIG.fleeHealth() + 1;
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
    }

    @Test
    void strangersStopTheJobUnlessTurnedOff() {
        S s = new S();
        s.stranger = 10;
        Decision d = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.HAND_BACK, d.response());
        assertTrue(d.reason().contains("you have control"), d.reason());
        assertNull(ThreatRanking.decide(s.build(), new Config(CONFIG.fleeHealth(), false)));
        s.stranger = 13;
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
    }
}
