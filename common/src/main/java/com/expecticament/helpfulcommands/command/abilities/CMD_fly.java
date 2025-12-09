package com.expecticament.helpfulcommands.command.abilities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CMD_fly extends HelpfulCommandsCommand {

    public CMD_fly(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("state", BoolArgumentType.bool())
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, BoolArgumentType.getBool(ctx, "state"), EntityArgument.getPlayers(ctx, "players")))
                        )
                        .executes(ctx -> execute(ctx, BoolArgumentType.getBool(ctx, "state")))
                )
                .executes(this::execute)
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, null, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Boolean state) throws CommandSyntaxException {
        return execute(ctx, state, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Boolean state, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<ServerPlayer> targets = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            toggleFlyingAndSendMessage(sourcePlayer, src, state, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        List<ServerPlayer> affected = targets.stream()
                .filter(p -> toggleFlyingAndSendMessage(p, src, state, textStyles, commandFeedback, p == sourcePlayer, false))
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        Component stateText = Component.literal(TranslationManager.translate(src, "commands.helpful_commands.fly.output.success." + String.valueOf(state).toLowerCase()));
        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpful_commands.fly.output.success.multiple"));
        }
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.fly.output.success.other", stateText, affectedText);
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean toggleFlyingAndSendMessage(ServerPlayer player, CommandSourceStack commandSourceStack, Boolean state, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        Abilities abilities = player.getAbilities();
        boolean finalState = state != null ? state : !abilities.mayfly;

        if (finalState != abilities.mayfly) {
            abilities.mayfly = finalState;
            if (!finalState) {
                abilities.flying = false;
            }
            player.onUpdateAbilities();

            if (commandFeedback) {
                TextBuilder textBuilder = new TextBuilder(player);
                textBuilder.setStyle(isSource ? (finalState ? textStyles.getAffectedPositive() : textStyles.getAffectedNeutral()) : textStyles.getAffectedNeutral());
                textBuilder.appendTranslatable("commands.helpful_commands.fly.output.success.affected", Component.literal(TranslationManager.translate(player, "commands.helpful_commands.fly.output.success." + String.valueOf(finalState).toLowerCase())));

                if (isSource) {
                    if (feedbackSource) {
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return true;
        }

        return false;
    }
}
