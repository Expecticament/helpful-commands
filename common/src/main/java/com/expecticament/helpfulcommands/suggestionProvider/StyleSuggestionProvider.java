package com.expecticament.helpfulcommands.suggestionProvider;

import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

public class StyleSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("default");
        for (HelpfulCommandsStyle style : StylingManager.getAllStyles()) {
            builder.suggest(style.getDisplayName());
        }
        return builder.buildFuture();
    }
}
