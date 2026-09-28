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
        HAND_BACK
    }

    public static final double CREEPER_RADIUS = 4;
    public static final double HOSTILE_RADIUS = 8;
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
    }

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
     */
    public record Sense(boolean inLava, boolean inFire, boolean burning, boolean waterNearby, boolean falling, double predictedFall,
                        float health, int air, int maxAir, boolean underwater, boolean canEat,
                        List<Mob> mobs, boolean projectileIncoming, double nearestStranger,
                        boolean hasShield, boolean hasWaterBucket) {
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
            } else if (hostile(mob)) {
                out.add(new Threat(Kind.HOSTILE, mob.id(), mob.type(), mob.distance()));
            }
        }
        if (s.projectileIncoming()) out.add(new Threat(Kind.PROJECTILE, -1, "an incoming shot", 0));
        if (s.underwater() && s.maxAir() > 0 && s.air() < s.maxAir() / 3)
            out.add(new Threat(Kind.DROWNING, -1, "low air", 0));
        if (s.health() > 0 && s.health() <= c.fleeHealth() && s.canEat())
            out.add(new Threat(Kind.LOW_HEALTH, -1, "low health", 0));
        if (c.stopForPlayers() && s.nearestStranger() <= PLAYER_RADIUS)
            out.add(new Threat(Kind.PLAYER_NEAR, -1, "a player", s.nearestStranger()));
        out.sort(Comparator.comparing(Threat::kind).thenComparingDouble(Threat::distance));
        return out;
    }

    /** Whether a non-creeper mob counts as a hostile threat: close, targeting the player and within chase range. */
    static boolean hostile(Mob mob) {
        if (mob.distance() > HOSTILE_RADIUS) return false;
        if (mob.distanceFromAnchor() > CHASE_LIMIT + HOSTILE_RADIUS) return false;
        return mob.aggressive() || !mob.neutral() && mob.distance() <= CLOSE_RADIUS;
    }

    /** The response to the most urgent threat that has one, or null when there is nothing to do. */
    public static Decision decide(Sense s, Config c) {
        List<Threat> threats = threats(s, c);
        long crowd = s.mobs().stream().filter(m -> !m.creeper() && hostile(m) && m.distance() <= CROWD_RADIUS).count();
        for (Threat t : threats) {
            Response r = respond(t, s, c, crowd);
            if (r != null) return new Decision(t, r, reason(t, r));
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
                // A hit knocks it back and resets the fuse; once it hisses, get out of range.
                yield creeper != null && !creeper.swelling() && healthy ? Response.FIGHT : Response.BACK_OFF;
            }
            case HOSTILE -> {
                if (!healthy) yield Response.RETREAT;
                if (crowd >= CROWD && s.health() <= c.fleeHealth() + CROWD_HEALTH_MARGIN) yield Response.RETREAT;
                yield Response.FIGHT;
            }
            case PROJECTILE -> s.hasShield() ? Response.SHIELD : null;
            case DROWNING -> Response.SURFACE;
            case LOW_HEALTH -> Response.EAT;
            case PLAYER_NEAR -> Response.HAND_BACK;
        };
    }

    private static Mob mob(Sense s, int id) {
        for (Mob m : s.mobs()) if (m.id() == id) return m;
        return null;
    }

    private static String reason(Threat t, Response r) {
        String what = shortId(t.what());
        return switch (r) {
            case ESCAPE_HAZARD -> "in " + what + ": stepping out";
            case EXTINGUISH -> "on fire: putting it out";
            case WATER_CLUTCH -> what + ": water bucket";
            case BACK_OFF -> what + " close: backing off";
            case FIGHT -> what + " at " + Math.round(t.distance()) + " blocks: fighting";
            case RETREAT -> what + " at " + Math.round(t.distance()) + " blocks, health low: retreating";
            case SHIELD -> what + ": shield up";
            case SURFACE -> "running out of air: swimming up";
            case EAT -> "low health: eating";
            case HAND_BACK -> what + " " + Math.round(t.distance()) + " blocks away: stopping, you have control";
        };
    }

    static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
