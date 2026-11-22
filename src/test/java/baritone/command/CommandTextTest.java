package baritone.command;

import baritone.api.command.CommandText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CommandTextTest {

    @AfterEach
    void resetFormatter() {
        CommandText.formatter(UnaryOperator.identity());
    }

    @Test
    void standaloneFormattingPreservesCommandReferencesAndOtherHashes() {
        String message = "Use #structure\n# comment\t[link](#section) color #aabbcc";
        assertSame(message, CommandText.format(message));
    }

    @Test
    void installedFormatterControlsCommandReferencesWithoutRewritingOtherHashes() {
        CommandText.formatter(text -> text.replace("#structure", ".structure"));
        assertEquals("Use .structure\n# comment\t[link](#section) color #aabbcc",
                CommandText.format("Use #structure\n# comment\t[link](#section) color #aabbcc"));
    }

    @Test
    void replacementUsesCurrentFormatterWithoutChainingPreviousCallbacks() {
        CommandText.formatter(text -> text.replace("#autosleep", ".autosleep"));
        CommandText.formatter(text -> text.replace("#autosleep", "!autosleep"));
        assertEquals("!autosleep", CommandText.format("#autosleep"));
        CommandText.formatter(UnaryOperator.identity());
        assertEquals("#autosleep", CommandText.format("#autosleep"));
    }

    @Test
    void nullCallbackIsRejectedWithoutDiscardingInstalledFormatter() {
        CommandText.formatter(text -> ".seedinput");
        assertThrows(NullPointerException.class, () -> CommandText.formatter(null));
        assertEquals(".seedinput", CommandText.format("#seedinput"));
    }

    @Test
    void otherThreadsUseReplacementFormatter() throws Exception {
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            CommandText.formatter(text -> text.replace("#structure", ".structure"));
            assertEquals(".structure", reader.submit(() -> CommandText.format("#structure")).get(5, TimeUnit.SECONDS));
            CommandText.formatter(text -> text.replace("#structure", "!structure"));
            assertEquals("!structure", reader.submit(() -> CommandText.format("#structure")).get(5, TimeUnit.SECONDS));
        } finally {
            reader.shutdownNow();
        }
    }
}
