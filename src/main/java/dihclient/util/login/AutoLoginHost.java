package dihclient.util.login;

import dihclient.api.custommenu.CustomMenuSnapshot;
import dihclient.api.custommenu.CustomMenuSubmission;

public interface AutoLoginHost {

    String password();

    boolean spawnedInWorld();

    boolean canSendChat();

    CustomMenuSnapshot customMenu();

    boolean submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission);

    boolean sendCommandLine(String line);

    default boolean screenOwnedElsewhere() {
        return false;
    }

    default void note(String message) {
    }

    default void needsPassword(String context) {
    }
}
