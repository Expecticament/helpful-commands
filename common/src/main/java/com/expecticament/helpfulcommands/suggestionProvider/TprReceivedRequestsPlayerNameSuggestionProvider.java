package com.expecticament.helpfulcommands.suggestionProvider;

import com.expecticament.helpfulcommands.manager.TpRequestsManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.concurrent.CompletableFuture;

public class TprReceivedRequestsPlayerNameSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }

        PlayerList playerList = source.getServer().getPlayerList();
        for (TpRequestsManager.Request request : TpRequestsManager.getReceivedRequests(player)) {
            ServerPlayer from = playerList.getPlayer(request.getFrom());
            if (from == null) {
                continue;
            }
            builder.suggest(from.getName().getString());
        }

        return builder.buildFuture();
    }
}
