package com.expecticament.helpful_commands.command.movementAndTeleportation;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.translation.TranslationManager;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;

public class SpawnCommand extends HelpfulCommandsCommand {
    private static final DynamicCommandExceptionType PLAYER_SPAWN_NOT_SET = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.spawn.error.player_spawn_not_set.self").build()
    );
    private static final Dynamic2CommandExceptionType TARGET_PLAYER_SPAWN_NOT_SET = new Dynamic2CommandExceptionType((src, otherPlayer) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.spawn.error.player_spawn_not_set.other", StylingUtil.getAffectedEntityNameText((ServerPlayer) otherPlayer)).build()
    );

    public SpawnCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("player")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_PLAYER))
                        .then(Commands.literal("query")
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_PLAYER_QUERY))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_PLAYER_QUERY_OTHER))
                                        .executes(ctx -> executePlayerQuery(ctx, EntityArgument.getPlayer(ctx, "player")))
                                )
                                .executes(ctx -> executePlayerQuery(ctx, null))
                        )
                        .then(Commands.literal("tp")
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_PLAYER_TP))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_PLAYER_TP_OTHER))
                                        .executes(ctx -> executePlayerTp(ctx, EntityArgument.getPlayer(ctx, "player")))
                                )
                                .executes(ctx -> executePlayerTp(ctx, null))
                        )
                )
                .then(Commands.literal("world")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_WORLD))
                        .then(Commands.literal("query")
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_WORLD_QUERY))
                                .executes(this::executeWorldQuery)
                        )
                        .then(Commands.literal("tp")
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SPAWN_WORLD_TP))
                                .executes(this::executeWorldTp)
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_SPAWN) || PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_SPAWN_WORLD);
    }

    private int executePlayerTp(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        boolean self = false;
        if (otherPlayer == null || otherPlayer == sourcePlayer) {
            otherPlayer = sourcePlayer;
            self = true;
        }

        int result = teleportToPlayerSpawn(src, sourcePlayer, otherPlayer);

        if (result == 1) {
            if (self) {
                throw PLAYER_SPAWN_NOT_SET.create(src);
            } else {
                throw TARGET_PLAYER_SPAWN_NOT_SET.create(src, otherPlayer);
            }
        }

        if (result == 2) {
            throw FAILED_TO_TELEPORT.create(src);
        }

        if (self) {
            componentBuilder.appendTranslatable("commands.helpful_commands.spawn.player.tp.self.own");
        } else {
            componentBuilder.appendTranslatable("commands.helpful_commands.spawn.player.tp.self.other", StylingUtil.getAffectedEntityNameText(otherPlayer));
        }

        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeWorldTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (!teleportToWorldSpawn(src, sourcePlayer, sourcePlayer.level())) {
            throw FAILED_TO_TELEPORT.create(src);
        }

        componentBuilder.appendTranslatable("commands.helpful_commands.spawn.world.tp").setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executePlayerQuery(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (otherPlayer == null && sourcePlayer == null) {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }

        boolean self = false;
        if (otherPlayer == null || otherPlayer == sourcePlayer) {
            otherPlayer = sourcePlayer;
            self = true;
        }

        ServerPlayer.RespawnConfig respawnConfig = otherPlayer.getRespawnConfig();
        if (respawnConfig == null) {
            if (self) {
                throw PLAYER_SPAWN_NOT_SET.create(src);
            } else {
                throw TARGET_PLAYER_SPAWN_NOT_SET.create(src, otherPlayer);
            }
        }

        src.sendSystemMessage(buildInfoComponent(respawnConfig.respawnData(), src, otherPlayer.getName().getString(), self));

        return Command.SINGLE_SUCCESS;
    }

    private int executeWorldQuery(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        src.sendSystemMessage(buildInfoComponent(src.getLevel().getRespawnData(), src, "", false));

        return Command.SINGLE_SUCCESS;
    }

    private Component buildInfoComponent(LevelData.RespawnData respawnData, CommandSourceStack src, String playerName, boolean isOwnSpawn) {
        HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = currentStyle.getTextDecorators();

        boolean isPlayer = !playerName.isEmpty();
        HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "helpful_commands.hover.click_to_teleport")));
        ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/spawn " + (isPlayer ? "player tp" + (isOwnSpawn ? "" : " " + playerName) : "world tp"));
        Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);

        Vec3 pos = respawnData.pos().getCenter();
        String dimensionLocation = respawnData.dimension().identifier().toString();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder
                .appendComponent(StylingUtil.getTitle(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.spawn.info.title")), Component.literal(isPlayer ? playerName : TranslationManager.translate(src, "commands.helpful_commands.spawn.info.title.world"))))
                .appendNewline()
                .appendLiteral(textDecorators.getBulletPoint())
                .appendTranslatable("helpful_commands.common.position")
                .appendLiteral(": ")
                .appendComponent(StylingUtil.getPositionText(pos.x(), pos.y(), pos.z()))
                .appendNewline()
                .appendLiteral(textDecorators.getBulletPoint())
                .appendTranslatable("helpful_commands.common.dimension")
                .appendLiteral(": ")
                .appendComponent(Component.literal(dimensionLocation).setStyle(textStyles.getSecondary()));

        if (src.isPlayer() && PermissionsUtil.hasPermission(src, isPlayer ? (isOwnSpawn ? ModPermissions.Permission.COMMAND_SPAWN_PLAYER_TP : ModPermissions.Permission.COMMAND_SPAWN_PLAYER_TP_OTHER) : ModPermissions.Permission.COMMAND_SPAWN_WORLD_TP)) {
            componentBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(StylingUtil.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpful_commands.common.teleport")), tpBtnStyle));
        }

        componentBuilder.setStyle(textStyles.getTertiary());

        return componentBuilder.build();
    }

    private int teleportToPlayerSpawn(CommandSourceStack source, ServerPlayer teleportedPlayer, ServerPlayer otherPlayer) throws CommandSyntaxException {
        ServerPlayer.RespawnConfig respawnConfig = otherPlayer.getRespawnConfig();
        if (respawnConfig == null) {
            return 1;
        }
        LevelData.RespawnData respawnData = respawnConfig.respawnData();
        String dimensionLocation = respawnData.dimension().identifier().toString();
        ServerLevel serverLevel;
        try {
            serverLevel = ServerLevelUtil.getLevel(dimensionLocation);
        } catch (ServerLevelUtil.UnknownServerLevelException e) {
            throw UNKNOWN_DIMENSION.create(source, dimensionLocation);
        }

        Vec3 pos = respawnData.pos().getCenter();
        return teleportedPlayer.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), new HashSet<>(), respawnData.yaw(), respawnData.pitch(), false) ? 0 : 2;
    }

    private boolean teleportToWorldSpawn(CommandSourceStack source, ServerPlayer teleportedPlayer, ServerLevel serverLevel) throws CommandSyntaxException {
        LevelData.RespawnData respawnData = serverLevel.getRespawnData();
        String dimensionLocation = respawnData.dimension().identifier().toString();
        try {
            serverLevel = ServerLevelUtil.getLevel(dimensionLocation);
        } catch (ServerLevelUtil.UnknownServerLevelException e) {
            throw UNKNOWN_DIMENSION.create(source, dimensionLocation);
        }

        Vec3 pos = respawnData.pos().getCenter();
        return teleportedPlayer.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), new HashSet<>(), respawnData.yaw(), respawnData.pitch(), false);
    }
}
