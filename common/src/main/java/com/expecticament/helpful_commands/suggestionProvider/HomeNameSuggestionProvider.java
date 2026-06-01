package com.expecticament.helpful_commands.suggestionProvider;

import com.expecticament.helpful_commands.manager.home.Home;
import com.expecticament.helpful_commands.manager.home.HomeManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HomeNameSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            for (Map.Entry<String, Home> entry : HomeManager.getHomes(player).entrySet()) {
                String homeName = entry.getKey();
                if (homeName != null && !homeName.isBlank()) {
                    builder.suggest(homeName);
                }
            }
        }
        return builder.buildFuture();
    }
}
