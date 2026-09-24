package dihclient.util;

import net.minecraft.client.KeyMapping;

public interface DihKeyMappingBridge {
    static DihKeyMappingBridge of(KeyMapping mapping) {
        return (DihKeyMappingBridge) mapping;
    }

    boolean dih$isActuallyDown();

    void dih$resetPressedState();

    void dih$simulatePress(boolean pressed);
}
