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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CMD_gm extends HelpfulCommandsCommand {

    public CMD_gm(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("a")
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, GameType.ADVENTURE, EntityArgument.getPlayers(ctx, "players")))
                        )
                        .executes(ctx -> execute(ctx, GameType.ADVENTURE))
                )
                .then(Commands.literal("c")
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, GameType.CREATIVE, EntityArgument.getPlayers(ctx, "players")))
                        )
                        .executes(ctx -> execute(ctx, GameType.CREATIVE))
                )
                .then(Commands.literal("s")
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, GameType.SURVIVAL, EntityArgument.getPlayers(ctx, "players")))
                        )
                        .executes(ctx -> execute(ctx, GameType.SURVIVAL))
                )
                .then(Commands.literal("sp")
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, GameType.SPECTATOR, EntityArgument.getPlayers(ctx, "players")))
                        )
                        .executes(ctx -> execute(ctx, GameType.SPECTATOR))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx, GameType gameType) throws CommandSyntaxException {
        return execute(ctx, gameType, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, GameType gameType, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<ServerPlayer> targets = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            changeGameModeAndSendMessage(sourcePlayer, src, gameType, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        List<ServerPlayer> affected = targets.stream()
                .filter(p -> changeGameModeAndSendMessage(p, src, gameType, textStyles, commandFeedback, p == sourcePlayer, false))
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        String gameModeTrKey = "gameMode." + gameType.getName();
        Component gameModeComponent = Component.translatable(gameModeTrKey).setStyle(textStyles.getPrimary());

        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
        if (affected.size() == 1) {
            textBuilder.setStyle(textStyles.getSuccess());
            textBuilder
                    .appendTranslatable("commands.helpful_commands.gm.output.success.other.part1")
                    .appendWhitespace()
                    .appendComponent(StylingHelper.getAffectedEntityNameText(affected.getFirst()))
                    .appendTranslatable("commands.helpful_commands.gm.output.success.other.part2")
                    .appendWhitespace()
                    .appendComponent(gameModeComponent);
        } else {
            textBuilder.setStyle(textStyles.getSuccess());
            textBuilder
                    .appendTranslatable("commands.helpful_commands.gm.output.success.others.part1")
                    .appendWhitespace()
                    .appendComponent(gameModeComponent)
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.gm.output.success.others.part2")
                    .appendWhitespace()
                    .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.gm.output.success.others.part3");
        }

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean changeGameModeAndSendMessage(ServerPlayer player, CommandSourceStack commandSourceStack, GameType gameType, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        if (player.setGameMode(gameType)) {
            if (commandFeedback) {
                String gameModeTrKey = "gameMode." + gameType.getName();
                Component gameModeComponent = Component.translatable(gameModeTrKey).setStyle(textStyles.getPrimary());
                if (isSource) {
                    if (feedbackSource) {
                        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.gm.output.success.self")
                                .appendWhitespace()
                                .appendComponent(gameModeComponent);
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                    textBuilder.setStyle(textStyles.getAffectedNeutral());
                    textBuilder
                            .appendTranslatable("commands.helpful_commands.gm.output.success.affected")
                            .appendWhitespace()
                            .appendComponent(gameModeComponent);
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return true;
        }

        return false;
    }
}
