package dihclient.util;

import dihclient.DihClientAddon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DihPerf {
    private static final int REPORT_EVERY = 240;
    private static final long DEFAULT_SPIKE_NANOS = 4_000_000L;
    private static final boolean SYSTEM_ENABLED = Boolean.getBoolean("dih.perf");

    private static volatile Map<String, Counter> counters;

    private static volatile boolean configEnabled;
    private static volatile int joinTicksRemaining;
    private static volatile int forcedTicksRemaining;

    private DihPerf() {
    }

    public static long begin() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static long beginSampled() {
        return forcedTicksRemaining > 0 || enabled() ? System.nanoTime() : 0L;
    }

    public static void beginForcedWindow(int ticks) {
        forcedTicksRemaining = Math.max(forcedTicksRemaining, ticks);
    }

    public static void tickForcedWindow() {
        if (forcedTicksRemaining > 0) forcedTicksRemaining--;
    }

    public static boolean isForcedWindowActive() {
        return forcedTicksRemaining > 0;
    }

    public static Map<String, long[]> snapshotCounters() {
        Map<String, Counter> source = counters;
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, long[]> out = new java.util.HashMap<>(source.size());
        for (Map.Entry<String, Counter> entry : source.entrySet()) {
            Counter c = entry.getValue();
            out.put(entry.getKey(), new long[] {c.samples, c.totalNanos, c.maxNanos});
        }
        return out;
    }

    public static long beginJoin() {
        return joinTicksRemaining > 0 && enabled() ? System.nanoTime() : 0L;
    }

    public static void end(String name, long startNanos) {
        if (startNanos == 0L || name == null || name.isBlank()) return;
        long elapsed = System.nanoTime() - startNanos;
        record(name, elapsed);
    }

    public static void endJoinSpike(String name, long startNanos) {
        endJoinSpike(name, startNanos, DEFAULT_SPIKE_NANOS);
    }

    public static void endJoinSpike(String name, long startNanos, long thresholdNanos) {
        if (startNanos == 0L || name == null || name.isBlank()) return;
        long elapsed = System.nanoTime() - startNanos;
        record(name, elapsed);
        if (joinTicksRemaining > 0 && elapsed >= thresholdNanos) {
            DihClientAddon.LOG.warn("[DihPerf] join spike {}={}ms",
                name,
                String.format(java.util.Locale.ROOT, "%.3f", elapsed / 1_000_000.0));
        }
    }

    public static void endSpike(String name, long startNanos, long thresholdNanos) {
        if (startNanos == 0L || name == null || name.isBlank()) return;
        long elapsed = System.nanoTime() - startNanos;
        record(name, elapsed);
        if (elapsed >= thresholdNanos) {
            DihClientAddon.LOG.warn("[DihPerf] spike {}={}ms",
                name,
                String.format(java.util.Locale.ROOT, "%.3f", elapsed / 1_000_000.0));
        }
    }

    private static void record(String name, long elapsed) {
        Counter counter = countersForWrite().computeIfAbsent(name, ignored -> new Counter());
        long samples = ++counter.samples;
        counter.totalNanos += elapsed;
        counter.maxNanos = Math.max(counter.maxNanos, elapsed);
        if (samples % REPORT_EVERY == 0L) {
            double avgMs = counter.totalNanos / (double) samples / 1_000_000.0;
            double maxMs = counter.maxNanos / 1_000_000.0;
            DihClientAddon.LOG.info("[DihPerf] {} avg={}ms max={}ms samples={}",
                name,
                String.format(java.util.Locale.ROOT, "%.3f", avgMs),
                String.format(java.util.Locale.ROOT, "%.3f", maxMs),
                samples);
        }
    }

    public static void beginJoinWindow() {
        if (!enabled()) return;
        joinTicksRemaining = 100;
        DihClientAddon.LOG.info("[DihPerf] join profiling window started.");
    }

    public static void tickJoinWindow() {
        if (joinTicksRemaining > 0) joinTicksRemaining--;
    }

    public static boolean isJoinWindowActive() {
        return joinTicksRemaining > 0 && enabled();
    }

    public static boolean enabled() {
        return SYSTEM_ENABLED || configEnabled;
    }

    static void publishConfigState(DihConfig config) {
        configEnabled = config != null && config.performanceDebug;
    }

    private static Map<String, Counter> countersForWrite() {
        Map<String, Counter> current = counters;
        if (current != null) return current;
        synchronized (DihPerf.class) {
            current = counters;
            if (current == null) {
                current = new ConcurrentHashMap<>();
                counters = current;
            }
            return current;
        }
    }

    private static final class Counter {
        private long samples;
        private long totalNanos;
        private long maxNanos;
    }
}
