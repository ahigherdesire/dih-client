package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DihPluginScanJobStateTest {
    @Test
    void mutableServerContextDoesNotInvalidateSameGeneration() {
        DihPluginScanJobState state = new DihPluginScanJobState(7L, "play.example.net");
        assertTrue(state.matches(7L, "play.example.net"));
        assertTrue(state.matches(7L, "play.example.net"));
    }

    @Test
    void staleGenerationAndDifferentServerAreRejected() {
        DihPluginScanJobState state = new DihPluginScanJobState(8L, "one.example.net");
        assertFalse(state.matches(7L, "one.example.net"));
        assertFalse(state.matches(8L, "two.example.net"));
    }

    @Test
    void consecutiveScansCannotReuseSuggestionIds() {
        DihPluginProbeIdAllocator allocator = new DihPluginProbeIdAllocator();
        int first = allocator.allocateBlock();
        int second = allocator.allocateBlock();
        assertEquals(DihPluginProbeIdAllocator.BLOCK_SIZE, second - first);
        assertTrue(first + DihPluginProbeIdAllocator.BLOCK_SIZE <= second);
    }

    @Test
    void sendFailureRetriesExactlyThreeTimes() {
        DihPluginScanJobState state = new DihPluginScanJobState(1L, "server");
        assertTrue(state.canRetry(0));
        assertTrue(state.canRetry(1));
        assertTrue(state.canRetry(2));
        assertFalse(state.canRetry(3));
        assertFalse(state.canRetry(4));
    }

    @Test
    void missingRepliesAndMalformedProcessingProducePartialResults() {
        DihPluginScanJobState state = new DihPluginScanJobState(1L, "server");
        assertTrue(state.requiresPartialResult(0, 0, 4));
        assertTrue(state.requiresPartialResult(1, 0, 0));
        assertTrue(state.requiresPartialResult(0, 1, 0));
        assertFalse(state.requiresPartialResult(0, 0, 0));
    }

    @Test
    void finalizationIsIdempotentAndRejectsLateReplies() {
        DihPluginScanJobState state = new DihPluginScanJobState(3L, "server");
        assertTrue(state.beginFinalize());
        assertFalse(state.beginFinalize());
        assertFalse(state.matches(3L, "server"));
        assertFalse(state.canRetry(0));
    }

    @Test
    void disconnectOrPanicCancellationUsesTheSameTerminalGuard() {
        DihPluginScanJobState disconnected = new DihPluginScanJobState(4L, "server");
        DihPluginScanJobState panic = new DihPluginScanJobState(5L, "server");
        assertTrue(disconnected.beginFinalize());
        assertTrue(panic.beginFinalize());
        assertTrue(disconnected.isFinalized());
        assertTrue(panic.isFinalized());
    }
}
