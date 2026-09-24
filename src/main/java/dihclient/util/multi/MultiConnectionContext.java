package dihclient.util.multi;

import dihclient.util.DihProxy;
import dihclient.util.DihProxyType;
import net.minecraft.network.Connection;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MultiConnectionContext {
    public record ProxySpec(DihProxyType type, String address, int port, String username, String password) {
        public static ProxySpec copyOf(DihProxy proxy) {
            if (proxy == null) return null;
            return new ProxySpec(
                proxy.type,
                proxy.address == null ? "" : proxy.address,
                proxy.port,
                proxy.username == null ? "" : proxy.username,
                proxy.password == null ? "" : proxy.password
            );
        }
    }

    private static final ThreadLocal<Boolean> CONNECTING_MULTI = new ThreadLocal<>();
    private static final ThreadLocal<ProxySpec> CONNECTING_PROXY = new ThreadLocal<>();

    public static ProxySpec beginConnect(ProxySpec proxy) {
        CONNECTING_MULTI.set(Boolean.TRUE);
        ProxySpec previous = CONNECTING_PROXY.get();
        if (proxy == null) CONNECTING_PROXY.remove();
        else CONNECTING_PROXY.set(proxy);
        return previous;
    }

    public static void endConnect(ProxySpec previous) {
        CONNECTING_MULTI.remove();
        if (previous == null) CONNECTING_PROXY.remove();
        else CONNECTING_PROXY.set(previous);
    }

    public static boolean isConnecting() {
        return Boolean.TRUE.equals(CONNECTING_MULTI.get());
    }

    public static ProxySpec pendingProxy() {
        return CONNECTING_PROXY.get();
    }

    private static final AttributeKey<Boolean> MULTI_CHANNEL =
        AttributeKey.valueOf("dihclient:multi_connection");

    private static final Map<Connection, ProxySpecHolder> UNMIXED_CONTEXTS = new ConcurrentHashMap<>();

    private MultiConnectionContext() {
    }

    public static void register(Connection connection, DihProxy proxy) {
        if (connection == null) return;
        ProxySpec spec = ProxySpec.copyOf(proxy);
        if (connection instanceof MultiConnectionMarker marker) {
            marker.dih$setMultiManaged(spec);
        } else {
            UNMIXED_CONTEXTS.put(connection, new ProxySpecHolder(spec));
        }
    }

    public static boolean isMulti(Connection connection) {
        if (connection instanceof MultiConnectionMarker marker) return marker.dih$isMultiManaged();
        return connection != null && UNMIXED_CONTEXTS.containsKey(connection);
    }

    public static void bindChannel(Connection connection, Channel channel) {
        if (isMulti(connection) && channel != null) channel.attr(MULTI_CHANNEL).set(Boolean.TRUE);
    }

    public static boolean isMulti(Channel channel) {
        return channel != null && Boolean.TRUE.equals(channel.attr(MULTI_CHANNEL).get());
    }

    public static void unbindChannel(Channel channel) {
        if (channel != null) channel.attr(MULTI_CHANNEL).set(null);
    }

    public static ProxySpec proxy(Connection connection) {
        if (connection instanceof MultiConnectionMarker marker) return marker.dih$multiProxy();
        ProxySpecHolder holder = connection == null ? null : UNMIXED_CONTEXTS.get(connection);
        return holder == null ? null : holder.proxy;
    }

    public static void remove(Connection connection) {
        if (connection instanceof MultiConnectionMarker marker) {
            marker.dih$clearMultiManaged();
        } else if (connection != null) {
            UNMIXED_CONTEXTS.remove(connection);
        }
    }

    private record ProxySpecHolder(ProxySpec proxy) {
    }
}
