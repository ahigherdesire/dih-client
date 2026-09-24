package dihclient.util.worldgen.mc26_2;

import java.util.Objects;
import java.util.function.Supplier;

public final class DihOreSimGenerationScope {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private DihOreSimGenerationScope() {
    }

    public static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    public static <T> T call(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        Integer previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                DEPTH.remove();
            } else {
                DEPTH.set(previous);
            }
        }
    }
}
