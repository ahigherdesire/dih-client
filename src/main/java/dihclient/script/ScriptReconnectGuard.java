package dihclient.script;

import java.util.Locale;
import java.util.UUID;

/** A one-shot resume grant bound to the exact API-initiated connection attempt and server. */
public final class ScriptReconnectGuard {
    private boolean armed;
    private String requestedToken;
    private String expectedServer;
    private Object activeAttempt;

    public synchronized void arm(boolean enabled) {
        armed = enabled;
        if (!enabled) clear();
    }

    /** Call immediately before the synchronous vanilla startConnecting call. */
    public synchronized String requestReconnect(String server) {
        requestedToken = null;
        activeAttempt = null;
        expectedServer = null;
        if (!armed) return null;
        expectedServer = normalize(server);
        requestedToken = UUID.randomUUID().toString();
        return requestedToken;
    }

    /** Every vanilla connection start must pass through here; manual starts pass a null token. */
    public synchronized void connectionStarted(Object attempt, String server, String apiToken) {
        if (armed && attempt != null && requestedToken != null && requestedToken.equals(apiToken)
            && expectedServer.equals(normalize(server))) {
            activeAttempt = attempt;
            requestedToken = null;
        } else clear();
    }

    /** Consume only the matching attempt. A late completion cannot consume a newer grant. */
    public synchronized boolean joined(Object attempt, String server) {
        if (attempt == null || attempt != activeAttempt) return false;
        boolean resume = armed && expectedServer != null && expectedServer.equals(normalize(server));
        clear();
        return resume;
    }

    public synchronized void failed(Object attempt) {
        if (attempt != null && attempt == activeAttempt) clear();
    }

    /** Clear a request if vanilla rejected or threw before constructing a connection attempt. */
    public synchronized void cancelRequest(String token) {
        if (token != null && token.equals(requestedToken)) clear();
    }

    public synchronized void died() { clear(); }

    private void clear() {
        armed = false;
        requestedToken = null;
        expectedServer = null;
        activeAttempt = null;
    }

    private static String normalize(String server) {
        if (server == null || server.isBlank()) throw new IllegalArgumentException("Server address is required");
        return server.strip().toLowerCase(Locale.ROOT);
    }
}
