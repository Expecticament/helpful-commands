package com.expecticament.helpful_commands.suggestionProvider;

import com.expecticament.helpful_commands.manager.WarpManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WarpNameSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (Map.Entry<String, WarpManager.Warp> entry : WarpManager.getWarps().entrySet()) {
            String warpName = entry.getKey();
            if (warpName != null && !warpName.isBlank()) {
                builder.suggest(warpName);
            }
        }
        return builder.buildFuture();
    }
}
