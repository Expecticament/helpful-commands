package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.DeprecatedTextBuilder;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CMD_feed extends HelpfulCommandsCommand {

    public CMD_feed(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> canExecute(src, "other"))
                        .executes(ctx -> execute(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
                .executes(this::execute)
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<ServerPlayer> targets = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            feedAndSendMessage(sourcePlayer, src, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        List<ServerPlayer> affected = targets.stream()
                .filter(p -> feedAndSendMessage(p, src, textStyles, commandFeedback, p == sourcePlayer, false))
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder
                .appendTranslatable("commands.helpful_commands.feed.output.success.part1")
                .appendWhitespace();

        if (affected.size() == 1) {
            textBuilder.appendComponent(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            textBuilder
                    .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.feed.output.success.part2.others");
        }

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean feedAndSendMessage(ServerPlayer player, CommandSourceStack commandSourceStack, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        FoodData foodData = player.getFoodData();

        if (player.isAlive() && foodData.needsFood()) {
            foodData.setFoodLevel(20);
            foodData.setSaturation(5);

            if (commandFeedback) {
                if (isSource) {
                    if (feedbackSource) {
                        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.feed.output.success.part1")
                                .appendWhitespace()
                                .appendTranslatable("commands.helpful_commands.feed.output.success.part2.self");
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                    textBuilder.setStyle(textStyles.getAffectedPositive());
                    textBuilder.appendTranslatable("commands.helpful_commands.feed.output.success.affected");
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return true;
        }

        return false;
    }
}
