package baritone.ai;

import baritone.acquire.AcquireControl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A scriptable stand-in for the {@code #acquire} executor. */
class FakeAcquireControl implements AcquireControl {

    private final List<Consumer<AcquireEvent>> listeners;

    boolean active;
    String status = "idle";
    /** Thrown by start/plan as IllegalArgumentException when set. */
    String rejectWith;
    String startMessage = "Acquiring.";
    String planText = "1. mine 3 oak_log\n2. craft 12 oak_planks";
    /** Fire STARTED from inside start(), like a synchronous executor would. */
    boolean fireStartedSynchronously = true;

    String lastItem;
    int lastCount;

    FakeAcquireControl() {
        this(new ArrayList<>());
    }

    FakeAcquireControl(List<Consumer<AcquireEvent>> listeners) {
        this.listeners = listeners;
    }

    @Override
    public String start(String itemText, int count) {
        if (this.rejectWith != null) {
            throw new IllegalArgumentException(this.rejectWith);
        }
        this.lastItem = itemText;
        this.lastCount = count;
        this.active = true;
        if (this.fireStartedSynchronously) {
            fire(AcquireEvent.Kind.STARTED, "");
        }
        return this.startMessage;
    }

    @Override
    public String plan(String itemText, int count) {
        if (this.rejectWith != null) {
            throw new IllegalArgumentException(this.rejectWith);
        }
        this.lastItem = itemText;
        this.lastCount = count;
        return this.planText;
    }

    @Override
    public void stop() {
        if (this.active) {
            this.active = false;
            fire(AcquireEvent.Kind.STOPPED, "");
        }
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public String status() {
        return this.status;
    }

    @Override
    public void addListener(Consumer<AcquireEvent> listener) {
        this.listeners.add(listener);
    }

    void fire(AcquireEvent.Kind kind, String message) {
        if (kind == AcquireEvent.Kind.DONE || kind == AcquireEvent.Kind.FAILED) {
            this.active = false;
        }
        for (Consumer<AcquireEvent> listener : List.copyOf(this.listeners)) {
            listener.accept(new AcquireEvent(kind, this.lastItem, this.lastCount, message));
        }
    }
}
