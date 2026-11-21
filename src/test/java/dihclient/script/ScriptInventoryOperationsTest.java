package dihclient.script;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptInventoryOperationsTest {
    @Test void safetyAbortKeepsOwnershipUntilRecoveryThenResetsForNextOperation() {
        var ownership=new ScriptInventoryOperations.Ownership();
        assertFalse(ownership.requestAbort());
        assertEquals("yielded",ownership.run(()->{
            assertFalse(ownership.aborted());
            assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(ownership::requestAbort).join());
            assertTrue(ownership.aborted());
            assertTrue(ownership.busy());
            assertEquals("busy",ownership.run(()->"unsafe concurrent action","busy"));
            return "yielded";
        },"busy"));
        assertFalse(ownership.busy());
        ownership.run(()->{assertFalse(ownership.aborted()); return true;},false);
    }
    @Test void ownershipRejectsNestedCallbacksAndReleasesAfterFailure() {
        var ownership=new ScriptInventoryOperations.Ownership();
        assertEquals("busy",ownership.run(()->ownership.run(()->"competing","busy"),"busy"));
        assertEquals("busy",ownership.run(()->java.util.concurrent.CompletableFuture.supplyAsync(
            ()->ownership.run(()->"other script","busy")).join(),"busy"));
        assertThrows(IllegalStateException.class,()->ownership.run(()->{ throw new IllegalStateException("cleanup failed"); },"busy"));
        assertEquals("next",ownership.run(()->"next","busy"));
    }
    @Test void exactMoveLeavesRemainderAndEmptyCursor() {
        Fake access = new Fake();
        access.put(0, "gold", 64); access.put(1, "gold", 60);
        assertEquals(4, ScriptInventoryOperations.move(access, 0, 1, 10));
        assertEquals(60, access.stack(0).count());
        assertEquals(64, access.stack(1).count());
        assertEquals(0, access.cursor().count());
    }
    @Test void fullInventorySwapDoesNotDropItems() {
        Fake access = new Fake();
        access.put(0, "steak", 64); access.put(1, "sword", 1);
        assertTrue(ScriptInventoryOperations.swap(access, 0, 1));
        assertEquals("sword", access.stack(0).kind());
        assertEquals("steak", access.stack(1).kind());
        assertEquals(0, access.cursor().count());
    }
    @Test void sameItemSwapExchangesCountsRatherThanMerging() {
        Fake access = new Fake();
        access.put(0, "gold", 50); access.put(1, "gold", 20);
        assertTrue(ScriptInventoryOperations.swap(access, 0, 1));
        assertEquals(20, access.stack(0).count()); assertEquals(50, access.stack(1).count());
    }
    @Test void occupiedCursorRejectsWithoutClicks() {
        Fake access = new Fake(); access.put(0, "gold", 64);
        access.carried = stack("diamond", 2);
        assertEquals(0, ScriptInventoryOperations.move(access, 0, 1, 3));
        assertFalse(ScriptInventoryOperations.swap(access, 0, 1));
        assertEquals(0, ScriptInventoryOperations.drop(access, 0, 3));
        assertEquals(0, access.clicks);
    }
    @Test void rejectedClickIsNotReportedAsSuccess() {
        Fake access = new Fake(); access.put(0, "gold", 64); access.reject = true;
        assertEquals(0, ScriptInventoryOperations.move(access, 0, 1, 4));
        assertEquals(64, access.stack(0).count()); assertEquals(0, access.stack(1).count());
    }
    @Test void dropRemovesOnlyRequestedAmount() {
        Fake access = new Fake(); access.put(0, "gold", 64);
        assertEquals(3, ScriptInventoryOperations.drop(access, 0, 3));
        assertEquals(61, access.stack(0).count()); assertEquals(3, access.dropped);
    }
    @Test void predictedDropWithoutServerConfirmationReturnsZero() {
        Fake state=new Fake(); state.put(0,"gold",64);
        ScriptInventoryOperations.Access unconfirmed=new ScriptInventoryOperations.Access() {
            public ScriptInventoryOperations.Stack stack(int slot) { return state.stack(slot); }
            public ScriptInventoryOperations.Stack cursor() { return state.cursor(); }
            public boolean click(int slot,int button,boolean throwing) { state.click(slot,button,throwing); return false; }
        };
        assertEquals(0,ScriptInventoryOperations.drop(unconfirmed,0,64));
    }
    private static ScriptInventoryOperations.Stack stack(String kind, int count) {
        return new ScriptInventoryOperations.Stack(kind, count, 64);
    }
    private static final class Fake implements ScriptInventoryOperations.Access {
        final Map<Integer, ScriptInventoryOperations.Stack> slots = new HashMap<>();
        ScriptInventoryOperations.Stack carried = ScriptInventoryOperationsTest.stack("air", 0);
        int clicks, dropped; boolean reject;
        void put(int slot, String kind, int count) { slots.put(slot, ScriptInventoryOperationsTest.stack(kind, count)); }
        public ScriptInventoryOperations.Stack stack(int slot) { return slots.getOrDefault(slot, ScriptInventoryOperationsTest.stack("air", 0)); }
        public ScriptInventoryOperations.Stack cursor() { return carried; }
        public boolean click(int slot, int button, boolean throwing) {
            clicks++; if (reject) return false;
            var target = stack(slot);
            if (throwing) {
                int n = button == 1 ? target.count() : Math.min(1, target.count());
                dropped += n; slots.put(slot, new ScriptInventoryOperations.Stack(target.kind(), target.count()-n, 64)); return true;
            }
            if (carried.count() == 0) { carried = target; slots.put(slot, ScriptInventoryOperationsTest.stack("air",0)); }
            else if (target.count() == 0 || target.kind().equals(carried.kind())) {
                int n = Math.min(button == 1 ? 1 : carried.count(), 64-target.count());
                slots.put(slot, new ScriptInventoryOperations.Stack(carried.kind(), target.count()+n,64));
                carried = new ScriptInventoryOperations.Stack(carried.kind(),carried.count()-n,64);
            } else { slots.put(slot,carried); carried=target; }
            return true;
        }
    }
}
