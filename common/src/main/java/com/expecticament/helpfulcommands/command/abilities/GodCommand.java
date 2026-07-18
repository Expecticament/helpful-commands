package com.expecticament.helpfulcommands.command.abilities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.manager.translation.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.util.GameRulesUtil;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.StylingUtil;
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
    public GodCommand(ModCommandManager.ModCommand modCommand) {
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
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GOD_OTHERS))
                                .executes(ctx -> executeOther(ctx, BoolArgumentType.getBool(ctx, "state"), EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_GOD);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeSelf(ctx, null);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Boolean state) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (!toggleInvulnerability(sourcePlayer, state)) {
            return 0;
        }

        boolean invulnerable = sourcePlayer.getAbilities().invulnerable;
        componentBuilder.appendTranslatable("commands.helpfulcommands.god.self", Component.literal(TranslationManager.translate(src, "commands.helpfulcommands.god." + String.valueOf(invulnerable).toLowerCase())));
        componentBuilder.setStyle(invulnerable ? textStyles.getAffectedPositive() : textStyles.getAffectedNegative());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Boolean state, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx, state);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        List<ServerPlayer> affected = players.stream()
                .filter(player -> toggleInvulnerability(player, state))
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            ComponentBuilder componentBuilder = new ComponentBuilder(player);
                            componentBuilder.appendTranslatable("commands.helpfulcommands.god.affected", Component.literal(TranslationManager.translate(src, "commands.helpfulcommands.god." + String.valueOf(state).toLowerCase()))).setStyle(textStyles.getAffectedNeutral());
                            player.sendSystemMessage(componentBuilder.build());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingUtil.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingUtil.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpfulcommands.god.other.multiple"));
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpfulcommands.god.other", Component.literal(TranslationManager.translate(src, "commands.helpfulcommands.god." + String.valueOf(state).toLowerCase())), affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

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
