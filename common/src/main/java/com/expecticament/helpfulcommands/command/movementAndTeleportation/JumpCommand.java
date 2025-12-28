package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class JumpCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType DISTANCE_CONFIG_VALUE_EXCEEDED = new Dynamic2CommandExceptionType((src, maxDistance) ->
            new TranslationManager.TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.jump.error.distanceConfigvalueExceeded", Component.literal(String.valueOf(maxDistance)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );

    public JumpCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.1))
                                .executes(ctx -> execute(ctx, DoubleArgumentType.getDouble(ctx, "distance"), false))
                                .then(Commands.argument("check_blocks", BoolArgumentType.bool())
                                        .executes(ctx -> execute(ctx, DoubleArgumentType.getDouble(ctx, "distance"), BoolArgumentType.getBool(ctx, "check_blocks")))
                                )
                        )
                .executes(ctx -> execute(ctx, null, true))
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Double distance, boolean checkBlocks) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        Vec3 eyePos = sourcePlayer.getEyePosition();
        Vec3 look = sourcePlayer.getLookAngle();

        double distanceLimit = PermissionHelper.getMetaOrElseConfigValue(src, ConfigManager.CONFIG_FIELD.JUMP_DISTANCE_LIMIT);
        if (distance == null) {
            distance = distanceLimit;
        } else if (distance > distanceLimit) {
            throw DISTANCE_CONFIG_VALUE_EXCEEDED.create(src, distanceLimit);
        }

        Vec3 endPos = eyePos.add(look.scale(distance));

        Vec3 targetPos;

        if (checkBlocks) {
            HitResult hit = sourcePlayer.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sourcePlayer));

            if (hit.getType() != HitResult.Type.MISS) {
                targetPos = hit.getLocation();
            } else {
                targetPos = endPos;
            }
        } else {
            targetPos = endPos;
        }

        Vec3 safePos = targetPos.subtract(look.scale(0.5));
        sourcePlayer.teleportTo(safePos.x, safePos.y, safePos.z);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder.appendTranslatable("commands.helpful_commands.jump", StylingHelper.getPositionText(safePos));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
