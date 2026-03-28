package com.expecticament.helpful_commands.suggestionProvider;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

public class HelpfulCommandsCommandSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (HelpfulCommandsCommand command : ModCommandManager.getCommandList()) {
            ModCommandManager.ModCommand modCommand = command.getModCommand();
            if (modCommand.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                continue;
            }
            builder.suggest(modCommand.getName());
        }
        return builder.buildFuture();
    }
}
