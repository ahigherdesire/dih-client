package dihclient.security;

public final class DihProtectorServerPackFailureGuard {
    private static volatile long suppressServerPacksUntilMs;

    private DihProtectorServerPackFailureGuard() {
    }

    public static void suppressServerPacksTemporarily() {
        suppressServerPacksUntilMs = Math.max(suppressServerPacksUntilMs, System.currentTimeMillis() + 15_000L);
    }

    public static boolean shouldSuppressServerPacks() {
        return System.currentTimeMillis() < suppressServerPacksUntilMs;
    }

    public static void clear() {
        suppressServerPacksUntilMs = 0L;
    }
}
