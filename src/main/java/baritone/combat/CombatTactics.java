package baritone.combat;

/**
 * What to do against one mob this tick. Pure (plain numbers, no Minecraft types) so every rule is unit-tested;
 * {@link CombatRunner} fills in a {@link Situation} from the game and carries the move out.
 *
 * <p>The rules, as a careful player fights:
 * <ul>
 *   <li>Melee hits wait for a full attack cooldown and land as critical hits: jump, then hit on the way down.
 *       While the sword recharges, the shield is up.</li>
 *   <li>A creeper is hit flat (the sprint hit knocks it back) and then backed off from while the sword recharges;
 *       a hissing creeper close by is simply left. At range it is shot.</li>
 *   <li>A skeleton is closed on at a sprint, with the shield up while it draws its bow.</li>
 *   <li>A blaze out of reach is shot, with the shield up while it charges its fireballs (unless the arrow is
 *       nearly drawn); one low enough is fought in melee.</li>
 *   <li>Endermen are never shot: arrows can't hit them.</li>
 *   <li>A shot on its way from anything (another blaze, a skeleton behind) gets the shield, unless the bow is
 *       nearly drawn or a hissing creeper is close.</li>
 * </ul>
 */
public final class CombatTactics {

    /** Attack cooldown at which a hit does full damage. */
    public static final float READY = 0.95f;
    /** A charging creeper closer than this is left alone. */
    public static final double CREEPER_SAFE = 6;
    /** Shoot creepers and other walkers only from at least this far. */
    public static final double BOW_MIN_DISTANCE = 6;
    /** Shoot a blaze from further than this, or whenever it hovers out of reach. */
    public static final double BLAZE_BOW_DISTANCE = 4;
    /** A mob this far above the feet can't be hit with a sword. */
    public static final double REACH_HEIGHT = 2.5;
    /** Once drawn this long, finish the shot rather than raise the shield. */
    public static final int COMMIT_DRAW_TICKS = 10;
    /** Raise the shield against a drawing skeleton only while further than this (closer, just hit it). */
    public static final double SHIELD_DISTANCE = 4;

    private CombatTactics() {
    }

    public enum Foe {
        /** Walks up and hits: zombies, spiders, piglins, hoglins. */
        MELEE,
        /** Shoots from the ground: skeletons, strays, pillagers. */
        RANGED,
        CREEPER,
        /** Teleports away from arrows. */
        ENDERMAN,
        /** Flies and shoots fireballs: blazes, ghasts. */
        BLAZE
    }

    public enum Move {
        /** Attack now. */
        STRIKE,
        /** Jump, to hit on the way down for a critical. */
        JUMP,
        /** Face the mob and hold still. */
        WAIT,
        /** Hold the shield up toward the mob. */
        SHIELD,
        /** Walk toward the mob with the shield up. */
        SHIELD_APPROACH,
        /** Sprint straight at the mob (the ground between is safe). */
        CHARGE,
        /** Path to the mob. */
        APPROACH,
        /** Move away from the mob. */
        BACK_OFF,
        /** Draw the bow (or keep drawing), aiming. */
        DRAW,
        /** Release the drawn bow. */
        LOOSE
    }

    /**
     * The fight as seen this tick. {@code heightAbove} is the mob's feet above the player's; {@code foeCharging}
     * means a creeper hisses, a skeleton draws or a blaze is lit up to fire; {@code hasBow} means a bow and arrows;
     * {@code drawTicks} is how long the bow has been drawn (0 when not); {@code aimSolved} means the bow math
     * found a line to the mob and the player is looking along it; {@code clearRun} means flat safe ground between;
     * {@code shotIncoming} means an arrow or fireball from any mob is flying at the player.
     */
    public record Situation(Foe foe, double distance, double heightAbove, boolean inReach, boolean lineOfSight,
                            float cooldown, boolean onGround, boolean falling, boolean canJump, boolean inWater,
                            boolean hasShield, boolean hasBow, int drawTicks, boolean aimSolved, boolean foeCharging,
                            boolean clearRun, boolean shotIncoming) {

        public static Builder builder(Foe foe) {
            return new Builder().foe(foe);
        }

        public static final class Builder {
            private Foe foe = Foe.MELEE;
            private double distance;
            private double heightAbove;
            private boolean inReach;
            private boolean lineOfSight;
            private float cooldown;
            private boolean onGround;
            private boolean falling;
            private boolean canJump;
            private boolean inWater;
            private boolean hasShield;
            private boolean hasBow;
            private int drawTicks;
            private boolean aimSolved;
            private boolean foeCharging;
            private boolean clearRun;
            private boolean shotIncoming;

            public Builder foe(Foe v) { foe = v; return this; }
            public Builder distance(double v) { distance = v; return this; }
            public Builder heightAbove(double v) { heightAbove = v; return this; }
            public Builder inReach(boolean v) { inReach = v; return this; }
            public Builder lineOfSight(boolean v) { lineOfSight = v; return this; }
            public Builder cooldown(float v) { cooldown = v; return this; }
            public Builder onGround(boolean v) { onGround = v; return this; }
            public Builder falling(boolean v) { falling = v; return this; }
            public Builder canJump(boolean v) { canJump = v; return this; }
            public Builder inWater(boolean v) { inWater = v; return this; }
            public Builder hasShield(boolean v) { hasShield = v; return this; }
            public Builder hasBow(boolean v) { hasBow = v; return this; }
            public Builder drawTicks(int v) { drawTicks = v; return this; }
            public Builder aimSolved(boolean v) { aimSolved = v; return this; }
            public Builder foeCharging(boolean v) { foeCharging = v; return this; }
            public Builder clearRun(boolean v) { clearRun = v; return this; }
            public Builder shotIncoming(boolean v) { shotIncoming = v; return this; }

            public Situation build() {
                return new Situation(foe, distance, heightAbove, inReach, lineOfSight, cooldown, onGround, falling,
                        canJump, inWater, hasShield, hasBow, drawTicks, aimSolved, foeCharging, clearRun, shotIncoming);
            }
        }
    }

    public static Move decide(Situation s) {
        // A drawn bow is loosed at anything it can hit, rather than wasted.
        if (s.drawTicks() >= BowMath.FULL_DRAW_TICKS && s.aimSolved() && s.lineOfSight() && s.foe() != Foe.ENDERMAN) {
            return Move.LOOSE;
        }
        boolean hissing = s.foe() == Foe.CREEPER && s.foeCharging() && s.distance() < CREEPER_SAFE;
        if (s.shotIncoming() && s.hasShield() && s.drawTicks() < COMMIT_DRAW_TICKS && !hissing) return Move.SHIELD;
        return switch (s.foe()) {
            case CREEPER -> creeper(s);
            case BLAZE -> blaze(s);
            case RANGED -> {
                if (s.inReach()) yield melee(s);
                if (s.foeCharging() && s.hasShield() && s.distance() > SHIELD_DISTANCE) yield Move.SHIELD_APPROACH;
                yield close(s);
            }
            case MELEE, ENDERMAN -> s.inReach() ? melee(s) : close(s);
        };
    }

    private static Move creeper(Situation s) {
        if (s.foeCharging() && s.distance() < CREEPER_SAFE) return Move.BACK_OFF;
        if (s.inReach()) return s.cooldown() >= READY ? Move.STRIKE : Move.BACK_OFF;
        if (s.hasBow() && s.lineOfSight() && s.distance() >= BOW_MIN_DISTANCE) return Move.DRAW;
        return close(s);
    }

    private static Move blaze(Situation s) {
        if (s.inReach()) return melee(s);
        if (s.foeCharging() && s.hasShield() && s.drawTicks() < COMMIT_DRAW_TICKS) return Move.SHIELD;
        boolean high = s.heightAbove() > REACH_HEIGHT;
        if (s.hasBow() && s.lineOfSight() && (high || s.distance() > BLAZE_BOW_DISTANCE)) return Move.DRAW;
        if (high) {
            if (!s.lineOfSight()) return Move.APPROACH;
            return s.hasShield() ? Move.SHIELD : Move.WAIT;
        }
        return close(s);
    }

    /** In reach: a critical hit when the cooldown is full, the shield while it recharges. */
    private static Move melee(Situation s) {
        if (s.cooldown() < READY) return s.hasShield() ? Move.SHIELD : Move.WAIT;
        if (s.inWater() || !s.canJump()) return Move.STRIKE;
        if (s.onGround()) return Move.JUMP;
        return s.falling() ? Move.STRIKE : Move.WAIT;
    }

    private static Move close(Situation s) {
        return s.clearRun() && s.lineOfSight() ? Move.CHARGE : Move.APPROACH;
    }
}
