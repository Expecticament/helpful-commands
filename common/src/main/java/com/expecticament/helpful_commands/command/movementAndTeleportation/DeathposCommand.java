package com.expecticament.helpful_commands.command.movementAndTeleportation;

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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.Optional;

public class DeathposCommand extends HelpfulCommandsCommand {
    private static final DynamicCommandExceptionType NO_DEATH_POS = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.deathpos.error.no_death_pos.self").getComponent()
    );
    private static final Dynamic2CommandExceptionType NO_DEATH_POS_OTHER = new Dynamic2CommandExceptionType((src, otherPlayer) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.deathpos.error.no_death_pos.other", StylingUtil.getAffectedEntityNameText((ServerPlayer) otherPlayer)).getComponent()
    );

    public DeathposCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("tp")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DEATHPOS_TP))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DEATHPOS_TP_OTHER))
                                .executes(ctx -> teleport(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::teleport)
                )
                .then(Commands.literal("query")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DEATHPOS_QUERY))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DEATHPOS_QUERY_OTHER))
                                .executes(ctx -> query(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::query)
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_DEATHPOS);
    }

    private int query(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return query(ctx, null);
    }

    private int query(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == null) {
            if (sourcePlayer == null) {
                throw CommandSourceStack.ERROR_NOT_PLAYER.create();
            }
            player = sourcePlayer;
        }

        boolean self = player == sourcePlayer;

        Optional<GlobalPos> deathPos = player.getLastDeathLocation();

        if (deathPos.isEmpty()) {
            if (self) {
                throw NO_DEATH_POS.create(src);
            } else {
                throw NO_DEATH_POS_OTHER.create(src, player);
            }
        }

        GlobalPos globalPos = deathPos.get();
        Component deathPosComponent = StylingUtil.getLocationText(globalPos);

        TextBuilder textBuilder = new TextBuilder(src);

        if (self) {
            textBuilder.appendTranslatable("commands.helpful_commands.deathpos.query.self", deathPosComponent);
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.deathpos.query.other", StylingUtil.getAffectedEntityNameText(player), deathPosComponent);
        }

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleport(ctx, null);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        if (player == null) {
            if (sourcePlayer == null) {
                throw CommandSourceStack.ERROR_NOT_PLAYER.create();
            }
            player = sourcePlayer;
        }

        boolean self = player == sourcePlayer;

        Optional<GlobalPos> deathPos = player.getLastDeathLocation();

        if (deathPos.isEmpty()) {
            if (self) {
                throw NO_DEATH_POS.create(src);
            } else {
                throw NO_DEATH_POS_OTHER.create(src, player);
            }
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        GlobalPos globalPos = deathPos.get();
        String levelLocation = globalPos.dimension().identifier().toString();

        try {
            ServerLevel level = ServerLevelUtil.getLevel(levelLocation);
            BlockPos blockPos = globalPos.pos();

            sourcePlayer.teleportTo(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), Relative.DELTA, sourcePlayer.getYRot(), sourcePlayer.getXRot(), false);

            TextBuilder textBuilder = new TextBuilder(src);

            if (self) {
                textBuilder.appendTranslatable("commands.helpful_commands.deathpos.teleport.self");
            } else {
                textBuilder.appendTranslatable("commands.helpful_commands.deathpos.teleport.other", StylingUtil.getAffectedEntityNameText(player));
            }

            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);
        } catch (ServerLevelUtil.UnknownServerLevelException e) {
            throw UNKNOWN_DIMENSION.create(src, levelLocation);
        }

        return Command.SINGLE_SUCCESS;
    }
}
