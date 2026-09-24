package dihclient.security;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihPackResponseSchedulerTest {
    @BeforeEach
    @AfterEach
    void clearQueue() {
        DihPackResponseScheduler.clearAll();
    }

    @Test
    void acceptedDownloadedAppliedAreStrictlyOrdered() {

        for (int i = 0; i < 200; i++) {
            long accepted = DihPackResponseScheduler.acceptDelayMs();
            long downloaded = DihPackResponseScheduler.downloadedDelayMs(accepted);
            long applied = DihPackResponseScheduler.appliedDelayMs(downloaded);
            long failed = DihPackResponseScheduler.failedDelayMs(accepted);
            assertTrue(accepted > 0, "accepted must not be same-tick");
            assertTrue(downloaded > accepted, "downloaded must follow accepted");
            assertTrue(applied > downloaded, "applied must follow downloaded");
            assertTrue(failed > accepted, "failed download must follow accepted");
        }
    }

    @Test
    void aDueReplyIsReleasedOnTick() {
        UUID pack = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        DihPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 0L, sent::add);
        assertTrue(DihPackResponseScheduler.hasPending());
        DihPackResponseScheduler.tick();
        assertEquals(1, sent.size());
        assertEquals(ServerboundResourcePackPacket.Action.DECLINED, sent.get(0).action());
        assertEquals(pack, sent.get(0).id());
        assertFalse(DihPackResponseScheduler.hasPending());
    }

    @Test
    void aReplyThatIsNotDueYetStaysQueued() {
        UUID pack = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        DihPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 600_000L, sent::add);
        DihPackResponseScheduler.tick();
        assertTrue(sent.isEmpty());
        assertTrue(DihPackResponseScheduler.hasPending());
    }

    @Test
    void poppingAPackDropsOnlyItsQueuedReplies() {
        UUID popped = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        DihPackResponseScheduler.schedule(popped, ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        DihPackResponseScheduler.schedule(kept, ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        DihPackResponseScheduler.cancel(popped);
        DihPackResponseScheduler.tick();
        assertEquals(1, sent.size());
        assertEquals(kept, sent.get(0).id());
    }

    @Test
    void onSentRunsOnlyWhenTheReplyActuallyGoesOut() {
        UUID pack = UUID.randomUUID();
        boolean[] notified = {false};
        DihPackResponseScheduler.schedule(pack, ServerboundResourcePackPacket.Action.DECLINED, 0L,
            p -> { }, () -> notified[0] = true);
        assertFalse(notified[0]);
        DihPackResponseScheduler.tick();
        assertTrue(notified[0]);
    }

    @Test
    void clearAllDropsEverything() {
        List<ServerboundResourcePackPacket> sent = new ArrayList<>();
        DihPackResponseScheduler.schedule(UUID.randomUUID(), ServerboundResourcePackPacket.Action.ACCEPTED, 0L, sent::add);
        DihPackResponseScheduler.schedule(UUID.randomUUID(), ServerboundResourcePackPacket.Action.DECLINED, 0L, sent::add);
        DihPackResponseScheduler.clearAll();
        DihPackResponseScheduler.tick();
        assertTrue(sent.isEmpty());
    }
}
