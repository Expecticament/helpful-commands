package com.expecticament.helpful_commands.command.movementAndTeleportation;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.helper.PermissionHelper;
import com.expecticament.helpful_commands.helper.StylingHelper;
import com.expecticament.helpful_commands.manager.*;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.permission.PermissionHandlerProvider;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.expecticament.helpful_commands.suggestionProvider.TprReceivedRequestsPlayerNameSuggestionProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;

public class TprCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType TARGET_CANT_ACCEPT_REQUESTS = new Dynamic2CommandExceptionType((src, targetPlayer) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.request.error.targetCantAcceptRequests", StylingHelper.getAffectedEntityNameText((ServerPlayer) targetPlayer)).getComponent()
    );
    private static final Dynamic2CommandExceptionType ON_COOLDOWN = new Dynamic2CommandExceptionType((src, remaining) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.request.error.onCooldown", StylingHelper.formatDuration((long) remaining, (CommandSourceStack) src)).getComponent()
    );
    private static final DynamicCommandExceptionType PENDING_REQUEST_EXISTS = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.request.error.pendingRequestExists").getComponent()
    );
    private static final DynamicCommandExceptionType NO_PENDING_REQUEST = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.cancel.error.noPendingRequest").getComponent()
    );
    private static final Dynamic2CommandExceptionType NO_PENDING_INCOMING_REQUEST = new Dynamic2CommandExceptionType((src, otherPlayer) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.error.noPendingIncomingRequest", StylingHelper.getAffectedEntityNameText((ServerPlayer) otherPlayer)).getComponent()
    );
    private static final DynamicCommandExceptionType FAILED_TO_TELEPORT = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.tpr.accept.error.failedToTeleport").getComponent()
    );

    public TprCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("accept")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_ACCEPT))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeAccept(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("deny")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_DENY))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeDeny(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("request")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_REQUEST))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), ""))
                                .then(Commands.argument("comment", StringArgumentType.string())
                                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_REQUEST_COMMENT))
                                        .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "comment")))
                                )
                        )
                )
                .then(Commands.literal("cancel")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_CANCEL))
                        .executes(this::executeCancel)
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_TPR);
    }

    private int executeRequest(CommandContext<CommandSourceStack> ctx, ServerPlayer otherPlayer, String comment) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        if (sourcePlayer == otherPlayer) {
            throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
        }

        if (!canAccept(otherPlayer)) {
            throw TARGET_CANT_ACCEPT_REQUESTS.create(src, otherPlayer);
        }

        long remainingCooldown = CooldownManager.getRemainingCooldown(sourcePlayer, CooldownManager.CooldownType.TPR_REQUEST);
        if (remainingCooldown > 0) {
            throw ON_COOLDOWN.create(src, remainingCooldown);
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
                    .appendComponent(acceptBtn);
            if (canDeny(otherPlayer)) {
                Component denyBtn = StylingHelper.getButton(textDecorators.getCross(), Component.literal(TranslationManager.translate(otherPlayer, "commands.helpful_commands.tpr.request.deny")), textStyles.getAffectedNegative().withClickEvent(new ClickEvent.RunCommand("/tpr deny " + sourcePlayer.getName().getString())));
                otherTextBuilder
                        .appendWhitespace()
                        .appendComponent(denyBtn);
            }

            otherPlayer.sendSystemMessage(otherTextBuilder.getComponent());

            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.tpr.request", StylingHelper.getAffectedEntityNameText(otherPlayer), StylingHelper.formatDuration(timeout, sourcePlayer));
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, false);
        } catch (TpRequestsManager.PendingRequestExistsException e) {
            throw PENDING_REQUEST_EXISTS.create(src);
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
            throw NO_PENDING_REQUEST.create(src);
        }

        TpRequestsManager.removeRequest(sourcePlayer);
        CooldownManager.applyCooldown(sourcePlayer, CooldownManager.CooldownType.TPR_REQUEST, PermissionHelper.getMetaOrElseConfigValue(sourcePlayer, ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_CANCEL));

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
            throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
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
            throw NO_PENDING_INCOMING_REQUEST.create(src, otherPlayer);
        }

        Vec3 pos = sourcePlayer.position();
        if (otherPlayer.teleportTo(src.getLevel(), pos.x(), pos.y(), pos.z(), new HashSet<>(), otherPlayer.getYRot(), otherPlayer.getXRot(), false)) {
            TpRequestsManager.removeRequest(otherPlayer);
            CooldownManager.applyCooldown(otherPlayer, CooldownManager.CooldownType.TPR_REQUEST, PermissionHelper.getMetaOrElseConfigValue(otherPlayer, ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_ACCEPTED));

            TextBuilder otherPlayerTextBuilder = new TextBuilder(src);
            otherPlayerTextBuilder.setStyle(textStyles.getAffectedPositive());
            otherPlayerTextBuilder.appendTranslatable("commands.helpful_commands.tpr.accept.affected", StylingHelper.getAffectedEntityNameText(sourcePlayer));
            otherPlayer.sendSystemMessage(otherPlayerTextBuilder.getComponent());

            textBuilder.appendTranslatable("commands.helpful_commands.tpr.accept", StylingHelper.getAffectedEntityNameText(otherPlayer));
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } else {
            throw FAILED_TO_TELEPORT.create(src);
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
            throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
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
            throw NO_PENDING_INCOMING_REQUEST.create(src, otherPlayer);
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

    private boolean canAccept(ServerPlayer player) {
        CommandSourceStack src = player.createCommandSourceStack();
        return checkBaseCommandRequirements(src) && PermissionHandlerProvider.get().hasPermission(src, ModPermissions.Permission.COMMAND_TPR_ACCEPT);
    }

    private boolean canDeny(ServerPlayer player) {
        CommandSourceStack src = player.createCommandSourceStack();
        return checkBaseCommandRequirements(src) && PermissionHandlerProvider.get().hasPermission(src, ModPermissions.Permission.COMMAND_TPR_DENY);
    }
}
