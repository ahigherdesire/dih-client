package dihclient.security;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihFontFileGuardTest {

    private static PackResources pack(String id, PackSource source) {
        PackLocationInfo location = new PackLocationInfo(id, Component.literal(id), source, Optional.empty());
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
            new Class<?>[]{PackResources.class}, (proxy, method, args) -> switch (method.getName()) {
                case "location" -> location;
                case "packId" -> id;
                default -> null;
            });
    }

    @Test
    void refusesServerAndWorldPacks() {
        assertTrue(DihFontFileGuard.isUntrusted(pack("server/0000ABCD/3f2a", PackSource.SERVER)));
        assertTrue(DihFontFileGuard.isUntrusted(pack("world", PackSource.WORLD)));
    }

    @Test
    void refusesServerPackIdEvenWhenSourceIsHidden() {
        assertTrue(DihFontFileGuard.isUntrusted(pack("server/0000ABCD/3f2a", PackSource.DEFAULT)));
    }

    @Test
    void keepsGameModAndPlayerPacks() {
        assertFalse(DihFontFileGuard.isUntrusted(pack("vanilla", PackSource.BUILT_IN)));
        assertFalse(DihFontFileGuard.isUntrusted(pack("fabric", PackSource.BUILT_IN)));
        assertFalse(DihFontFileGuard.isUntrusted(pack("file/MyPack.zip", PackSource.DEFAULT)));
        assertFalse(DihFontFileGuard.isUntrusted(null));
    }
}
