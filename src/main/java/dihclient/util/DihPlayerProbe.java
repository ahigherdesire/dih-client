package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class DihPlayerProbe {

    private static final int PROBE_MAX_QUERIES = 5_000;

    private DihPlayerProbe() {
    }

    public static List<String> everyone(Minecraft mc, boolean probeHidden) {
        return everyone(mc, probeHidden, () -> false);
    }

    public static List<String> everyone(Minecraft mc, boolean probeHidden, BooleanSupplier cancelled) {
        String self = selfName(mc);
        LinkedHashMap<String, String> names = DihNameHarvest.instantNames(mc, self);
        ClientPacketListener connection = mc == null ? null : mc.getConnection();
        if (probeHidden && connection != null) {
            List<DihNameHarvest.Vector> vectors = DihNameHarvest.discoverVectors(connection);
            DihNameHarvest.sweep(connection, names, self, vectors, new DihNameHarvest.Control() {
                @Override
                public boolean cancelled() {
                    Minecraft m = Minecraft.getInstance();
                    return (cancelled != null && cancelled.getAsBoolean())
                        || m == null || m.getConnection() != connection;
                }

                @Override
                public boolean paused() {
                    return false;
                }

                @Override
                public int limit() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public int maxQueries() {
                    return PROBE_MAX_QUERIES;
                }
            });
        }
        return new ArrayList<>(names.values());
    }

    private static String selfName(Minecraft mc) {
        return mc == null || mc.player == null ? "" : mc.player.getName().getString();
    }
}
