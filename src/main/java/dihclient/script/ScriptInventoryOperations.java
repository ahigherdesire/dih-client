package dihclient.script;

import java.util.Objects;

/** Inventory transaction algorithms; Access.click must wait for an authoritative server resync. */
final class ScriptInventoryOperations {
    static final class Ownership {
        private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean abortRequested;
        boolean busy() { return busy.get(); }
        synchronized boolean requestAbort() {
            if(!busy.get()) return false;
            abortRequested=true;
            return true;
        }
        boolean aborted() { return abortRequested; }
        private synchronized boolean acquire() {
            if(!busy.compareAndSet(false,true)) return false;
            abortRequested=false;
            return true;
        }
        private synchronized void release() { busy.set(false); }
        <T> T run(java.util.function.Supplier<T> work,T failure) {
            if(!acquire()) return failure;
            try { return work.get(); }
            finally { release(); }
        }
    }
    record Stack(Object kind, int count, int max) {
        boolean empty() { return count <= 0; }
        boolean sameKind(Stack other) { return !empty() && !other.empty() && Objects.equals(kind, other.kind); }
    }
    interface Access {
        Stack stack(int slot);
        Stack cursor();
        boolean click(int slot, int button, boolean throwing);
    }
    static boolean swap(Access a, int from, int to) {
        if (!a.cursor().empty()) return false;
        Stack source=a.stack(from), target=a.stack(to);
        if(source.max()<=0 || target.max()<=0) return false;
        if(from==to || source.empty() && target.empty()) return true;
        if(source.sameKind(target)) {
            int difference=source.count()-target.count();
            if(difference>0) move(a,from,to,difference);
            else if(difference<0) move(a,to,from,-difference);
        } else {
            if(!a.click(from,0,false)) return false;
            if(!a.click(to,0,false)) { restoreCursor(a,from); return false; }
            if(!a.cursor().empty() && !a.click(from,0,false)) { restoreCursor(a,to); return false; }
        }
        return same(a.stack(from),target) && same(a.stack(to),source) && a.cursor().empty();
    }
    static int move(Access a, int from, int to, int amount) {
        if(from==to || amount<=0 || !a.cursor().empty()) return 0;
        Stack source=a.stack(from),target=a.stack(to);
        if(source.empty() || source.max()<=0 || target.max()<=0 || !target.empty() && !source.sameKind(target)) return 0;
        int count=Math.min(amount,Math.min(source.count(),Math.max(0,target.max()-target.count())));
        if(count==0 || !a.click(from,0,false)) return 0;
        int confirmed=0;
        try {
            if(count==source.count()) {
                if(a.click(to,0,false)) confirmed=count;
            } else {
                for(int i=0;i<count;i++) { if(!a.click(to,1,false)) break; confirmed++; }
            }
        } finally {
            restoreCursor(a,from);
        }
        Stack after=a.stack(to);
        return source.sameKind(after) ? Math.max(0,Math.min(confirmed,after.count()-target.count())) : 0;
    }
    static int drop(Access a, int slot, int amount) {
        if(amount<=0 || !a.cursor().empty()) return 0;
        Stack before=a.stack(slot);
        int count=Math.min(amount,before.count());
        int confirmed=0;
        if(count==before.count() && count>0) { if(a.click(slot,1,true)) confirmed=count; }
        else for(int i=0;i<count;i++) { if(!a.click(slot,0,true)) break; confirmed++; }
        Stack after=a.stack(slot);
        if(!after.empty() && !before.sameKind(after)) return 0;
        return Math.max(0,Math.min(confirmed,before.count()-after.count()));
    }
    private static void restoreCursor(Access a,int slot) {
        Stack carried=a.cursor(),target=a.stack(slot);
        if(!carried.empty() && (target.empty() || carried.sameKind(target) && target.count()+carried.count()<=target.max()))
            a.click(slot,0,false);
    }
    private static boolean same(Stack a,Stack b) {
        return a.empty() && b.empty() || a.count()==b.count() && a.sameKind(b);
    }
}
