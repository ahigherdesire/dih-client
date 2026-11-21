package dihclient.script;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptReconnectGuardTest {
    @Test void onlyTheArmedApiAttemptCanResumeOnce() {
        var guard = new ScriptReconnectGuard();
        Object attempt = new Object();
        guard.arm(true);
        String token = guard.requestReconnect("example.org:25565");
        assertNotNull(token);
        guard.connectionStarted(attempt, "example.org:25565", token);
        assertTrue(guard.joined(attempt, "example.org:25565"));
        assertFalse(guard.joined(attempt, "example.org:25565"));
    }

    @Test void aManualAttemptToTheSameServerCannotConsumeStalePermission() {
        var guard = new ScriptReconnectGuard();
        guard.arm(true);
        String token = guard.requestReconnect("example.org");
        Object apiAttempt = new Object();
        guard.connectionStarted(apiAttempt, "example.org", token);
        Object manual = new Object();
        guard.connectionStarted(manual, "example.org", null);
        assertFalse(guard.joined(manual, "example.org"));
        assertFalse(guard.joined(apiAttempt, "example.org"));
    }

    @Test void aFailedOrCancelledAttemptCannotResumeOnALaterJoin() {
        var guard = new ScriptReconnectGuard();
        guard.arm(true);
        Object attempt = new Object();
        guard.connectionStarted(attempt, "example.org", guard.requestReconnect("example.org"));
        guard.failed(attempt);
        assertFalse(guard.joined(attempt, "example.org"));
    }

    @Test void anOldAttemptsFailureCannotCancelANewerExplicitAttempt() {
        var guard = new ScriptReconnectGuard();
        Object old = new Object(), current = new Object();
        guard.arm(true);
        guard.connectionStarted(old, "example.org", guard.requestReconnect("example.org"));
        guard.arm(true);
        guard.connectionStarted(current, "example.org", guard.requestReconnect("example.org"));
        guard.failed(old);
        assertTrue(guard.joined(current, "example.org"));
    }

    @Test void deathAndDisarmingInvalidateAlreadyStartedAttempts() {
        for (boolean death : new boolean[] {false, true}) {
            var guard = new ScriptReconnectGuard();
            Object attempt = new Object();
            guard.arm(true);
            guard.connectionStarted(attempt, "example.org", guard.requestReconnect("example.org"));
            if (death) guard.died(); else guard.arm(false);
            assertFalse(guard.joined(attempt, "example.org"));
        }
    }

    @Test void wrongServerWrongIdentityAndReusedTokensCannotResume() {
        var guard = new ScriptReconnectGuard();
        guard.arm(true);
        String token = guard.requestReconnect("expected.org");
        Object attempt = new Object();
        guard.connectionStarted(attempt, "other.org", token);
        assertFalse(guard.joined(attempt, "other.org"));
        guard.arm(true);
        token = guard.requestReconnect("expected.org");
        guard.connectionStarted(attempt, "expected.org", token);
        guard.connectionStarted(new Object(), "expected.org", token);
        assertFalse(guard.joined(attempt, "expected.org"));
    }
}
