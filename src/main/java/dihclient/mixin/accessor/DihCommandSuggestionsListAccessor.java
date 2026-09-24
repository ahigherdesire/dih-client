package dihclient.mixin.accessor;

import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface DihCommandSuggestionsListAccessor {
    @Accessor("originalContents")
    String dih$getOriginalContents();

    @Accessor("suggestionList")
    List<Suggestion> dih$getSuggestionList();
}
