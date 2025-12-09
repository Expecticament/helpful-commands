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
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CMD_ignite extends HelpfulCommandsCommand {

    public CMD_ignite(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("duration_seconds", FloatArgumentType.floatArg(1f))
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, FloatArgumentType.getFloat(ctx, "duration_seconds"), EntityArgument.getEntities(ctx, "entities")))
                        )
                        .executes(ctx -> execute(ctx, FloatArgumentType.getFloat(ctx, "duration_seconds")))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx, float duration) throws CommandSyntaxException {
        return execute(ctx, duration, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, float duration, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<Entity> targets = new ArrayList<>(Objects.requireNonNullElse(entities, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            igniteAndSendMessage(sourcePlayer, src, duration, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        List<Entity> affected = targets.stream()
                .filter(p -> igniteAndSendMessage(p, src, duration, textStyles, commandFeedback, p == sourcePlayer, false))
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }

        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder
                .appendTranslatable("commands.helpful_commands.ignite.output.success.other.part1")
                .appendWhitespace();

        if (affected.size() == 1) {
            textBuilder.appendComponent(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            textBuilder
                    .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.ignite.output.success.other.part2.multiple");
        }

        Component durationText = Component.literal(String.valueOf(duration)).setStyle(textStyles.getPrimary());
        String durationTrKey = "commands.helpful_commands.ignite.output.success.duration.part2." + (duration == 1 ? "singular" : "plural");

        textBuilder
                .appendWhitespace()
                .appendTranslatable("commands.helpful_commands.ignite.output.success.other.part3")
                .appendWhitespace()
                .appendTranslatable("commands.helpful_commands.ignite.output.success.duration.part1")
                .appendWhitespace()
                .appendComponent(durationText)
                .appendWhitespace()
                .appendTranslatable(durationTrKey);

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean igniteAndSendMessage(Entity entity, CommandSourceStack commandSourceStack, float duration, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        if (entity.isAlive() && !entity.fireImmune()) {
            ServerPlayer player = null;
            if (entity.isAlwaysTicking()) {
                player = (ServerPlayer) entity;
            }

            if (player != null && player.getAbilities().invulnerable) {
                return false;
            }

            entity.igniteForSeconds(duration);

            if (commandFeedback && player != null) {
                if (isSource) {
                    if (feedbackSource) {
                        Component durationText = Component.literal(String.valueOf(duration)).setStyle(textStyles.getPrimary());
                        String durationTrKey = "commands.helpful_commands.ignite.output.success.duration.part2." + (duration == 1 ? "singular" : "plural");

                        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedNeutral());
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.ignite.output.success.self")
                                .appendWhitespace()
                                .appendTranslatable("commands.helpful_commands.ignite.output.success.duration.part1")
                                .appendWhitespace()
                                .appendComponent(durationText)
                                .appendWhitespace()
                                .appendTranslatable(durationTrKey);
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                    textBuilder.setStyle(textStyles.getAffectedNeutral());
                    textBuilder.appendTranslatable("commands.helpful_commands.ignite.output.success.affected");
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return true;
        }

        return false;
    }
}
