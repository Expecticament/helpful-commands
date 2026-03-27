package com.expecticament.helpfulcommands.command.abilities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
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

public class FlyCommand extends HelpfulCommandsCommand {
    public FlyCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("state", BoolArgumentType.bool())
                        .executes(ctx -> executeSelf(ctx, BoolArgumentType.getBool(ctx, "state")))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_FLY_OTHERS))
                                .executes(ctx -> executeOther(ctx, BoolArgumentType.getBool(ctx, "state"), EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_FLY);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeSelf(ctx, null);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Boolean state) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);

        if (!toggleFlying(sourcePlayer, state)) {
            return 0;
        }

        boolean mayFly = sourcePlayer.getAbilities().mayfly;
        textBuilder.appendTranslatable("commands.helpful_commands.fly.self", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.fly." + String.valueOf(mayFly).toLowerCase())));
        textBuilder.setStyle(mayFly ? textStyles.getAffectedPositive() : textStyles.getAffectedNegative());

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
                .filter(player -> toggleFlying(player, state))
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            TextBuilder textBuilder = new TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.fly.affected", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.fly." + String.valueOf(state).toLowerCase()))).setStyle(textStyles.getAffectedNeutral());
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
                    .append(TranslationManager.translate(src, "commands.helpful_commands.fly.other.multiple"));
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.fly.other", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.fly." + String.valueOf(state).toLowerCase())), affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean toggleFlying(ServerPlayer player, Boolean state) {
        Abilities abilities = player.getAbilities();
        boolean finalState = state != null ? state : !abilities.mayfly;

        if (finalState == abilities.mayfly) {
            return false;
        }

        abilities.mayfly = finalState;
        if (!finalState) {
            abilities.flying = false;
        }

        player.onUpdateAbilities();

        return true;
    }
}
