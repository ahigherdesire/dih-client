package dihclient.security;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihFabricRegisterMimicryTest {

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void fallbackWithoutVoicechatIsExactlyTheStockFabricSet() {
        List<Identifier> channels = DihFabricRegisterMimicry.fallbackReceivableChannels(false);
        assertEquals(List.of(id("fabric-screen-handler-api-v1", "open_screen")), channels);
    }

    @Test
    void fallbackWithVoicechatAppendsAllNineVoiceChannelsInStockOrder() {
        List<Identifier> channels = DihFabricRegisterMimicry.fallbackReceivableChannels(true);
        assertEquals(List.of(
            id("fabric-screen-handler-api-v1", "open_screen"),
            id("voicechat", "secret"),
            id("voicechat", "state"),
            id("voicechat", "states"),
            id("voicechat", "remove_state"),
            id("voicechat", "add_group"),
            id("voicechat", "remove_group"),
            id("voicechat", "joined_group"),
            id("voicechat", "add_category"),
            id("voicechat", "remove_category")
        ), channels);
    }

    @Test
    void announcementSelectionNeverExceedsTheWhitelist() {

        List<Identifier> receivable = List.of(
            id("dihclient", "secret"),
            id("fabric-screen-handler-api-v1", "open_screen"));
        List<Identifier> kept = DihProtectorChannelFilter.keepWhitelisted(
            receivable, channel -> !"dihclient".equals(channel.getNamespace()));
        assertEquals(List.of(id("fabric-screen-handler-api-v1", "open_screen")), kept);
    }

    @Test
    void synthesizesOnlyWhenModdedLateAndNoAuthenticRegister() {
        long grace = DihFabricRegisterMimicry.minPlayTicksBeforeSynthesize();

        assertTrue(DihFabricRegisterMimicry.shouldSynthesize(true, false, false, grace));

        assertFalse(DihFabricRegisterMimicry.shouldSynthesize(true, true, false, grace + 100));

        assertFalse(DihFabricRegisterMimicry.shouldSynthesize(false, false, false, grace + 100));

        assertFalse(DihFabricRegisterMimicry.shouldSynthesize(true, false, false, grace - 1));

        assertFalse(DihFabricRegisterMimicry.shouldSynthesize(true, false, true, grace + 100));
    }
}
