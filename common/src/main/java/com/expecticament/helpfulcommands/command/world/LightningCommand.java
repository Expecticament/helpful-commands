package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public class LightningCommand extends HelpfulCommandsCommand {
    public LightningCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecute)
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position")))
                )
                .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(ctx -> executeTarget(ctx, EntityArgument.getEntity(ctx, "entity")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_LIGHTNING);
    }

    private int executePosition(CommandContext<CommandSourceStack> ctx, Vec3 position) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        ServerLevel serverLevel = src.getLevel();

        LightningBolt livingEntity = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
        livingEntity.setPos(position.x(), position.y(), position.z());
        serverLevel.addFreshEntity(livingEntity);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder.appendTranslatable("commands.helpful_commands.lightning", StylingHelper.getLocationText(position, ServerLevelHelper.getLevelLocation(serverLevel)));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
        return executePosition(ctx, entity.position());
    }
}
