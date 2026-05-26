package com.expecticament.helpful_commands.command.playersAndEntities;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CoordsCommand extends HelpfulCommandsCommand {
    public CoordsCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("broadcast")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_COORDS_BROADCAST))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_COORDS_BROADCAST_OTHER))
                                .executes(ctx -> broadcast(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::broadcast)
                )
                .then(Commands.literal("share")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_COORDS_SHARE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> share(ctx, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("query")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_COORDS_QUERY))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_COORDS_QUERY_OTHER))
                                .executes(ctx -> query(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::execute)
                )
                .executes(this::execute)
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_COORDS);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        String dimensionKey = ServerLevelUtil.getLevelLocation(sourcePlayer.level());

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.coords.self", StylingUtil.getLocationText(sourcePlayer.position(), dimensionKey));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int broadcast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return broadcast(ctx, null);
    }

    private int broadcast(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == null && sourcePlayer == null) {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }

        if (player == null) {
            player = sourcePlayer;
        }

        String dimensionKey = ServerLevelUtil.getLevelLocation(player.level());

        List<ServerPlayer> playerList = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());

        if (playerList.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        for (ServerPlayer plr : playerList) {
            TextBuilder textBuilder = new TextBuilder(plr);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingUtil.getAffectedEntityNameText(player), StylingUtil.getLocationText(player.position(), dimensionKey));
            plr.sendSystemMessage(textBuilder.getComponent());
        }

        if (sourcePlayer == null) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingUtil.getAffectedEntityNameText(player), StylingUtil.getLocationText(player.position(), dimensionKey));
            src.sendSystemMessage(textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int share(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        List<ServerPlayer> playerList = new ArrayList<>(players);
        playerList.remove(sourcePlayer);

        if (playerList.isEmpty()) {
            throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        String dimensionKey = ServerLevelUtil.getLevelLocation(sourcePlayer.level());
        for (ServerPlayer player : playerList) {
            TextBuilder textBuilder = new TextBuilder(player);
            textBuilder.appendTranslatable("commands.helpful_commands.coords.share.affected", StylingUtil.getAffectedEntityNameText(sourcePlayer), StylingUtil.getLocationText(sourcePlayer.position(), dimensionKey));
            player.sendSystemMessage(textBuilder.getComponent());
        }

        TextBuilder textBuilder = new TextBuilder(sourcePlayer);
        textBuilder.setStyle(textStyles.getSuccess());

        MutableComponent affected = Component.empty();
        if (playerList.size() == 1) {
            affected.append(StylingUtil.getAffectedEntityNameText(playerList.getFirst()));
        } else {
            affected
                    .append(StylingUtil.getAffectedEntitiesNumberText(playerList))
                    .append(TranslationManager.translate(src, "commands.helpful_commands.coords.share.multiple"));
        }

        textBuilder.appendTranslatable("commands.helpful_commands.coords.share.other", affected);

        src.sendSuccess(textBuilder::getComponent, true);

        return playerList.size();
    }

    private int query(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == sourcePlayer) {
            return execute(ctx);
        }

        String dimensionKey = ServerLevelUtil.getLevelLocation(player.level());

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.coords.other", StylingUtil.getAffectedEntityNameText(player), StylingUtil.getLocationText(player.position(), dimensionKey));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
