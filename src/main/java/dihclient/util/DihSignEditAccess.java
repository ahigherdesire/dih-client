package dihclient.util;

import net.minecraft.core.BlockPos;

public interface DihSignEditAccess {
    BlockPos dih$getSignPos();
    boolean dih$isFrontText();
    String[] dih$getSignLines();
}
