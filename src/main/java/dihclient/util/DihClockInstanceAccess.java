package dihclient.util;

/** 26.3: marks the client clock instance that holds the overworld's time (for the No Render time override). */
public interface DihClockInstanceAccess {
    void dih$markOverworld();
}
