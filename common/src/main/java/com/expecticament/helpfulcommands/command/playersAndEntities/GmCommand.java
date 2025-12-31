package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
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
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;

public class GmCommand extends HelpfulCommandsCommand {
    public GmCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecute)
                .then(Commands.literal("a")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_A))
                        .executes(ctx -> executeSelf(ctx, GameType.ADVENTURE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.ADVENTURE, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("c")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_C))
                        .executes(ctx -> executeSelf(ctx, GameType.CREATIVE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.CREATIVE, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_S))
                .then(Commands.literal("s")
                        .executes(ctx -> executeSelf(ctx, GameType.SURVIVAL))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.SURVIVAL, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("sp")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_SP))
                        .executes(ctx -> executeSelf(ctx, GameType.SPECTATOR))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_GM_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.SPECTATOR, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_GM);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, GameType gameType) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);

        if (!sourcePlayer.setGameMode(gameType)) {
            return 0;
        }

        textBuilder.appendTranslatable("commands.helpful_commands.gm.self", Component.translatable("gameMode." + gameType.getName()).setStyle(textStyles.getPrimary()));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, GameType gameType, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx, gameType);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        Component gameModeNameComponent = Component.translatable("gameMode." + gameType.getName()).setStyle(textStyles.getPrimary());

        List<ServerPlayer> affected = players.stream()
                .filter(player -> player.setGameMode(gameType))
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.gm.affected", gameModeNameComponent).setStyle(textStyles.getAffectedNeutral());
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
                    .append(TranslationManager.translate(src, "commands.helpful_commands.gm.other.multiple"));
        }

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.gm.other", gameModeNameComponent, affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }
}
