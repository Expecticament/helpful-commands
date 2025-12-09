package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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

public class CMD_spawn extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType FAILED_TO_TELEPORT = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType PLAYER_SPAWN_NOT_SET = new SimpleCommandExceptionType(Component.empty());

    public CMD_spawn(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("player")
                        .then(Commands.literal("tp")
                                .requires(src -> canExecute(src, "player.tp"))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> canExecute(src, "player.tp.other"))
                                        .executes(ctx -> executePlayerTp(ctx, EntityArgument.getPlayer(ctx, "player")))
                                )
                                .executes(ctx -> executePlayerTp(ctx, null))
                        )
                        .then(Commands.literal("query")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> canExecute(src, "player.query.other"))
                                        .executes(ctx -> executePlayerQuery(ctx, EntityArgument.getPlayer(ctx, "player")))
                                )
                                .executes(ctx -> executePlayerQuery(ctx, null))
                        )
                )
                .then(Commands.literal("world")
                        .then(Commands.literal("tp")
                                .requires(src -> canExecute(src, "world.tp"))
                                .executes(this::executeWorldTp)
                        )
                        .then(Commands.literal("query")
                                .executes(this::executeWorldQuery)
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "player.tp") || canExecute(source, "player.tp.other") || canExecute(source, "player.query.other") || canExecute(source, "world.tp");
    }

    private int executePlayerTp(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);

        boolean self = false;
        if (otherPlayer == null || otherPlayer == sourcePlayer) {
            otherPlayer = sourcePlayer;
            self = true;
        }

        int result = teleportToPlayerSpawn(sourcePlayer, otherPlayer);

        if (result == 1) {
            if (self) {
                textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.playerSpawnNotSet.self");
            } else {
                textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.playerSpawnNotSet.other", StylingHelper.getAffectedEntityNameText(otherPlayer));
            }
            throw new CommandSyntaxException(PLAYER_SPAWN_NOT_SET, textBuilder.getComponent());
        }

        if (result == 2) {
            textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.failedToTeleport");
            throw new CommandSyntaxException(FAILED_TO_TELEPORT, textBuilder.getComponent());
        }

        if (self) {
            textBuilder.appendTranslatable("commands.helpful_commands.spawn.player.tp.self.own");
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.spawn.player.tp.self.other", StylingHelper.getAffectedEntityNameText(otherPlayer));
        }

        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeWorldTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);

        if (!teleportToWorldSpawn(sourcePlayer, sourcePlayer.level())) {
            textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.failedToTeleport");
            throw new CommandSyntaxException(FAILED_TO_TELEPORT, textBuilder.getComponent());
        }

        textBuilder.appendTranslatable("commands.helpful_commands.spawn.world.tp").setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executePlayerQuery(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        TextBuilder textBuilder = new TextBuilder(src);

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
                textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.playerSpawnNotSet.self");
            } else {
                textBuilder.appendTranslatable("commands.helpful_commands.spawn.error.playerSpawnNotSet.other", StylingHelper.getAffectedEntityNameText(otherPlayer));
            }
            throw new CommandSyntaxException(PLAYER_SPAWN_NOT_SET, textBuilder.getComponent());
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
        HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToTeleport")));
        ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/spawn " + (isPlayer ? "player tp" + (isOwnSpawn ? "" : " " + playerName) : "world tp"));
        Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);

        Vec3 pos = respawnData.pos().getCenter();
        String dimensionLocation = respawnData.dimension().identifier().toString();

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder
                .appendComponent(StylingHelper.getTitle(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.spawn.info.title")), Component.literal(isPlayer ? playerName : TranslationManager.translate(src, "commands.helpful_commands.spawn.info.title.world"))))
                .appendNewline()
                .appendLiteral(textDecorators.getBulletPoint())
                .appendTranslatable("helpful_commands.common.position")
                .appendLiteral(": ")
                .appendComponent(StylingHelper.getPositionText(pos.x(), pos.y(), pos.z()))
                .appendNewline()
                .appendLiteral(textDecorators.getBulletPoint())
                .appendTranslatable("helpful_commands.common.dimension")
                .appendLiteral(": ")
                .appendComponent(Component.literal(dimensionLocation).setStyle(textStyles.getSecondary()));

        if (src.isPlayer() && canExecute(src, isPlayer ? (isOwnSpawn ? "player.tp" : "player.tp.other") : "world.tp")) {
            textBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(StylingHelper.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpful_commands.common.teleport")), tpBtnStyle));
        }

        textBuilder.setStyle(textStyles.getTertiary());

        return textBuilder.getComponent();
    }

    private int teleportToPlayerSpawn(ServerPlayer teleportedPlayer, ServerPlayer otherPlayer) throws CommandSyntaxException {
        ServerPlayer.RespawnConfig respawnConfig = otherPlayer.getRespawnConfig();
        if (respawnConfig == null) {
            return 1;
        }
        LevelData.RespawnData respawnData = respawnConfig.respawnData();
        String dimensionLocation = respawnData.dimension().identifier().toString();
        ServerLevel serverLevel;
        try {
            serverLevel = ServerLevelHelper.getLevel(dimensionLocation);
        } catch (ServerLevelHelper.UnknownServerLevelException e) {
            HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
            TextBuilder eTextBuilder = new TranslationManager.TextBuilder(teleportedPlayer);
            eTextBuilder.appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(dimensionLocation).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(UNKNOWN_DIMENSION, eTextBuilder.getComponent());
        }

        Vec3 pos = respawnData.pos().getCenter();
        return teleportedPlayer.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), new HashSet<>(), respawnData.yaw(), respawnData.pitch(), false) ? 0 : 2;
    }

    private boolean teleportToWorldSpawn(ServerPlayer teleportedPlayer, ServerLevel serverLevel) throws CommandSyntaxException {
        LevelData.RespawnData respawnData = serverLevel.getRespawnData();
        String dimensionLocation = respawnData.dimension().identifier().toString();
        try {
            serverLevel = ServerLevelHelper.getLevel(dimensionLocation);
        } catch (ServerLevelHelper.UnknownServerLevelException e) {
            HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
            TextBuilder eTextBuilder = new TranslationManager.TextBuilder(teleportedPlayer);
            eTextBuilder.appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(dimensionLocation).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(UNKNOWN_DIMENSION, eTextBuilder.getComponent());
        }

        Vec3 pos = respawnData.pos().getCenter();
        return teleportedPlayer.teleportTo(serverLevel, pos.x(), pos.y(), pos.z(), new HashSet<>(), respawnData.yaw(), respawnData.pitch(), false);
    }
}
