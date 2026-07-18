package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.ServerLevelUtil;
import com.expecticament.helpfulcommands.util.StylingUtil;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public class LightningCommand extends HelpfulCommandsCommand {
    public LightningCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_LIGHTNING_POSITION))
                        .executes(ctx -> executePosition(ctx, Vec3Argument.getVec3(ctx, "position")))
                )
                .then(Commands.argument("entity", EntityArgument.entity())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_LIGHTNING_ENTITY))
                        .executes(ctx -> executeTarget(ctx, EntityArgument.getEntity(ctx, "entity")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_LIGHTNING);
    }

    private int executePosition(CommandContext<CommandSourceStack> ctx, Vec3 position) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        ServerLevel serverLevel = src.getLevel();


        LightningBolt livingEntity = new LightningBolt(EntityTypes.LIGHTNING_BOLT, serverLevel);
        livingEntity.setPos(position.x(), position.y(), position.z());
        serverLevel.addFreshEntity(livingEntity);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.setStyle(textStyles.getSuccess());
        componentBuilder.appendTranslatable("commands.helpfulcommands.lightning", StylingUtil.getLocationText(position, ServerLevelUtil.getLevelLocation(serverLevel)));

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
        return executePosition(ctx, entity.position());
    }
}
