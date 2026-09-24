package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihWindowClampTest {
    private static DihWindowLayout layout(int x, int y, int w, int h) {
        return new DihWindowLayout(x, y, w, h, true, false);
    }

    @Test
    void aMinimizedWindowLeavesBoundsUntouched() {

        DihWindowLayout bounds = layout(300, 200, 420, 260);
        assertSame(bounds, DihWindow.clampToScreenSize(bounds, 100, 60, 0, 0));
        assertSame(bounds, DihWindow.clampToScreenSize(bounds, 100, 60, 0, 1080));
        assertSame(bounds, DihWindow.clampToScreenSize(bounds, 100, 60, 1920, 0));
    }

    @Test
    void anOnScreenWindowIsLeftWhereItIs() {
        DihWindowLayout clamped = DihWindow.clampToScreenSize(layout(300, 200, 420, 260), 100, 60, 1920, 1080);
        assertEquals(300, clamped.x);
        assertEquals(200, clamped.y);
        assertEquals(420, clamped.width);
        assertEquals(260, clamped.height);
    }

    @Test
    void anOffScreenWindowIsPulledBackIntoView() {
        DihWindowLayout clamped = DihWindow.clampToScreenSize(layout(5000, 4000, 420, 260), 100, 60, 800, 600);
        assertEquals(800 - 4 - clamped.width, clamped.x);
        assertEquals(600 - 4 - clamped.height, clamped.y);
    }

    @Test
    void shrinkingThenGrowingRestoresTheUsersLayout() {

        DihWindowLayout user = layout(1400, 700, 420, 260);
        DihWindowLayout small = DihWindow.clampToScreenSize(user, 100, 60, 800, 600);
        assertEquals(800 - 4 - small.width, small.x);

        DihWindowLayout restored = DihWindow.clampToScreenSize(user, 100, 60, 1920, 1080);
        assertEquals(user.x, restored.x);
        assertEquals(user.y, restored.y);
        assertEquals(user.width, restored.width);
        assertEquals(user.height, restored.height);
    }

    @Test
    void reclampingAnAlreadyClampedLayoutLosesThePosition() {

        DihWindowLayout user = layout(1400, 700, 420, 260);
        DihWindowLayout small = DihWindow.clampToScreenSize(user, 100, 60, 800, 600);
        DihWindowLayout regrown = DihWindow.clampToScreenSize(small, 100, 60, 1920, 1080);
        assertNotEquals(user.x, regrown.x);
        assertNotEquals(user.y, regrown.y);
    }

    @Test
    void aMinimizedFrameNeverDisturbsAShrunkLayout() {

        DihWindowLayout user = layout(1400, 700, 420, 260);
        DihWindowLayout small = DihWindow.clampToScreenSize(user, 100, 60, 800, 600);
        assertSame(small, DihWindow.clampToScreenSize(small, 100, 60, 0, 0));
    }

    @Test
    void fitsOnScreenAgreesWithTheClamp() {

        assertTrue(DihOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 1920, 1080));
        assertFalse(DihOverlayManager.fitsOnScreen(layout(1400, 700, 420, 260), 800, 600));
        assertFalse(DihOverlayManager.fitsOnScreen(layout(0, 0, 9999, 9999), 800, 600));
    }

    @Test
    void nothingFitsWhileTheWindowIsMinimized() {

        assertFalse(DihOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 0, 0));
        assertFalse(DihOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 1920, 0));
    }

    @Test
    void aWindowFlushAgainstTheSafeMarginStillFits() {

        DihWindowLayout pulledIn = DihWindow.clampToScreenSize(layout(5000, 4000, 420, 260), 100, 60, 800, 600);
        assertTrue(DihOverlayManager.fitsOnScreen(pulledIn, 800, 600));
    }

    @Test
    void aClosedWindowStaysClosedWhenTheStashIsReplayed() {

        DihWindowLayout stashedWhileOpen = new DihWindowLayout(1400, 700, 420, 260, true, false);
        DihWindowLayout liveAfterClosing = new DihWindowLayout(376, 336, 420, 260, false, false);

        DihWindowLayout applied = DihOverlayManager.withLiveState(stashedWhileOpen, liveAfterClosing);
        assertFalse(applied.visible, "a closed window must not be reopened by a resize");
        assertEquals(1400, applied.x, "the user's real position still comes back");
        assertEquals(700, applied.y);
        assertEquals(420, applied.width);
        assertEquals(260, applied.height);
    }

    @Test
    void collapseStateAlsoComesFromTheLiveWindow() {
        DihWindowLayout stashedExpanded = new DihWindowLayout(1400, 700, 420, 260, true, false);
        DihWindowLayout liveCollapsed = new DihWindowLayout(376, 336, 420, 260, true, true);
        assertTrue(DihOverlayManager.withLiveState(stashedExpanded, liveCollapsed).collapsed);
    }

    @Test
    void anOversizedWindowShrinksToFit() {
        DihWindowLayout clamped = DihWindow.clampToScreenSize(layout(0, 0, 9999, 9999), 100, 60, 800, 600);
        assertEquals(800 - 8, clamped.width);
        assertEquals(600 - 8, clamped.height);
        assertEquals(4, clamped.x);
        assertEquals(4, clamped.y);
    }
}
