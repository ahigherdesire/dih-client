package dihclient.script;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptMeteorTest {
    public static class FakeModule {
        boolean active;
        int toggles;
        public boolean isActive() { return active; }
        public void toggle() { active = !active; toggles++; }
    }
    public static class RefusingModule {
        public boolean isActive() { return false; }
        public void toggle() { }
    }
    public static class FailingModule {
        public boolean isActive() { return false; }
        public void toggle() { throw new IllegalStateException("unavailable"); }
    }
    @Test void alreadyDisabledDoesNotToggle() {
        FakeModule module = new FakeModule();
        assertTrue(ScriptMeteor.setState(module, false));
        assertEquals(0, module.toggles);
    }
    @Test void reportsActualPostToggleState() {
        assertFalse(ScriptMeteor.setState(new RefusingModule(), true));
        assertFalse(ScriptMeteor.setState(new FailingModule(), true));
    }
    @Test void settingSameStateDoesNotToggleAgain() {
        FakeModule module = new FakeModule();
        assertTrue(ScriptMeteor.setState(module, true));
        assertTrue(ScriptMeteor.setState(module, true));
        assertEquals(1, module.toggles);
        assertTrue(ScriptMeteor.setState(module, false));
        assertFalse(module.active);
        assertEquals(2, module.toggles);
    }
    @Test void missingModuleReportsFailure() {
        assertFalse(ScriptMeteor.setState(null, true));
        assertFalse(ScriptMeteor.enabled("missing-provider", "auto-eat"));
        assertFalse(ScriptMeteor.setEnabled("missing-provider", "auto-eat", true));
    }
    @Test void failedToggleDoesNotReportSuccess() {
        assertFalse(ScriptMeteor.setState(new Object(), true));
    }
}
