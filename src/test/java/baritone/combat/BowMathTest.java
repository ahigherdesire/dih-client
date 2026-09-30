package baritone.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BowMathTest {

    @Test
    void drawTimeSetsTheSpeedLikeTheBow() {
        assertEquals(0, BowMath.speed(0), 1e-9);
        assertEquals(1.25, BowMath.speed(10), 1e-9);
        assertEquals(BowMath.FULL_SPEED, BowMath.speed(20), 1e-9);
        assertEquals(BowMath.FULL_SPEED, BowMath.speed(60), 1e-9);
    }

    @Test
    void aFlatShotAimsSlightlyUp() {
        double pitch = BowMath.lowArcPitch(20, 0, BowMath.FULL_SPEED);
        assertTrue(pitch > 0 && pitch < 5, "pitch " + pitch);
    }

    @Test
    void everySolvedPitchLandsOnTheTarget() {
        int solved = 0;
        for (double d = 2; d <= 60; d += 2.5) {
            for (double h = -20; h <= 20; h += 2.5) {
                double pitch = BowMath.lowArcPitch(d, h, BowMath.FULL_SPEED);
                if (Double.isNaN(pitch)) continue;
                solved++;
                assertEquals(h, heightAt(d, pitch, BowMath.FULL_SPEED), 0.05, "d=" + d + " h=" + h + " pitch=" + pitch);
            }
        }
        assertTrue(solved > 300, "most of the grid is in range: " + solved);
    }

    @Test
    void theLowArcIsChosen() {
        double pitch = BowMath.lowArcPitch(30, 0, BowMath.FULL_SPEED);
        assertTrue(pitch < 20, "the flat arc, not the lob: " + pitch);
    }

    @Test
    void outOfRangeTargetsHaveNoPitch() {
        assertTrue(Double.isNaN(BowMath.lowArcPitch(400, 0, BowMath.FULL_SPEED)), "beyond the drag limit");
        assertTrue(Double.isNaN(BowMath.lowArcPitch(10, 150, BowMath.FULL_SPEED)), "too high to reach");
        assertTrue(Double.isNaN(BowMath.lowArcPitch(20, 0, 0)), "no draw");
    }

    @Test
    void yawAndPitchFollowMinecraftsConventions() {
        Vec3 eye = new Vec3(0, 64, 0);
        BowMath.Aim south = BowMath.aim(eye, new Vec3(0, 64, 20), Vec3.ZERO, BowMath.FULL_SPEED);
        assertNotNull(south);
        assertEquals(0, wrap(south.yaw()), 0.01);
        assertTrue(south.pitch() < 0, "looking up (negative pitch) to reach a level target: " + south.pitch());
        BowMath.Aim west = BowMath.aim(eye, new Vec3(-20, 64, 0), Vec3.ZERO, BowMath.FULL_SPEED);
        assertEquals(90, wrap(west.yaw()), 0.01);
        BowMath.Aim east = BowMath.aim(eye, new Vec3(20, 64, 0), Vec3.ZERO, BowMath.FULL_SPEED);
        assertEquals(-90, wrap(east.yaw()), 0.01);
        BowMath.Aim below = BowMath.aim(eye, new Vec3(0, 44, 5), Vec3.ZERO, BowMath.FULL_SPEED);
        assertTrue(below.pitch() > 60, "steeply down: " + below.pitch());
    }

    @Test
    void aStillTargetIsHit() {
        Vec3 eye = new Vec3(3.5, 70.52, -2.5);
        Vec3 target = new Vec3(-17.2, 75.1, 21.9);
        BowMath.Aim aim = BowMath.aim(eye, target, Vec3.ZERO, BowMath.FULL_SPEED);
        assertNotNull(aim);
        assertTrue(miss(eye, aim, target, Vec3.ZERO) < 0.1, "miss " + miss(eye, aim, target, Vec3.ZERO));
    }

    @Test
    void aMovingTargetIsLed() {
        Vec3 eye = new Vec3(0, 64, 0);
        Vec3 target = new Vec3(25, 68, 10);
        Vec3 velocity = new Vec3(0.1, 0.02, -0.25);
        BowMath.Aim led = BowMath.aim(eye, target, velocity, BowMath.FULL_SPEED);
        BowMath.Aim unled = BowMath.aim(eye, target, Vec3.ZERO, BowMath.FULL_SPEED);
        assertNotNull(led);
        assertTrue(miss(eye, led, target, velocity) < 0.3, "led miss " + miss(eye, led, target, velocity));
        assertTrue(miss(eye, unled, target, velocity) > 1.5, "without lead it would miss: " + miss(eye, unled, target, velocity));
        assertTrue(led.ticks() > 5 && led.ticks() < 20, "flight ticks " + led.ticks());
    }

    @Test
    void anUnreachableTargetHasNoAim() {
        assertNull(BowMath.aim(Vec3.ZERO, new Vec3(0, 0, 500), Vec3.ZERO, BowMath.FULL_SPEED));
        assertNull(BowMath.aim(Vec3.ZERO, new Vec3(0, 0, 20), Vec3.ZERO, 0));
    }

    /** The arrow's height after it has flown {@code distance} horizontally, by stepping the game's own tick order. */
    private static double heightAt(double distance, double pitchUp, double speed) {
        double vx = speed * Math.cos(Math.toRadians(pitchUp));
        double vy = speed * Math.sin(Math.toRadians(pitchUp));
        double x = 0, y = 0;
        for (int tick = 0; tick < 400; tick++) {
            if (x + vx >= distance) return y + vy * (distance - x) / vx;
            x += vx;
            y += vy;
            vx *= BowMath.DRAG;
            vy = vy * BowMath.DRAG - BowMath.GRAVITY;
        }
        return Double.NaN;
    }

    /** Closest the arrow comes to the moving target, stepping arrow and target together tick by tick. */
    private static double miss(Vec3 eye, BowMath.Aim aim, Vec3 target, Vec3 velocity) {
        double yaw = Math.toRadians(aim.yaw());
        double pitch = Math.toRadians(aim.pitch());
        Vec3 v = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).scale(BowMath.FULL_SPEED);
        Vec3 arrow = eye;
        Vec3 at = target;
        double best = Double.MAX_VALUE;
        for (int tick = 0; tick < 200; tick++) {
            Vec3 next = arrow.add(v);
            Vec3 nextAt = at.add(velocity);
            for (int i = 0; i <= 20; i++) {
                double f = i / 20.0;
                best = Math.min(best, arrow.lerp(next, f).distanceTo(at.lerp(nextAt, f)));
            }
            arrow = next;
            at = nextAt;
            v = new Vec3(v.x * BowMath.DRAG, v.y * BowMath.DRAG - BowMath.GRAVITY, v.z * BowMath.DRAG);
        }
        return best;
    }

    private static double wrap(float yaw) {
        double y = yaw % 360;
        if (y > 180) y -= 360;
        if (y <= -180) y += 360;
        return y;
    }
}
