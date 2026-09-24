package dihclient.security;

public interface DihFromPacketAccess {
    void dih$setFromPacket();

    default void dih$setSilent() {

    }
}
