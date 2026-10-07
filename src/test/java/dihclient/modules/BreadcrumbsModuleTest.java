package dihclient.modules;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreadcrumbsModuleTest {
    private static BreadcrumbsModule.Point at(double x, long time) {
        return new BreadcrumbsModule.Point(x, 64.0D, 0.0D, time);
    }

    @Test
    void standingStillAddsNoPoints() {
        ArrayDeque<BreadcrumbsModule.Point> trail = new ArrayDeque<>();
        BreadcrumbsModule.append(trail, at(0.0D, 0L), 100);
        BreadcrumbsModule.append(trail, at(0.01D, 50L), 100);
        assertEquals(1, trail.size());
        BreadcrumbsModule.append(trail, at(0.5D, 100L), 100);
        assertEquals(2, trail.size());
    }

    @Test
    void aFullTrailDropsItsOldestPoints() {
        ArrayDeque<BreadcrumbsModule.Point> trail = new ArrayDeque<>();
        for (int i = 0; i < 10; i++) BreadcrumbsModule.append(trail, at(i, i), 4);
        assertEquals(4, trail.size());
        assertEquals(6.0D, trail.peekFirst().x());
    }

    @Test
    void expiredPointsGoFromTheFront() {
        ArrayDeque<BreadcrumbsModule.Point> trail = new ArrayDeque<>();
        for (int i = 0; i < 5; i++) BreadcrumbsModule.append(trail, at(i, i * 100L), 100);
        BreadcrumbsModule.expire(trail, 250L);
        assertEquals(2, trail.size());
        assertEquals(300L, trail.peekFirst().time());
    }

    @Test
    void pointsFadeLinearlyOverTheirLife() {
        assertEquals(1.0D, BreadcrumbsModule.fade(0L, 1000L));
        assertEquals(0.5D, BreadcrumbsModule.fade(500L, 1000L));
        assertEquals(0.0D, BreadcrumbsModule.fade(2000L, 1000L));
    }
}
