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
        boolean lava, fire, burning, water, falling, underwater, canEat, projectile, shield, bucket, canShelter, sheltered,
            inCover, coverNearby, digging, boxedIn;
        boolean daylight = true, canRegen = true;
        double fall, stranger = Double.POSITIVE_INFINITY;
        float health = 20;
        int air = 300;
        final List<Mob> mobs = new ArrayList<>();

        Sense build() {
            return new Sense(lava, fire, burning, water, falling, fall, health, air, 300, underwater, canEat, mobs, projectile, stranger,
                    shield, bucket, canShelter, sheltered, daylight, canRegen, digging, inCover, coverNearby, boxedIn);
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
        s.mobs.add(new Mob(1, "minecraft:zombie", 6, ThreatRanking.CHASE_LIMIT + ThreatRanking.HOSTILE_RADIUS + 1,
                true, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG));
        s.mobs.set(0, new Mob(1, "minecraft:skeleton", 6, ThreatRanking.CHASE_LIMIT + ThreatRanking.RANGED_RADIUS + 1,
                true, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "a shooter's longer reach still has a limit");
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

    /** From real night runs: fighting or fleeing a crowd in the open lost 17 HP; a sealed pit loses none. */
    @Test
    void hurtOrOutnumberedDigsInWhenItCan() {
        S s = new S();
        s.canShelter = true;
        s.health = CONFIG.fleeHealth();
        s.mobs.add(zombie(1, 5));
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.set(0, zombie(1, 2));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response(), "too close to start digging");
        s.digging = true;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "but a started pit is finished");
        s.digging = false;
        s.mobs.set(0, zombie(1, 5));
        s.mobs.set(0, new Mob(1, "minecraft:skeleton", 6, 6, true, false, false, false));
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "cover beats trading arrows when hurt");
        s.health = CONFIG.fleeHealth() + 2;
        s.mobs.add(zombie(2, 4));
        s.mobs.add(zombie(3, 5));
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "outnumbered");
        s.health = 20;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "healthy: fight, even outnumbered");
    }

    @Test
    void shelteredStaysUntilHealedAndDaylight() {
        S s = new S();
        s.sheltered = true;
        s.daylight = false;
        s.health = 20;
        s.mobs.add(zombie(1, 3));
        s.mobs.add(creeper(2, 3, false));
        Decision night = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.SHELTER, night.response(), "mobs waiting outside at night");
        assertTrue(night.reason().contains("daylight"), night.reason());
        s.mobs.clear();
        s.mobs.add(new Mob(1, "minecraft:zombie", 12, 12, false, false, false, false));
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(),
            "a zombie wandering 12 blocks away at night is still waiting for us");
        s.mobs.set(0, zombie(1, 3));
        s.daylight = true;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "day and healthy: deal with it");
        s.mobs.clear();
        s.health = ThreatRanking.RECOVERED_HEALTH - 2;
        Decision healing = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.SHELTER, healing.response(), "nothing outside, but still healing");
        assertTrue(healing.reason().contains("healed"), healing.reason());
        s.canRegen = false;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "no food and no regeneration: waiting would not help");
        s.canRegen = true;
        s.health = ThreatRanking.RECOVERED_HEALTH;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "healed and alone: the job carries on");
    }

    @Test
    void shelteredWithNoWayToHealDoesNotWaitForeverOnAMobOutside() {
        S s = new S();
        s.sheltered = true;
        s.daylight = true;
        s.health = 6;
        s.canEat = false;
        s.canRegen = false;
        s.mobs.add(zombie(1, 6));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "a zombie in the cave that can't reach, and nothing to heal with");
        s.canRegen = true;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "healing: stay in");
        s.canRegen = false;
        s.daylight = false;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "at night: wait for daylight");
    }

    /**
     * From a blaze-rod game-test death: blazes never show the aggression flag, so the Guardian saw one only at 4 blocks
     * and never took cover from it. One that can see the player now counts as aiming.
     */
    @Test
    void aBlazeInSightIsAShooterToTakeCoverFrom() {
        assertTrue(ThreatRanking.FIRES_ON_SIGHT.contains("minecraft:blaze"));
        S s = new S();
        s.health = 8;
        s.canEat = true;
        s.coverNearby = true;
        s.mobs.add(new Mob(1, "minecraft:blaze", 12, 12, true, false, false, false));
        assertEquals(Response.COVER, ThreatRanking.decide(s.build(), CONFIG).response(), "hurt: out of its sight to eat");
        s.health = 20;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "healthy: the kill step fights it, or it is left alone");
    }

    /**
     * From a blaze-rod death: hurt at a spawner on an open fortress floor with no cover near, it fought on and burned.
     * A blaze's fireballs are slow and so is the blaze: back the way we came, out of its sight, and heal there.
     */
    @Test
    void hurtWithNoCoverFromABlazeFallsBack() {
        S s = new S();
        s.health = 8;
        s.canEat = true;
        s.mobs.add(new Mob(1, "minecraft:blaze", 10, 10, true, false, false, false));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.canEat = false;
        s.canRegen = false;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "nothing to heal with: fight it");
        s.canEat = true;
        s.mobs.set(0, new Mob(1, "minecraft:skeleton", 10, 10, true, false, false, false));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "arrows outrun a retreat");
    }

    /**
     * From a blaze-rod death: hurt, 6 blocks from a blaze, it switched between falling back and digging a pit, and
     * burned while digging. A blaze shoots down into a pit as it is dug.
     */
    @Test
    void hurtByABlazeFallsBackRatherThanDigging() {
        S s = new S();
        s.health = 8;
        s.canEat = true;
        s.canShelter = true;
        s.mobs.add(new Mob(1, "minecraft:blaze", 6, 6, true, false, false, false));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.digging = true;
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response(), "a pit begun is left");
        s.mobs.set(0, new Mob(1, "minecraft:zombie", 6, 6, true, false, false, false));
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "a zombie can't reach into one");
    }

    /**
     * From a combat game-test death: outnumbered by blazes in a closed hall at 14 HP, it walked away from ones 2 to 4
     * blocks off and burned. One within reach is fought; the further ones are still backed away from.
     */
    @Test
    void aBlazeInReachIsFoughtNotWalkedAwayFrom() {
        S s = new S();
        s.health = 8;
        s.canEat = true;
        s.mobs.add(new Mob(1, "minecraft:blaze", 3, 3, true, false, false, false));
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response());
        s.mobs.set(0, new Mob(1, "minecraft:blaze", 7, 7, true, false, false, false));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response());
    }

    /** Handed back out of a pit with nothing to heal with, digging a new one only hands it back again. */
    @Test
    void hurtWithNoWayToHealDoesNotDigInByDay() {
        S s = new S();
        s.daylight = true;
        s.health = 6;
        s.canEat = false;
        s.canRegen = false;
        s.canShelter = true;
        s.mobs.add(zombie(1, 6));
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response(), "a pit would not heal us");
        s.digging = true;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "a started pit is finished");
        s.digging = false;
        s.mobs.set(0, zombie(1, 12));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "no pit worth digging: nothing to do at 12 blocks yet");
        s.daylight = false;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "at night a pit waits out the dark");
        s.daylight = true;
        s.canRegen = true;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "a pit to heal in");
    }

    /** From a game-test death: a zombie dropped down the shaft while it was dug, and the seal shut it in with us. */
    @Test
    void aMobInsideThePitIsFoughtNotWaitedOut() {
        S s = new S();
        s.sheltered = true;
        s.daylight = false;
        s.health = 11;
        s.mobs.add(zombie(1, 3.2)); // on the seal, above
        s.mobs.add(zombie(2, 0.3)); // in the pit with us
        Decision inside = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.FIGHT, inside.response(), "sealed in with it: only fighting helps");
        assertEquals(2, inside.threat().mobId());

        s.sheltered = false;
        s.digging = true;
        s.canShelter = true;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "it followed us down mid-dig");

        s.mobs.set(1, creeper(2, 0.8, true));
        s.sheltered = true;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(),
            "nowhere to back off to in a pit: a hit resets its fuse");

        s.mobs.remove(1);
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "the one on the seal can't reach");
    }

    /** A 3-deep pit takes about as long to dig as a zombie takes to cover 8 blocks: when hurt, start as soon as one comes. */
    @Test
    void hurtPlayersDigInBeforeTheMobIsClose() {
        S s = new S();
        s.daylight = false;
        s.health = 8;
        s.canShelter = true;
        s.mobs.add(zombie(1, 12));
        Decision d = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.SHELTER, d == null ? null : d.response(), "coming for us from 12 blocks: dig now");

        s.mobs.set(0, new Mob(1, "minecraft:zombie", 12, 12, false, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "not after us: leave it");
        s.mobs.set(0, zombie(1, 12));
        s.canShelter = false;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "no pit to dig: nothing to do at 12 blocks yet");
        s.canShelter = true;
        s.health = 20;
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "healthy: it can come to us");
    }

    /** From a crowd-test death: a zombie dropped down the shaft before the seal went on, and the Guardian backed off. */
    @Test
    void aMobInAnUnsealedPitIsFoughtNotRetreatedFrom() {
        S s = new S();
        s.daylight = false;
        s.health = 8;
        s.canEat = true;
        s.boxedIn = true;
        s.mobs.add(zombie(1, 3.1)); // at the top of the shaft
        s.mobs.add(zombie(2, 0.7)); // down in it
        Decision d = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.FIGHT, d.response(), "walled in on every side: stepping back goes nowhere");
        assertEquals(2, d.threat().mobId());

        s.boxedIn = false;
        assertEquals(Response.RETREAT, ThreatRanking.decide(s.build(), CONFIG).response(), "in the open, hurt: back off");
    }

    private static Mob aimingSkeleton(int id, double distance) {
        return new Mob(id, "minecraft:skeleton", distance, distance, true, false, false, false);
    }

    /** From game-test deaths: standing still to eat while a skeleton 9-15 blocks away kept shooting. */
    @Test
    void aimingSkeletonsCountFromFurtherAway() {
        S s = new S();
        s.mobs.add(aimingSkeleton(1, 13));
        assertEquals(Kind.HOSTILE, ThreatRanking.decide(s.build(), CONFIG).threat().kind());
        s.mobs.set(0, new Mob(1, "minecraft:skeleton", 13, 13, false, false, false, false));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "not aiming yet: not a threat at 13 blocks");
        s.mobs.set(0, zombie(1, 13));
        assertNull(ThreatRanking.decide(s.build(), CONFIG), "a zombie at 13 blocks is still too far to matter");
    }

    @Test
    void hurtUnderFireTakesCoverThenHealsThere() {
        S s = new S();
        s.health = CONFIG.fleeHealth() - 2;
        s.canEat = true;
        s.mobs.add(aimingSkeleton(1, 12));
        s.coverNearby = true;
        Decision exposed = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.COVER, exposed.response());
        assertTrue(exposed.reason().contains("taking cover"), exposed.reason());
        s.inCover = true;
        Decision hidden = ThreatRanking.decide(s.build(), CONFIG);
        assertEquals(Response.COVER, hidden.response(), "stay hidden and eat");
        assertTrue(hidden.reason().contains("healing"), hidden.reason());
        s.inCover = false;
        s.coverNearby = false;
        assertEquals(Response.FIGHT, ThreatRanking.decide(s.build(), CONFIG).response(), "nowhere to hide: charge it");
        s.canShelter = true;
        assertEquals(Response.SHELTER, ThreatRanking.decide(s.build(), CONFIG).response(), "a pit beats both");
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
