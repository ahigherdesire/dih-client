package dihclient.render.mc;

import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Constructor;

public final class DihAddressChecks {
    private static final AddressCheck INLINE_ALLOW_ALL = new AddressCheck() {
        @Override
        public boolean isAllowed(ResolvedServerAddress address) {
            return true;
        }

        @Override
        public boolean isAllowed(ServerAddress address) {
            return true;
        }
    };

    private DihAddressChecks() {
    }

    public static AddressCheck allowAll() {
        AddressCheck vanilla = emptyVanillaCheck();
        return vanilla != null ? vanilla : INLINE_ALLOW_ALL;
    }

    private static AddressCheck emptyVanillaCheck() {
        try {
            for (Class<?> nested : AddressCheck.class.getDeclaredClasses()) {
                if (nested == AddressCheck.class || !AddressCheck.class.isAssignableFrom(nested)) continue;
                Constructor<?> constructor = nested.getDeclaredConstructor(ImmutableList.class);
                constructor.setAccessible(true);
                return (AddressCheck) constructor.newInstance(ImmutableList.of());
            }
        } catch (Throwable ignored) {  }
        return null;
    }
}
