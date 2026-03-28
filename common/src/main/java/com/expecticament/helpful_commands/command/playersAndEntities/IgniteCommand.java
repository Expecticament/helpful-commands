package com.expecticament.helpful_commands.command.playersAndEntities;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.helper.GameRulesHelper;
import com.expecticament.helpful_commands.helper.PermissionHelper;
import com.expecticament.helpful_commands.helper.StylingHelper;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
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

import java.util.Collection;
import java.util.List;

public class IgniteCommand extends HelpfulCommandsCommand {
    public IgniteCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("duration_seconds", FloatArgumentType.floatArg(1f))
                        .executes(ctx -> executeSelf(ctx, FloatArgumentType.getFloat(ctx, "duration_seconds")))
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_IGNITE_OTHERS))
                                .executes(ctx -> executeOther(ctx, FloatArgumentType.getFloat(ctx, "duration_seconds"), EntityArgument.getEntities(ctx, "entities")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_IGNITE);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, float duration) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);

        if (!ignite(sourcePlayer, duration)) {
            return 0;
        }

        textBuilder.appendTranslatable("commands.helpful_commands.ignite.self", Component.literal(String.valueOf(duration)).setStyle(textStyles.getPrimary()), Component.literal(TranslationManager.translate(src, duration == 1 ? "commands.helpful_commands.ignite.second" : "commands.helpful_commands.ignite.seconds")));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, float duration, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && entities.size() == 1 && entities.contains(sourcePlayer)) {
            return executeSelf(ctx, duration);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        List<? extends Entity> affected = entities.stream()
                .filter(entity -> ignite(entity, duration))
                .peek(entity -> {
                    if (entity.isAlwaysTicking() && commandFeedback) {
                        ServerPlayer player = (ServerPlayer) entity;
                        if (player != sourcePlayer) {
                            TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.ignite.affected").setStyle(textStyles.getAffectedNegative());
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
                    .append(TranslationManager.translate(src, "commands.helpful_commands.ignite.other.multiple"));
        }

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.ignite.other", affectedText, Component.literal(String.valueOf(duration)).setStyle(textStyles.getPrimary()), Component.literal(TranslationManager.translate(src, duration == 1 ? "commands.helpful_commands.ignite.second" : "commands.helpful_commands.ignite.seconds"))).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean ignite(Entity entity, float duration) {
        if (!(entity.isAlive() && !entity.fireImmune())) {
            return false;
        }

        if (entity.isAlwaysTicking() && ((ServerPlayer) entity).getAbilities().invulnerable) {
            return false;
        }

        entity.igniteForSeconds(duration);

        return true;
    }
}
