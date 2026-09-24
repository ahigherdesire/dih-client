package dihclient.util;

public interface DihChamsHolder {
    void dih$setChams(boolean active, int visibleColor, int occludedColor);

    boolean dih$chamsActive();

    int dih$chamsVisible();

    int dih$chamsOccluded();
}
