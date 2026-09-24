package dihclient.gui.multi;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihMacroManager;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MultiChatCompletion {

    public record Result(int start, int length, List<String> entries) {
    }

    private static CacheEntry cached;

    private record CacheEntry(String value, int cursor, int commandRevision, int moduleRevision,
                              long macroRevision, AbstractContainerMenu menu, int menuRevision,
                              CompletableFuture<Suggestions> future, Result result) {
        private boolean matches(String nextValue, int nextCursor, int nextCommandRevision, int nextModuleRevision,
                                long nextMacroRevision, AbstractContainerMenu nextMenu, int nextMenuRevision) {
            return value.equals(nextValue)
                && cursor == nextCursor
                && commandRevision == nextCommandRevision
                && moduleRevision == nextModuleRevision
                && macroRevision == nextMacroRevision
                && menu == nextMenu
                && menuRevision == nextMenuRevision;
        }
    }

    private MultiChatCompletion() {
    }

    public static boolean isClientCommand(String value) {
        return DihCommands.isDihCommandMessage(value);
    }

    public static Result clientSuggestions(String value) {
        return clientSuggestions(value, value == null ? 0 : value.length());
    }

    public static Result clientSuggestions(String value, int requestedCursor) {
        try {
            int prefixLen = DihCommands.effectivePrefix().length();
            if (value == null || value.length() < prefixLen) return new Result(0, 0, List.of());
            int cursor = Math.max(prefixLen, Math.min(requestedCursor, value.length()));
            int commandRevision = DihCommands.revision();
            int moduleRevision = ModuleRegistry.revision();
            long macroRevision = DihMacroManager.get().getRevision();
            Minecraft minecraft = Minecraft.getInstance();
            AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
            int menuRevision = menu == null ? -1 : menu.getStateId();

            CacheEntry hit = cached;
            if (hit != null && hit.matches(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision)) {
                if (hit.result() != null) return hit.result();
                Suggestions completed = hit.future().getNow(null);
                if (completed == null) return null;
                Result result = toResult(completed);
                cached = new CacheEntry(value, cursor, commandRevision, moduleRevision, macroRevision,
                    menu, menuRevision, hit.future(), result);
                return result;
            }

            StringReader reader = new StringReader(value);
            reader.setCursor(prefixLen);
            ParseResults<DihCommandSource> parse = DihCommands.dispatcher().parse(reader, DihCommandSource.INSTANCE);
            CompletableFuture<Suggestions> future = DihCommands.dispatcher().getCompletionSuggestions(parse, cursor);
            Suggestions built = future.getNow(null);
            Result result = built == null ? null : toResult(built);
            cached = new CacheEntry(value, cursor, commandRevision, moduleRevision, macroRevision,
                menu, menuRevision, future, result);
            return result;
        } catch (RuntimeException error) {
            return new Result(0, 0, List.of());
        }
    }

    private static Result toResult(Suggestions built) {
        List<String> entries = new ArrayList<>(built.getList().size());
        for (Suggestion suggestion : built.getList()) entries.add(suggestion.getText());
        return new Result(built.getRange().getStart(), built.getRange().getLength(), List.copyOf(entries));
    }
}
