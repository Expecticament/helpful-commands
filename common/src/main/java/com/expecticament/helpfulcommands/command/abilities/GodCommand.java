package com.expecticament.helpfulcommands.command.abilities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
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

import java.util.Collection;
import java.util.List;

public class GodCommand extends HelpfulCommandsCommand {

    public GodCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::executeSelf)
                .then(Commands.argument("state", BoolArgumentType.bool())
                        .executes(ctx -> executeSelf(ctx, BoolArgumentType.getBool(ctx, "state")))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> executeOther(ctx, BoolArgumentType.getBool(ctx, "state"), EntityArgument.getPlayers(ctx, "players")))
                                .requires(src -> canExecute(src, "other"))
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeSelf(ctx, null);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Boolean state) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);

        if (!toggleInvulnerability(sourcePlayer, state)) {
            return 0;
        }

        boolean invulnerable = sourcePlayer.getAbilities().invulnerable;
        textBuilder.appendTranslatable("commands.helpful_commands.god.self", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.god." + String.valueOf(invulnerable).toLowerCase())));
        textBuilder.setStyle(invulnerable ? textStyles.getAffectedPositive() : textStyles.getAffectedNegative());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Boolean state, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx, state);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        List<ServerPlayer> affected = players.stream()
                .filter(player -> toggleInvulnerability(player, state))
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            TextBuilder textBuilder = new TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.god.affected", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.god." + String.valueOf(state).toLowerCase()))).setStyle(textStyles.getAffectedNeutral());
                            player.sendSystemMessage(textBuilder.getComponent());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpful_commands.god.other.multiple"));
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.god.other", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.god." + String.valueOf(state).toLowerCase())), affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean toggleInvulnerability(ServerPlayer player, Boolean state) {
        Abilities abilities = player.getAbilities();
        boolean finalState = state != null ? state : !abilities.invulnerable;

        if (finalState == abilities.invulnerable) {
            return false;
        }

        abilities.invulnerable = finalState;

        player.onUpdateAbilities();

        return true;
    }
}
