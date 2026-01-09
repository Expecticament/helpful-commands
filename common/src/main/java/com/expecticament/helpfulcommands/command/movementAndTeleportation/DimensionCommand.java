package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
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
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class DimensionCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType ALREADY_IN_DIMENSION = new Dynamic2CommandExceptionType((src, dimensionName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.dimension.error.alreadyInDimension", Component.literal(dimensionName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );

    public DimensionCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_DIMENSION_OTHERS))
                                .executes(ctx -> executeOther(ctx, DimensionArgument.getDimension(ctx, "dimension"), EntityArgument.getEntities(ctx, "entities")))
                        )
                        .executes(ctx -> executeSelf(ctx, DimensionArgument.getDimension(ctx, "dimension")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_DIMENSION);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, ServerLevel serverLevel) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int result = switchDimension(sourcePlayer, serverLevel);
        Component dimensionText = getDimensionText(serverLevel, textStyles);

        TextBuilder textBuilder = new TextBuilder(src);

        if (result == 1) {
            throw ALREADY_IN_DIMENSION.create(src, ServerLevelHelper.getLevelLocation(serverLevel));
        }
        if (result == 2) {
            throw FAILED_TO_TELEPORT.create(src);
        }

        textBuilder.appendTranslatable("commands.helpful_commands.dimension.self", dimensionText);
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, ServerLevel serverLevel, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && entities.size() == 1 && entities.contains(sourcePlayer)) {
            return executeSelf(ctx, serverLevel);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        Component dimensionText = getDimensionText(serverLevel, textStyles);

        List<? extends Entity> affected = entities.stream()
                .filter(entity -> switchDimension(entity, serverLevel) == 0)
                .peek(entity -> {
                    if (entity.isAlwaysTicking() && commandFeedback) {
                        ServerPlayer player = (ServerPlayer) entity;
                        if (player != sourcePlayer) {
                            TextBuilder textBuilder = new TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.dimension.affected", dimensionText).setStyle(textStyles.getAffectedNeutral());
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
                    .append(TranslationManager.translate(src, "commands.helpful_commands.dimension.other.multiple"));
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.dimension.other", affectedText, dimensionText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private int switchDimension(Entity entity, ServerLevel serverLevel) {
        if (entity.level() == serverLevel) {
            return 1;
        }

        Vec3 pos = entity.position();
        return entity.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), new HashSet<>(), entity.getYRot(), entity.getXRot(), false) ? 0 : 2;
    }

    private Component getDimensionText(ServerLevel serverLevel, HelpfulCommandsStyle.TextStyles textStyles) {
        return Component.literal(ServerLevelHelper.getLevelLocation(serverLevel)).setStyle(textStyles.getPrimary());
    }
}
