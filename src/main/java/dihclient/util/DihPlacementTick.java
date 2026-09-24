package dihclient.util;

public final class DihPlacementTick {
    private static String owner;
    private static int ownedTick = Integer.MIN_VALUE;

    private DihPlacementTick() {
    }

    public static synchronized boolean claim(String moduleId) {
        int tick = DihSharedState.get().getClientTickCounter();

        if (tick == ownedTick) return owner != null && owner.equals(moduleId);
        owner = moduleId;
        ownedTick = tick;
        return true;
    }

    public static synchronized String owner() {
        return DihSharedState.get().getClientTickCounter() == ownedTick ? owner : null;
    }
}
