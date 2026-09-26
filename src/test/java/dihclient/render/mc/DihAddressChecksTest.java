package dihclient.render.mc;

import net.minecraft.client.multiplayer.resolver.ServerAddress;

import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;

import net.minecraft.client.multiplayer.resolver.AddressCheck;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DihAddressChecksTest {
    @Test
    void emptyBlocklistAllowsEveryAddress() {
        AddressCheck check = DihAddressChecks.allowAll();
        assertTrue(check.isAllowed(ResolvedServerAddress.from(new InetSocketAddress("blocked.example.com", 25565))));
        assertTrue(check.isAllowed(ResolvedServerAddress.from(new InetSocketAddress("1.2.3.4", 25565))));
        assertTrue(check.isAllowed(ServerAddress.parseString("blocked.example.com")));
        assertTrue(check.isAllowed(ServerAddress.parseString("blocked.example.com:25566")));
    }
}
