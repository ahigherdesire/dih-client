package dihclient.util;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import net.minecraft.server.Services;
//? if >=26.3 {
/*import com.mojang.authlib.Environment;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.response.discovery.Discovery;
import com.mojang.authlib.services.response.discovery.DiscoveryResponse;
import com.mojang.authlib.services.response.discovery.Endpoint;
import com.mojang.authlib.services.response.discovery.Endpoints;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
*///?} else {
import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
//?}

import java.io.File;
import java.net.Proxy;

/**
 * Minecraft's account services for one login, built the way the running version's authlib wants: authlib 9
 * (26.2) configures a YggdrasilAuthenticationService with an Environment of hosts, authlib 10 (26.3) reads every
 * endpoint from Mojang's discovery document. Callers never name either type.
 *
 * <p>TheAltening: session calls (join, verify, profile lookup) and the public keys that verify its signed skins
 * go to TheAltening; everything else stays on Mojang. That is what the authlib 9 Environment did.
 */
public final class DihAuthServices {
    /** TheAltening's session server (joins, profile lookups). */
    public static final String ALTENING_SESSION_HOST = "http://sessionserver.thealtening.com";
    /** TheAltening's auth server (logins, public keys). */
    public static final String ALTENING_AUTH_HOST = "http://authserver.thealtening.com";

    //? if >=26.3 {
    /*private final MinecraftServicesDiscoveryService auth;

    private DihAuthServices(MinecraftServicesDiscoveryService auth) {
        this.auth = auth;
    }

    public static DihAuthServices mojang(Proxy proxy) {
        return new DihAuthServices(MinecraftServicesDiscoveryService.create(proxy));
    }

    /^* Mojang's live discovery document with the session and public-key endpoints pointed at TheAltening. ^/
    @SuppressWarnings("unchecked")
    public static DihAuthServices theAltening(Proxy proxy) {
        try {
            Class<MinecraftServicesDiscoveryService> type = MinecraftServicesDiscoveryService.class;
            Method environment = type.getDeclaredMethod("determineEnvironment");
            Method discovery = type.getDeclaredMethod("createDiscoverySupplier", Proxy.class, Environment.class);
            Constructor<MinecraftServicesDiscoveryService> create =
                type.getDeclaredConstructor(Proxy.class, boolean.class, Supplier.class);
            environment.setAccessible(true);
            discovery.setAccessible(true);
            create.setAccessible(true);
            Supplier<DiscoveryResponse> live = (Supplier<DiscoveryResponse>) discovery.invoke(null, proxy, environment.invoke(null));
            Supplier<DiscoveryResponse> altening = () -> withAlteningSession(live.get());
            return new DihAuthServices(create.newInstance(proxy, true, altening));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This authlib can't be pointed at TheAltening", e);
        }
    }

    private static DiscoveryResponse withAlteningSession(DiscoveryResponse live) {
        Discovery d = live == null ? null : live.discovery();
        if (d == null) return live;
        Endpoints session = new Endpoints(Map.of(
            "join", new Endpoint(ALTENING_SESSION_HOST + "/session/minecraft/join"),
            "verify", new Endpoint(ALTENING_SESSION_HOST + "/session/minecraft/hasJoined"),
            "getProfileById", new Endpoint(ALTENING_SESSION_HOST + "/session/minecraft/profile/{profileId}")
        ));
        Map<String, Endpoint> authentication = new HashMap<>(d.authentication() == null ? Map.of() : d.authentication().endpoints());
        authentication.put("getPublicKeys", new Endpoint(ALTENING_AUTH_HOST + "/publickeys"));
        return new DiscoveryResponse(live.environment(), live.product(), new Discovery(
            d.product(), new Endpoints(authentication), session, d.player(), d.profiles(), d.telemetry()));
    }
    *///?} else {
    private static final Environment ALTENING_ENVIRONMENT =
        new Environment(ALTENING_SESSION_HOST, ALTENING_AUTH_HOST, "https://api.mojang.com", "The Altening");

    private final YggdrasilAuthenticationService auth;

    private DihAuthServices(YggdrasilAuthenticationService auth) {
        this.auth = auth;
    }

    public static DihAuthServices mojang(Proxy proxy) {
        return new DihAuthServices(new YggdrasilAuthenticationService(proxy));
    }

    public static DihAuthServices theAltening(Proxy proxy) {
        return new DihAuthServices(new YggdrasilAuthenticationService(proxy, ALTENING_ENVIRONMENT));
    }
    //?}

    /** Session service, public keys and profile lookups, as the game keeps them in {@code Minecraft.services()}. */
    public Services services(File gameDirectory) {
        return Services.create(auth, gameDirectory);
    }

    public UserApiService userApi(String accessToken) {
        return auth.createUserApiService(accessToken);
    }

    public FriendsService friends(String accessToken) {
        return auth.createFriendsService(accessToken);
    }
}
