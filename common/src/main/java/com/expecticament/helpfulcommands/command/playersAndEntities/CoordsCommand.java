package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CoordsCommand extends HelpfulCommandsCommand {

    public CoordsCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("broadcast")
                        .requires(src -> canExecute(src, "broadcast", 2) || canExecute(src, "broadcast.other", 2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> canExecute(src, "broadcast.other", 2))
                                .executes(ctx -> broadcast(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::broadcast)
                )
                .then(Commands.literal("share")
                        .requires(src -> canExecute(src, "share"))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> share(ctx, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("query")
                        .requires(src -> canExecute(src, "query", 2) || canExecute(src, "query.other", 2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> canExecute(src, "query.other", 2))
                                .executes(ctx -> query(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::execute)
                )
                .executes(this::execute)
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "broadcast", 2) || canExecute(source, "broadcast.other", 2) || canExecute(source, "share") || canExecute(source,"query", 2) || canExecute(source,"query.other", 2);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        String dimensionKey = ServerLevelHelper.getLevelLocation(sourcePlayer.level());

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.coords.self", StylingHelper.getLocationText(sourcePlayer.position(), dimensionKey));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int broadcast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return broadcast(ctx, null);
    }

    private int broadcast(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == null && sourcePlayer == null) {
            throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
        }

        if (player == null) {
            player = sourcePlayer;
        }

        String dimensionKey = ServerLevelHelper.getLevelLocation(player.level());

        List<ServerPlayer> playerList = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());

        if (playerList.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        for (ServerPlayer plr : playerList) {
            TextBuilder textBuilder = new TextBuilder(plr);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingHelper.getAffectedEntityNameText(player), StylingHelper.getLocationText(player.position(), dimensionKey));
            plr.sendSystemMessage(textBuilder.getComponent());
        }

        if (sourcePlayer == null) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingHelper.getAffectedEntityNameText(player), StylingHelper.getLocationText(player.position(), dimensionKey));
            src.sendSystemMessage(textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int share(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        List<ServerPlayer> playerList = new ArrayList<>(players);
        playerList.remove(sourcePlayer);

        if (playerList.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        String dimensionKey = ServerLevelHelper.getLevelLocation(sourcePlayer.level());
        for (ServerPlayer player : playerList) {
            TextBuilder textBuilder = new TextBuilder(player);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.share.affected", StylingHelper.getAffectedEntityNameText(sourcePlayer), StylingHelper.getLocationText(sourcePlayer.position(), dimensionKey));
            player.sendSystemMessage(textBuilder.getComponent());
        }

        TextBuilder textBuilder = new TextBuilder(sourcePlayer);
        textBuilder.setStyle(textStyles.getSuccess());

        MutableComponent affected = Component.empty();
        if (playerList.size() == 1) {
            affected.append(StylingHelper.getAffectedEntityNameText(playerList.getFirst()));
        } else {
            affected
                    .append(StylingHelper.getAffectedEntitiesNumberText(playerList))
                    .append(TranslationManager.translate(src, "commands.helpful_commands.coords.share.multiple"));
        }

        textBuilder.appendTranslatable("commands.helpful_commands.coords.share.other", affected);

        src.sendSuccess(textBuilder::getComponent, true);

        return playerList.size();
    }

    private int query(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == sourcePlayer) {
            return execute(ctx);
        }

        String dimensionKey = ServerLevelHelper.getLevelLocation(player.level());

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingHelper.getAffectedEntityNameText(player), StylingHelper.getLocationText(player.position(), dimensionKey));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
