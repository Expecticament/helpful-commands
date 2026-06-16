package com.expecticament.helpful_commands.command.movementAndTeleportation;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ConfigManager;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
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
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.jump.error.distance_config_value_exceeded", Component.literal(String.valueOf(maxDistance)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );

    public JumpCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
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
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_JUMP);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Double distance, boolean checkBlocks) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        Vec3 eyePos = sourcePlayer.getEyePosition();
        Vec3 look = sourcePlayer.getLookAngle();

        double distanceLimit = PermissionsUtil.getMetaOrElseConfigValue(src, ConfigManager.CONFIG_FIELD.JUMP_DISTANCE_LIMIT);
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
        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.setStyle(textStyles.getSuccess());
        componentBuilder.appendTranslatable("commands.helpful_commands.jump", StylingUtil.getPositionText(safePos));

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }
}
