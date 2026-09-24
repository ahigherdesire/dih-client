package dihclient.util;

public interface DihSpecialGuiActions {
    void dih$closeWithPacket();

    void dih$closeWithoutPacket();

    void dih$desync();

    default void dih$closeWithPacket(boolean notify) {
        dih$closeWithPacket();
    }

    default void dih$closeWithoutPacket(boolean notify) {
        dih$closeWithoutPacket();
    }

    default void dih$desync(boolean notify) {
        dih$desync();
    }
}
