package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;

public class FireballCommand extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType POWER_CONFIG_VALUE_EXCEEDED = new SimpleCommandExceptionType(Component.empty());

    public FireballCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::execute)
                .then(Commands.argument("power", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "power")))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, 0);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, int power) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer player = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int maxPower = ConfigManager.readConfig().readField("fireballPowerLimit");
        if (power < 1) {
            power = Math.min(5, maxPower);
        } else if (power > maxPower) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.fireball.configValueExceeded", Component.literal(String.valueOf(maxPower)).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(POWER_CONFIG_VALUE_EXCEEDED, textBuilder.getComponent());
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
