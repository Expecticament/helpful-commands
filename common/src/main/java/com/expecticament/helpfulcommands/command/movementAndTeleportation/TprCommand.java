package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.*;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.TprReceivedRequestsPlayerNameSuggestionProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;

public class TprCommand extends HelpfulCommandsCommand {
    public TprCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeAccept(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("deny")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeDeny(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("request")
                        .requires(src -> canExecute(src, "request"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), ""))
                                .then(Commands.argument("comment", StringArgumentType.string())
                                        .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "comment")))
                                )
                        )
                )
                .then(Commands.literal("cancel")
                        .executes(this::executeCancel)
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "request");
    }

    private int executeRequest(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer, String comment) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        TextBuilder textBuilder = new TextBuilder(src);

        if (sourcePlayer == otherPlayer) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.selfRequest");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        if (!canExecuteBaseCommand(otherPlayer.createCommandSourceStack())) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.cantAccept", StylingHelper.getAffectedEntityNameText(otherPlayer));
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        long remainingCooldown = CooldownManager.getRemainingCooldown(sourcePlayer, CooldownManager.CooldownType.TPR_REQUEST);
        if (remainingCooldown > 0) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.onCooldown", StylingHelper.formatDuration(remainingCooldown, sourcePlayer));
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        try {
            int timeoutSeconds = ConfigManager.readConfig().readField(ConfigManager.CONFIG_FIELD.TPR_REQUEST_TIMEOUT);
            long timeout = timeoutSeconds * 1000L;
            TpRequestsManager.newRequest(sourcePlayer, otherPlayer, timeout);

            HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
            HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();
            HelpfulCommandsStyle.TextDecorators textDecorators = currentStyle.getTextDecorators();

            TextBuilder otherTextBuilder = new TextBuilder(otherPlayer);
            Component acceptBtn = StylingHelper.getButton(textDecorators.getCheckmark(), Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.accept")), textStyles.getAffectedPositive().withClickEvent(new ClickEvent.RunCommand("/tpr accept " + sourcePlayer.getName().getString())));
            Component denyBtn = StylingHelper.getButton(textDecorators.getCross(), Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.deny")), textStyles.getAffectedNegative().withClickEvent(new ClickEvent.RunCommand("/tpr deny " + sourcePlayer.getName().getString())));
            otherTextBuilder
                    .appendComponent(StylingHelper.getTitle(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.title"))))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.from")).setStyle(textStyles.getTertiary()))
                    .appendWhitespace()
                    .appendComponent(Component.literal(sourcePlayer.getName().getString()).setStyle(textStyles.getSecondary()));
            if (comment != null && !comment.isEmpty() && !comment.equals(" ")) {
                otherTextBuilder
                        .appendNewline()
                        .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                        .appendComponent(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.comment")).setStyle(textStyles.getTertiary()))
                        .appendWhitespace()
                        .appendLiteral(comment);
            }
            otherTextBuilder
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(new TextBuilder(otherPlayer).setStyle(textStyles.getTertiary()).appendTranslatable("commands.helpful_commands.tpr.request.timeout", StylingHelper.formatDuration(timeout, otherPlayer)).getComponent())
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(acceptBtn)
                    .appendWhitespace()
                    .appendComponent(denyBtn);
            otherPlayer.sendSystemMessage(otherTextBuilder.getComponent());

            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request", StylingHelper.getAffectedEntityNameText(otherPlayer), StylingHelper.formatDuration(timeout, sourcePlayer));
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, false);
        } catch (TpRequestsManager.PendingRequestExistsException e) {
            TextBuilder exceptionTextBuilder = new TextBuilder(src);
            exceptionTextBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.pendingRequestExists");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, exceptionTextBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executeCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);

        TpRequestsManager.Request request = TpRequestsManager.getSentRequest(sourcePlayer);

        if (request == null) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.cancel.error.noPendingRequest");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        TpRequestsManager.removeRequest(sourcePlayer);
        CooldownManager.applyCooldown(sourcePlayer, CooldownManager.CooldownType.TPR_REQUEST, ConfigManager.readConfig().readField(ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_CANCEL));

        ServerPlayer otherPlayer = src.getServer().getPlayerList().getPlayer(request.getTo());
        if (otherPlayer == null) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.cancel.noPlayer");
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.cancel.withPlayer", StylingHelper.getAffectedEntityNameText(otherPlayer));
        }

        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, false);

        return Command.SINGLE_SUCCESS;
    }

    private int executeAccept(CommandContext<CommandSourceStack> ctx, String otherPlayerName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ServerPlayer otherPlayer = src.getServer().getPlayerList().getPlayerByName(otherPlayerName);
        if (otherPlayer == null) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        TextBuilder textBuilder = new TextBuilder(src);

        if (sourcePlayer == otherPlayer) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.selfRequest");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        List<TpRequestsManager.Request> requests = TpRequestsManager.getReceivedRequests(sourcePlayer);
        TpRequestsManager.Request request = null;

        for (TpRequestsManager.Request r : requests) {
            if (r.getFrom().equals(otherPlayer.getUUID())) {
                request = r;
                break;
            }
        }

        if (request == null) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.accept.error.noPendingRequest", StylingHelper.getAffectedEntityNameText(otherPlayer));
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        Vec3 pos = sourcePlayer.position();
        if (otherPlayer.teleportTo(src.getLevel(), pos.x(), pos.y(), pos.z(), new HashSet<>(), otherPlayer.getYRot(), otherPlayer.getXRot(), false)) {
            TpRequestsManager.removeRequest(otherPlayer);
            CooldownManager.applyCooldown(otherPlayer, CooldownManager.CooldownType.TPR_REQUEST, ConfigManager.readConfig().readField(ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_ACCEPTED));

            TextBuilder otherPlayerTextBuilder = new TextBuilder(src);
            otherPlayerTextBuilder.setStyle(textStyles.getAffectedPositive());
            otherPlayerTextBuilder.appendTranslatable("commands.helpful_commands.tpr.accept.affected", StylingHelper.getAffectedEntityNameText(sourcePlayer));
            otherPlayer.sendSystemMessage(otherPlayerTextBuilder.getComponent());

            textBuilder.appendTranslatable("commands.helpful_commands.tpr.accept", StylingHelper.getAffectedEntityNameText(otherPlayer));
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.accept.error.failedToTeleport");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executeDeny(CommandContext<CommandSourceStack> ctx, String otherPlayerName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ServerPlayer otherPlayer = src.getServer().getPlayerList().getPlayerByName(otherPlayerName);
        if (otherPlayer == null) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        TextBuilder textBuilder = new TextBuilder(src);

        if (sourcePlayer == otherPlayer) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request.error.selfRequest");
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        List<TpRequestsManager.Request> requests = TpRequestsManager.getReceivedRequests(sourcePlayer);
        TpRequestsManager.Request request = null;

        for (TpRequestsManager.Request r : requests) {
            if (r.getFrom().equals(otherPlayer.getUUID())) {
                request = r;
                break;
            }
        }

        if (request == null) {
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.deny.error.noPendingRequest", StylingHelper.getAffectedEntityNameText(otherPlayer));
            throw new CommandSyntaxException(HC_COMMAND_EXCEPTION, textBuilder.getComponent());
        }

        TpRequestsManager.removeRequest(otherPlayer);

        TextBuilder otherPlayerTextBuilder = new TextBuilder(src);
        otherPlayerTextBuilder.setStyle(textStyles.getAffectedNegative());
        otherPlayerTextBuilder.appendTranslatable("commands.helpful_commands.tpr.deny.affected", StylingHelper.getAffectedEntityNameText(sourcePlayer));
        otherPlayer.sendSystemMessage(otherPlayerTextBuilder.getComponent());

        textBuilder.appendTranslatable("commands.helpful_commands.tpr.deny", StylingHelper.getAffectedEntityNameText(otherPlayer));
        textBuilder.setStyle(textStyles.getSuccess());
        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
