package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.List;

public class HealCommand extends HelpfulCommandsCommand {
    public HealCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::executeSelf)
                .then(Commands.argument("entities", EntityArgument.entities())
                        .requires(src -> canExecute(src, "other"))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getEntities(ctx, "entities"), 0))
                        .then(Commands.argument("hearts", FloatArgumentType.floatArg(0))
                                .executes(ctx -> executeOther(ctx, EntityArgument.getEntities(ctx, "entities"), FloatArgumentType.getFloat(ctx, "hearts")))

                        )
                )
                .then(Commands.argument("hearts", FloatArgumentType.floatArg(0))
                        .requires(src -> !canExecute(src, "other"))
                        .executes(ctx -> executeSelf(ctx, FloatArgumentType.getFloat(ctx, "hearts")))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeSelf(ctx, 0);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, float hearts) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);

        if (!heal(sourcePlayer, hearts)) {
            return 0;
        }

        textBuilder.appendTranslatable("commands.helpful_commands.heal.self");
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, float hearts) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && entities.size() == 1 && entities.contains(sourcePlayer)) {
            return executeSelf(ctx, hearts);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        List<? extends Entity> affected = entities.stream()
                .filter(entity -> heal(entity, hearts))
                .peek(entity -> {
                    if (entity.isAlwaysTicking() && commandFeedback) {
                        ServerPlayer player = (ServerPlayer) entity;
                        if (player != sourcePlayer) {
                            TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.heal.affected").setStyle(textStyles.getAffectedPositive());
                            player.sendSystemMessage(textBuilder.getComponent());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpful_commands.heal.other.multiple"));
        }

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.heal.other", affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean heal(Entity entity, float hearts) {
        LivingEntity livingEntity = entity.asLivingEntity();
        if (!(livingEntity != null && livingEntity.isAlive() && livingEntity.getHealth() < livingEntity.getMaxHealth())) {
            return false;
        }

        if (hearts <= 0) {
            hearts = livingEntity.getMaxHealth() - livingEntity.getHealth();
        }

        livingEntity.heal(hearts * 2);

        return true;
    }
}
