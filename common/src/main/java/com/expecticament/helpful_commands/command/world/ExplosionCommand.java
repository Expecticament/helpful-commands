package com.expecticament.helpful_commands.command.world;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ConfigManager;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ExplosionCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType POWER_CONFIG_VALUE_EXCEEDED = new Dynamic2CommandExceptionType((src, maxPower) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.explosion.power_config_value_exceeded", Component.literal(String.valueOf(maxPower)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );

    public ExplosionCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_EXPLOSION_POSITION))
                        .then(Commands.argument("power", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position"), IntegerArgumentType.getInteger(ctx, "power"), true))
                                .then(Commands.argument("destroy_blocks", BoolArgumentType.bool())
                                        .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position"), IntegerArgumentType.getInteger(ctx, "power"), BoolArgumentType.getBool(ctx, "destroy_blocks")))
                                )
                        )
                )
                .then(Commands.argument("entity", EntityArgument.entity())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_EXPLOSION_ENTITY))
                        .then(Commands.argument("power", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeTarget(ctx, EntityArgument.getEntity(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "power"), true))
                                .then(Commands.argument("destroy_blocks", BoolArgumentType.bool())
                                        .executes(ctx -> executeTarget(ctx, EntityArgument.getEntity(ctx, "entity"), IntegerArgumentType.getInteger(ctx, "power"), BoolArgumentType.getBool(ctx, "destroy_blocks")))
                                )
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_EXPLOSION);
    }

    private int executePosition(CommandContext<CommandSourceStack> ctx, Vec3 position, Integer power, boolean destroyBlocks) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        int validatedPower = validatePowerArgument(src, power);

        ServerLevel serverLevel = src.getLevel();
        serverLevel.explode(null, position.x(), position.y(), position.z(), validatedPower, destroyBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.setStyle(textStyles.getSuccess());
        componentBuilder.appendTranslatable("commands.helpful_commands.explosion", StylingUtil.getLocationText(position, ServerLevelUtil.getLevelLocation(serverLevel)));

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeTarget(CommandContext<CommandSourceStack> ctx, Entity entity, Integer power, boolean destroyBlocks) throws CommandSyntaxException {
        return executePosition(ctx, entity.position(), power, destroyBlocks);
    }

    private int validatePowerArgument(CommandSourceStack source, Integer power) throws CommandSyntaxException {
        int powerLimit = PermissionsUtil.getMetaOrElseConfigValue(source, ConfigManager.CONFIG_FIELD.EXPLOSION_POWER_LIMIT);
        if (power == null) {
            power = powerLimit;
        } else if (power > powerLimit) {
            throw POWER_CONFIG_VALUE_EXCEEDED.create(source, powerLimit);
        }

        return power;
    }
}
