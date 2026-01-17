package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
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
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;

public class ExplosionCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType POWER_CONFIG_VALUE_EXCEEDED = new Dynamic2CommandExceptionType((src, maxPower) ->
            new TranslationManager.TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.explosion.powerConfigValueExceeded", Component.literal(String.valueOf(maxPower)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
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
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_EXPLOSION_POSITION))
                        .then(Commands.argument("power", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position"), IntegerArgumentType.getInteger(ctx, "power"), true))
                                .then(Commands.argument("destroy_blocks", BoolArgumentType.bool())
                                        .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position"), IntegerArgumentType.getInteger(ctx, "power"), BoolArgumentType.getBool(ctx, "destroy_blocks")))
                                )
                        )
                )
                .then(Commands.argument("entity", EntityArgument.entity())
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_EXPLOSION_ENTITY))
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
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_EXPLOSION);
    }

    private int executePosition(CommandContext<CommandSourceStack> ctx, Vec3 position, Integer power, boolean destroyBlocks) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        int validatedPower = validatePowerArgument(src, power);

        ServerLevel serverLevel = src.getLevel();
        serverLevel.explode(null, position.x(), position.y(), position.z(), validatedPower, destroyBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder.appendTranslatable("commands.helpful_commands.explosion", StylingHelper.getLocationText(position, ServerLevelHelper.getLevelLocation(serverLevel)));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeTarget(CommandContext<CommandSourceStack> ctx, Entity entity, Integer power, boolean destroyBlocks) throws CommandSyntaxException {
        return executePosition(ctx, entity.position(), power, destroyBlocks);
    }

    private int validatePowerArgument(CommandSourceStack source, Integer power) throws CommandSyntaxException {
        int powerLimit = PermissionHelper.getMetaOrElseConfigValue(source, ConfigManager.CONFIG_FIELD.EXPLOSION_POWER_LIMIT);
        if (power == null) {
            power = powerLimit;
        } else if (power > powerLimit) {
            throw POWER_CONFIG_VALUE_EXCEEDED.create(source, powerLimit);
        }

        return power;
    }
}
