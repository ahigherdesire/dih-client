package com.example.addon.events;

import dihclient.api.DihAddons;
import dihclient.util.DihClientMessaging;

// Event hooks. Register listeners once in onInitialize().
// Available: onTick, onPacketSend (return true to cancel), onPacketReceive, onGameJoin, onGameLeft.
public final class ExampleEvents {
    private ExampleEvents() {}

    public static void register() {
        DihAddons.events().onGameJoin(() ->
            DihClientMessaging.sendPrefixed("\u00a7a[Example] joined a world!"));
    }
}
