package com.expecticament.helpful_commands.command.world;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.manager.ConfigManager;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

public class FireballCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType POWER_CONFIG_VALUE_EXCEEDED = new Dynamic2CommandExceptionType((src, maxPower) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.fireball.power_config_value_exceeded", Component.literal(String.valueOf(maxPower)).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );

    public FireballCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::execute)
                .then(Commands.argument("power", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "power")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_FIREBALL);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, 0);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, int power) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer player = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int maxPower = PermissionsUtil.getMetaOrElseConfigValue(src, ConfigManager.CONFIG_FIELD.FIREBALL_POWER_LIMIT);
        if (power < 1) {
            power = Math.min(5, maxPower);
        } else if (power > maxPower) {
            throw POWER_CONFIG_VALUE_EXCEEDED.create(src, maxPower);
        }

        ServerLevel level = player.level();

        Vec3 viewVector = player.getViewVector(1).normalize();
        Vec3 spawnPos = player.position().add(viewVector.multiply(new Vec3(2, 2, 2)));

        LargeFireball fireball = new LargeFireball(level, player, viewVector, power);
        fireball.snapTo(spawnPos.x, spawnPos.y + 1.5, spawnPos.z, player.getYRot(), player.getXRot());

        level.addFreshEntity(fireball);

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.fireball");
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
