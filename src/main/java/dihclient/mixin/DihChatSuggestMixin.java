package dihclient.mixin;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihCommandSuggestionsListAccessor;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.PackHideState;
import dihclient.util.DihCompatManager;
import dihclient.util.DihMacroManager;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class DihChatSuggestMixin {
    @Shadow @Final private EditBox input;
    @Shadow @Nullable private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow @Nullable private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow @Nullable private CommandSuggestions.SuggestionsList suggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;

    @Unique private boolean dih$owningSuggestions;
    @Unique private String dih$cachedInput;
    @Unique private int dih$cachedCursor = -1;
    @Unique private int dih$cachedCommandRevision = Integer.MIN_VALUE;
    @Unique private int dih$cachedModuleRevision = Integer.MIN_VALUE;
    @Unique private long dih$cachedMacroRevision = Long.MIN_VALUE;
    @Unique private AbstractContainerMenu dih$cachedMenu;
    @Unique private int dih$cachedMenuRevision = Integer.MIN_VALUE;

    @Unique private static final int DIH_MAX_SUGGESTION_INPUT = 4096;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);
    @Shadow private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parseResults, Suggestions suggestions) {}

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void dih$updateDihCommandInfo(CallbackInfo ci) {
        try {
            dih$updateDihCommandInfoSafe(ci);
        } catch (Throwable t) {

            dihclient.DihClientAddon.LOG.warn("[Commands] suggestion update failed", t);
            try { dih$clearActiveSuggestions(); } catch (Throwable ignored) {  }
            ci.cancel();
        }
    }

    @Unique
    private void dih$updateDihCommandInfoSafe(CallbackInfo ci) {
        String value = input.getValue();
        if (dih$shouldSuppressMeteorSuggestions(value)) {
            dih$clearActiveSuggestions();
            ci.cancel();
            return;
        }

        if (!dih$isDihCommandInput(value)) {
            if (dih$owningSuggestions) dih$clearActiveSuggestions();
            return;
        }

        if (value.length() > DIH_MAX_SUGGESTION_INPUT) {

            if (dih$owningSuggestions) dih$clearActiveSuggestions();
            ci.cancel();
            return;
        }

        int prefixLen = DihCommands.effectivePrefix().length();
        StringReader reader = new StringReader(value);
        reader.setCursor(prefixLen);
        int cursor = Math.max(prefixLen, Math.min(input.getCursorPosition(), value.length()));
        int commandRevision = DihCommands.revision();
        int moduleRevision = ModuleRegistry.revision();
        long macroRevision = DihMacroManager.get().getRevision();
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
        int menuRevision = menu == null ? -1 : menu.getStateId();

        if (value.equals(dih$cachedInput)
                && cursor == dih$cachedCursor
                && commandRevision == dih$cachedCommandRevision
                && moduleRevision == dih$cachedModuleRevision
                && macroRevision == dih$cachedMacroRevision
                && menu == dih$cachedMenu
                && menuRevision == dih$cachedMenuRevision
                && (dih$owningSuggestions || pendingSuggestions != null)) {
            ci.cancel();
            return;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ParseResults<DihCommandSource> parse = DihCommands.dispatcher().parse(reader, DihCommandSource.INSTANCE);

        @SuppressWarnings("unchecked")
        ParseResults<ClientSuggestionProvider> widgetParse =
                (ParseResults<ClientSuggestionProvider>) (Object) parse;

        currentParse = widgetParse;
        currentParseIsCommand = true;
        currentParseIsMessage = false;
        commandUsage.clear();

        if (suggestions != null && keepSuggestions && dih$canKeepSuggestionList(value)) {
            dih$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);
            ci.cancel();
            return;
        }

        dih$clearActiveSuggestions();
        dih$rememberSuggestionKey(value, cursor, commandRevision, moduleRevision, macroRevision, menu, menuRevision);

        String inputSnapshot = value;
        CompletableFuture<Suggestions> future = DihCommands.dispatcher()
                .getCompletionSuggestions(parse, cursor);
        pendingSuggestions = future;
        future.thenAccept(result -> {
            try {
                if (pendingSuggestions != future) return;
                if (!inputSnapshot.equals(input.getValue())) return;
                if (!dih$isDihCommandInput(inputSnapshot)) return;
                dih$owningSuggestions = true;
                updateUsageInfo(widgetParse, result);
                if (pendingSuggestions == future && inputSnapshot.equals(input.getValue())) {
                    showSuggestions(false);
                }
            } catch (Throwable t) {
                dihclient.DihClientAddon.LOG.warn("[Commands] suggestion apply failed", t);
                try { dih$clearActiveSuggestions(); } catch (Throwable ignored) {  }
            }
        });
        ci.cancel();
    }

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void dih$hideLateMeteorSuggestionsInPanic(CallbackInfo ci) {
        if (dih$shouldSuppressMeteorSuggestions(input.getValue())) {
            dih$clearActiveSuggestions();
        }
    }

    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void dih$hideShownMeteorSuggestionsInPanic(boolean narrateFirstSuggestion, CallbackInfo ci) {
        if (dih$shouldSuppressMeteorSuggestions(input.getValue())) {
            dih$clearActiveSuggestions();
            ci.cancel();
        }
    }

    @Unique
    private static boolean dih$isDihCommandInput(String value) {
        return !DihCommands.commandsBlockedByPanic() && DihCommands.isDihCommandMessage(value);
    }

    @Unique
    private static boolean dih$shouldSuppressMeteorSuggestions(String value) {
        if (!PackHideState.isActive()) return false;
        if (!DihCompatManager.isMeteorAvailable()) return false;
        if (value == null) return false;

        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return false;

        String prefix = DihCompatManager.meteorCommandPrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    @Unique
    private boolean dih$canKeepSuggestionList(String currentInput) {
        if (suggestions == null || currentInput == null) return false;
        try {
            DihCommandSuggestionsListAccessor accessor =
                    (DihCommandSuggestionsListAccessor) suggestions;
            String original = accessor.dih$getOriginalContents();
            List<Suggestion> list = accessor.dih$getSuggestionList();
            if (original == null || list == null || list.isEmpty()) return false;
            for (Suggestion suggestion : list) {
                if (suggestion == null) continue;
                try {
                    if (currentInput.equals(suggestion.apply(original))) return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    @Unique
    private void dih$rememberSuggestionKey(String value, int cursor, int commandRevision, int moduleRevision,
                                               long macroRevision, AbstractContainerMenu menu, int menuRevision) {
        dih$cachedInput = value;
        dih$cachedCursor = cursor;
        dih$cachedCommandRevision = commandRevision;
        dih$cachedModuleRevision = moduleRevision;
        dih$cachedMacroRevision = macroRevision;
        dih$cachedMenu = menu;
        dih$cachedMenuRevision = menuRevision;
    }

    @Unique
    private void dih$clearActiveSuggestions() {
        input.setSuggestion(null);
        suggestions = null;
        pendingSuggestions = null;
        keepSuggestions = false;
        dih$owningSuggestions = false;
    }
}
