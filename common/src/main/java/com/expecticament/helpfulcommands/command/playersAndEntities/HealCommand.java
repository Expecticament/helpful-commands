package com.expecticament.helpfulcommands.command.playersAndEntities;

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
    public HealCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("entities", EntityArgument.entities())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HEAL_OTHERS))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getEntities(ctx, "entities"), 0))
                        .then(Commands.argument("hearts", FloatArgumentType.floatArg(0))
                                .executes(ctx -> executeOther(ctx, EntityArgument.getEntities(ctx, "entities"), FloatArgumentType.getFloat(ctx, "hearts")))

                        )
                )
                .then(Commands.argument("hearts", FloatArgumentType.floatArg(0))
                        .requires(src -> !PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HEAL_OTHERS))
                        .executes(ctx -> executeSelf(ctx, FloatArgumentType.getFloat(ctx, "hearts")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_HEAL);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeSelf(ctx, 0);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, float hearts) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (!heal(sourcePlayer, hearts)) {
            return 0;
        }

        componentBuilder.appendTranslatable("commands.helpfulcommands.heal.self");
        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities, float hearts) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && entities.size() == 1 && entities.contains(sourcePlayer)) {
            return executeSelf(ctx, hearts);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        List<? extends Entity> affected = entities.stream()
                .filter(entity -> heal(entity, hearts))
                .peek(entity -> {
                    if (entity.isAlwaysTicking() && commandFeedback) {
                        ServerPlayer player = (ServerPlayer) entity;
                        if (player != sourcePlayer) {
                            ComponentBuilder componentBuilder = new ComponentBuilder(player);
                            componentBuilder.appendTranslatable("commands.helpfulcommands.heal.affected").setStyle(textStyles.getAffectedPositive());
                            player.sendSystemMessage(componentBuilder.build());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingUtil.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingUtil.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpfulcommands.heal.other.multiple"));
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpfulcommands.heal.other", affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

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
