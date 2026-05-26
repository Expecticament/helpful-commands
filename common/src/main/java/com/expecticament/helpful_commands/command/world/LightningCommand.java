package com.expecticament.helpful_commands.command.world;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
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

        LightningBolt livingEntity = new LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
        livingEntity.setPos(position.x(), position.y(), position.z());
        serverLevel.addFreshEntity(livingEntity);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder.appendTranslatable("commands.helpful_commands.lightning", StylingUtil.getLocationText(position, ServerLevelUtil.getLevelLocation(serverLevel)));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
        return executePosition(ctx, entity.position());
    }
}
