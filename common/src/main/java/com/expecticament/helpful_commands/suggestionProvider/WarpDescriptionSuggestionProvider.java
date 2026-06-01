package com.expecticament.helpful_commands.suggestionProvider;

import com.expecticament.helpful_commands.manager.warp.WarpException;
import com.expecticament.helpful_commands.manager.warp.WarpManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

public class WarpDescriptionSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            String currentDescription = WarpManager.getWarp(StringArgumentType.getString(context, "warp_name")).description;
            builder.suggest("\"%s\"".formatted(currentDescription));
        } catch (WarpException.DoesntExist ignored) {}
        return builder.buildFuture();
    }
}
