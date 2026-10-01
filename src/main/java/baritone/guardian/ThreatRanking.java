package baritone.guardian;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What endangers the player right now, and what the Guardian does about it. Pure (plain numbers, no Minecraft
 * types) so the ranking is unit-tested; {@link GuardianProcess} fills in a {@link Sense} every tick.
 *
 * <p>Survival comes first: threats are ranked by {@link Kind} order (the most immediately lethal first), then by
 * distance. The first threat with something useful to do decides the response.
 */
public final class ThreatRanking {

    /** Threat kinds, most urgent first. */
    public enum Kind {
        /** Standing in lava or fire, or burning with water at hand to put it out. */
        LAVA_OR_FIRE,
        /** Falling far enough to take damage. */
        FALLING,
        /** A creeper within {@link #CREEPER_RADIUS}. */
        CREEPER,
        /** A hostile mob targeting the player within {@link #HOSTILE_RADIUS}. */
        HOSTILE,
        /** An arrow or other projectile flying at the player. */
        PROJECTILE,
        /** Under water and running out of air. */
        DROWNING,
        /** Health at or below the flee threshold, with something to eat. */
        LOW_HEALTH,
        /** A player who is not a friend within {@link #PLAYER_RADIUS}. */
        PLAYER_NEAR
    }

    public enum Response {
        /** Step out of the lava or fire onto the nearest safe block. */
        ESCAPE_HAZARD,
        /** Burning: pour a water bucket at the feet, or walk into nearby water. */
        EXTINGUISH,
        /** Place a water bucket under the landing spot. */
        WATER_CLUTCH,
        /** Move away from a creeper. */
        BACK_OFF,
        /** Fight with the best weapon held. */
        FIGHT,
        /** Walk back along the path already walked, away from the threat. */
        RETREAT,
        /** Raise the shield toward the incoming shot. */
        SHIELD,
        /** Swim up for air. */
        SURFACE,
        /** Eat. */
        EAT,
        /** Stop the job and give control back to the player. */
        HAND_BACK,
        /**
         * Dig a 1x1 pit three blocks down and seal the top, then wait there until healed (and, while mobs wait
         * outside, until daylight). What a player does on a bad night with no armour.
         */
        SHELTER,
        /** Get out of a shooter's line of sight (behind a block or a tree), and heal there. */
        COVER
    }

    public static final double CREEPER_RADIUS = 4;
    public static final double HOSTILE_RADIUS = 8;
    /** A mob that shoots counts once it is aiming this close (a skeleton's bow reaches about 15 blocks). */
    public static final double RANGED_RADIUS = 16;
    /** Mobs that are not neutral count as threats this close even before they show aggression. */
    public static final double CLOSE_RADIUS = 4;
    public static final double PROJECTILE_RADIUS = 8;
    public static final double PLAYER_RADIUS = 12;
    /** Never fight a mob further than this from where the Guardian took over. */
    public static final double CHASE_LIMIT = 12;
    /** Fall height (blocks) above which landing hurts. Vanilla deals damage for every block past 3. */
    public static final double SAFE_FALL = 3;
    /** This many aggressive mobs within {@link #CROWD_RADIUS} count as outnumbered. */
    public static final int CROWD = 3;
    public static final double CROWD_RADIUS = 6;
    /** Outnumbered, the Guardian retreats while health is at or below the flee threshold plus this margin. */
    public static final int CROWD_HEALTH_MARGIN = 4;
    /** Stay sheltered until health is back to at least this (half-hearts). */
    public static final int RECOVERED_HEALTH = 16;
    /** Start a pit only while every attacker is further than this: hits knock the player off the column. */
    public static final double SHELTER_CLEARANCE = 3;
    /** Boxed in, sealed or digging, a mob this close is in the pit with the player (it dropped down the shaft). */
    public static final double IN_PIT_RADIUS = 1.5;

    /**
     * A nearby mob.
     *
     * @param distance           from the player
     * @param distanceFromAnchor from where the Guardian took over (the job's position)
     * @param aggressive         the game shows it attacking or aiming (raised arms, drawn bow)
     * @param neutral            only fights when provoked (endermen, zombified piglins, ...)
     * @param swelling           a creeper that has started to explode
     */
    public record Mob(int id, String type, double distance, double distanceFromAnchor, boolean aggressive,
                      boolean neutral, boolean creeper, boolean swelling) {
        /** Shoots from range: running away only gives it free shots. */
        public boolean ranged() {
            return RANGED.contains(type);
        }
    }

    static final java.util.Set<String> RANGED = java.util.Set.of("minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
        "minecraft:pillager", "minecraft:blaze", "minecraft:witch");
    /**
     * Shooters that never show the game's aggression flag (a blaze attacks without it): one that can see the player
     * counts as aggressive.
     */
    static final java.util.Set<String> FIRES_ON_SIGHT = java.util.Set.of("minecraft:blaze");

    /**
     * Everything the ranking looks at, as plain values.
     *
     * @param burning           on fire (after leaving lava, fire burns on for up to 15 seconds)
     * @param waterNearby       water within reach of a short walk
     * @param predictedFall     blocks the player will have fallen on landing (fall distance so far plus the drop below)
     * @param air               remaining air ticks
     * @param canEat            safe food is held and can be eaten now
     * @param projectileIncoming a projectile within {@link #PROJECTILE_RADIUS} is flying toward the player
     * @param nearestStranger   distance to the nearest player who is not a friend, or infinity
     * @param canShelter        a pit can be dug here (pickaxe and a block to seal it held, solid ground below)
     * @param sheltered         standing in a sealed pit: solid all round and overhead
     * @param daylight          overworld daytime, when zombies and skeletons burn (always true in other dimensions)
     * @param canRegen          the food bar is high enough for health to come back on its own
     * @param digging           a pit is already being dug
     * @param inCover           no aiming ranged mob can see the player
     * @param coverNearby       a spot out of every aiming ranged mob's sight is a few steps away
     * @param boxedIn           solid all round at feet and head height (a pit, sealed or not): no way to step back
     */
    public record Sense(boolean inLava, boolean inFire, boolean burning, boolean waterNearby, boolean falling, double predictedFall,
                        float health, int air, int maxAir, boolean underwater, boolean canEat,
                        List<Mob> mobs, boolean projectileIncoming, double nearestStranger,
                        boolean hasShield, boolean hasWaterBucket,
                        boolean canShelter, boolean sheltered, boolean daylight, boolean canRegen, boolean digging,
                        boolean inCover, boolean coverNearby, boolean boxedIn) {
    }

    /**
     * @param fleeHealth     fight only above this health (half-hearts); at or below it, retreat
     * @param stopForPlayers hand control back when a stranger comes near
     */
    public record Config(int fleeHealth, boolean stopForPlayers) {
        public static final Config DEFAULT = new Config(10, true);
    }

    /** One threat. {@code mobId} is the entity id for mob threats, else -1. */
    public record Threat(Kind kind, int mobId, String what, double distance) {
    }

    /** What to do, and why, in a few words for the event log. */
    public record Decision(Threat threat, Response response, String reason) {
    }

    private ThreatRanking() {
    }

    /** All current threats, most urgent first. */
    public static List<Threat> threats(Sense s, Config c) {
        List<Threat> out = new ArrayList<>();
        if (s.inLava() || s.inFire()) out.add(new Threat(Kind.LAVA_OR_FIRE, -1, s.inLava() ? "lava" : "fire", 0));
        else if (s.burning() && (s.waterNearby() || s.hasWaterBucket())) out.add(new Threat(Kind.LAVA_OR_FIRE, -1, "fire", 0));
        if (s.falling() && s.predictedFall() > SAFE_FALL + 0.5)
            out.add(new Threat(Kind.FALLING, -1, "a " + Math.round(s.predictedFall()) + "-block fall", s.predictedFall()));
        for (Mob mob : s.mobs()) {
            if (mob.creeper()) {
                if (mob.distance() <= CREEPER_RADIUS) out.add(new Threat(Kind.CREEPER, mob.id(), mob.type(), mob.distance()));
            } else if (hostile(mob) || lurking(mob, s) || closingIn(mob, s, c)) {
                out.add(new Threat(Kind.HOSTILE, mob.id(), mob.type(), mob.distance()));
            }
        }
        if (s.projectileIncoming()) out.add(new Threat(Kind.PROJECTILE, -1, "an incoming shot", 0));
        if (s.underwater() && s.maxAir() > 0 && s.air() < s.maxAir() / 3)
            out.add(new Threat(Kind.DROWNING, -1, "low air", 0));
        boolean lowHealth = s.health() <= c.fleeHealth() && s.canEat()
            || s.sheltered() && s.health() < RECOVERED_HEALTH && (s.canEat() || s.canRegen());
        if (s.health() > 0 && lowHealth) out.add(new Threat(Kind.LOW_HEALTH, -1, "low health", 0));
        if (c.stopForPlayers() && s.nearestStranger() <= PLAYER_RADIUS)
            out.add(new Threat(Kind.PLAYER_NEAR, -1, "a player", s.nearestStranger()));
        out.sort(Comparator.comparing(Threat::kind).thenComparingDouble(Threat::distance));
        return out;
    }

    /** Whether a non-creeper mob counts as a hostile threat: close, targeting the player and within chase range. */
    static boolean hostile(Mob mob) {
        double radius = mob.ranged() && mob.aggressive() ? RANGED_RADIUS : HOSTILE_RADIUS;
        if (mob.distance() > radius) return false;
        if (mob.distanceFromAnchor() > CHASE_LIMIT + radius) return false;
        return mob.aggressive() || !mob.neutral() && mob.distance() <= CLOSE_RADIUS;
    }

    /**
     * Hurt, with a pit worth digging: a mob coming for the player counts from further out, since digging 3 blocks down
     * takes about as long as a zombie takes to cover {@link #HOSTILE_RADIUS}.
     */
    static boolean closingIn(Mob mob, Sense s, Config c) {
        return s.canShelter() && shelterHelps(s) && !s.sheltered() && s.health() <= c.fleeHealth()
            && mob.aggressive() && !mob.neutral() && mob.distance() <= RANGED_RADIUS && mob.distanceFromAnchor() <= CHASE_LIMIT + RANGED_RADIUS;
    }

    /**
     * Whether a pit gets anything back: health comes back in it (food held, or a food bar full enough to regenerate),
     * or it waits out the night. By day with neither, the Guardian hands a sealed pit straight back to the job.
     */
    static boolean shelterHelps(Sense s) {
        return s.canEat() || s.canRegen() || !s.daylight();
    }

    /** In the pit with the player: sealing it in or waiting it out only helps the mob. */
    static boolean inPit(Mob mob, Sense s) {
        return mob != null && (s.sheltered() || s.digging() || s.boxedIn()) && mob.distance() <= IN_PIT_RADIUS;
    }

    /** Sheltered at night, any hostile mob nearby is waiting for the player to come out: stay in. */
    static boolean lurking(Mob mob, Sense s) {
        return s.sheltered() && !s.daylight() && !mob.neutral() && mob.distance() <= RANGED_RADIUS;
    }

    /** The response to the most urgent threat that has one, or null when there is nothing to do. */
    public static Decision decide(Sense s, Config c) {
        List<Threat> threats = threats(s, c);
        long crowd = s.mobs().stream().filter(m -> !m.creeper() && hostile(m) && m.distance() <= CROWD_RADIUS).count();
        for (Threat t : threats) {
            Response r = respond(t, s, c, crowd);
            if (r != null) return new Decision(t, r, reason(t, r, s));
        }
        return null;
    }

    private static Response respond(Threat t, Sense s, Config c, long crowd) {
        boolean healthy = s.health() > c.fleeHealth();
        return switch (t.kind()) {
            case LAVA_OR_FIRE -> s.inLava() || s.inFire() ? Response.ESCAPE_HAZARD : Response.EXTINGUISH;
            // Nothing breaks a fall without a bucket; the next threat decides instead.
            case FALLING -> s.hasWaterBucket() ? Response.WATER_CLUTCH : null;
            case CREEPER -> {
                Mob creeper = mob(s, t.mobId());
                // Nowhere to back off to in a pit; a hit knocks it back and resets the fuse.
                if (inPit(creeper, s)) yield Response.FIGHT;
                if (s.sheltered()) yield Response.SHELTER;
                // A hit knocks it back and resets the fuse; once it hisses, get out of range.
                yield creeper != null && !creeper.swelling() && healthy ? Response.FIGHT : Response.BACK_OFF;
            }
            case HOSTILE -> {
                Mob attacker = mob(s, t.mobId());
                if (inPit(attacker, s)) yield Response.FIGHT;
                // Sealed in: nothing outside can reach; wait for health while it comes back, and for daylight to deal
                // with the waiting mobs.
                boolean healing = s.health() < RECOVERED_HEALTH && (s.canEat() || s.canRegen());
                if (s.sheltered() && (healing || !s.daylight())) yield Response.SHELTER;
                boolean outnumbered = crowd >= CROWD && s.health() <= c.fleeHealth() + CROWD_HEALTH_MARGIN;
                if (healthy && !outnumbered) {
                    // Blazes are fought by a kill step (with its eating and looting) or left to fly: the Guardian
                    // only shields from them, takes cover and backs off.
                    yield attacker != null && FIRES_ON_SIGHT.contains(attacker.type()) ? null : Response.FIGHT;
                }
                // Sealed in by day with no food and no regeneration: waiting gets nothing back, so the job goes on
                // (and gets food first).
                if (s.sheltered()) yield null;
                // A blaze shoots down into a pit while it is dug: back off out of its sight instead.
                boolean firesOnSight = attacker != null && FIRES_ON_SIGHT.contains(attacker.type());
                if (s.canShelter() && !firesOnSight && (s.digging() || shelterHelps(s) && nearestAttacker(s) > SHELTER_CLEARANCE))
                    yield Response.SHELTER;
                if (attacker != null && attacker.ranged()) {
                    // Arrows outrange a retreat. Hurt: get out of sight and heal; with nowhere to hide, go through it.
                    if (s.inCover() && (s.canEat() || s.canRegen())) yield Response.COVER;
                    if (!s.inCover() && s.coverNearby()) yield Response.COVER;
                    // A blaze's fireballs are slow and so is the blaze: back the way we came, out of its sight, to heal.
                    if (FIRES_ON_SIGHT.contains(attacker.type()) && (s.canEat() || s.canRegen())) yield Response.RETREAT;
                    yield Response.FIGHT;
                }
                yield Response.RETREAT;
            }
            case PROJECTILE -> s.hasShield() ? Response.SHIELD : null;
            case DROWNING -> Response.SURFACE;
            case LOW_HEALTH -> s.sheltered() ? Response.SHELTER : Response.EAT;
            case PLAYER_NEAR -> Response.HAND_BACK;
        };
    }

    private static double nearestAttacker(Sense s) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Mob m : s.mobs()) {
            if (m.creeper() || hostile(m)) nearest = Math.min(nearest, m.distance());
        }
        return nearest;
    }

    private static Mob mob(Sense s, int id) {
        for (Mob m : s.mobs()) if (m.id() == id) return m;
        return null;
    }

    private static String reason(Threat t, Response r, Sense s) {
        String what = shortId(t.what());
        return switch (r) {
            case ESCAPE_HAZARD -> "in " + what + ": stepping out";
            case EXTINGUISH -> "on fire: putting it out";
            case WATER_CLUTCH -> what + ": water bucket";
            case BACK_OFF -> what + " close: backing off";
            case FIGHT -> inPit(mob(s, t.mobId()), s) ? what + " in the pit: fighting"
                : what + " at " + Math.round(t.distance()) + " blocks: fighting";
            case RETREAT -> what + " at " + Math.round(t.distance()) + " blocks, health low: retreating";
            case SHIELD -> what + ": shield up";
            case SURFACE -> "running out of air: swimming up";
            case EAT -> "low health: eating";
            case HAND_BACK -> what + " " + Math.round(t.distance()) + " blocks away: stopping, you have control";
            case COVER -> s.inCover()
                ? "out of " + what + "'s sight: healing"
                : what + " at " + Math.round(t.distance()) + " blocks, health low: taking cover";
            case SHELTER -> s.sheltered()
                ? "sheltering from " + what + " until " + (s.health() < RECOVERED_HEALTH ? "healed" : "daylight")
                : what + " at " + Math.round(t.distance()) + " blocks, health low: digging in";
        };
    }

    static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
