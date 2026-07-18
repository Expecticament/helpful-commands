package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.*;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.manager.translation.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.permission.PermissionHandlerProvider;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.TprReceivedRequestsPlayerNameSuggestionProvider;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.StylingUtil;
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
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.request.error.target_cant_accept_requests", StylingUtil.getAffectedEntityNameText((ServerPlayer) targetPlayer)).build()
    );
    private static final Dynamic2CommandExceptionType ON_COOLDOWN = new Dynamic2CommandExceptionType((src, remaining) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.request.error.on_cooldown", StylingUtil.formatDuration((long) remaining, (CommandSourceStack) src)).build()
    );
    private static final DynamicCommandExceptionType PENDING_REQUEST_EXISTS = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.request.error.pending_request_exists").build()
    );
    private static final DynamicCommandExceptionType NO_PENDING_REQUEST = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.cancel.error.no_pending_request").build()
    );
    private static final Dynamic2CommandExceptionType NO_PENDING_INCOMING_REQUEST = new Dynamic2CommandExceptionType((src, otherPlayer) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.error.no_pending_incoming_request", StylingUtil.getAffectedEntityNameText((ServerPlayer) otherPlayer)).build()
    );
    private static final DynamicCommandExceptionType FAILED_TO_TELEPORT = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.tpr.accept.error.failed_to_teleport").build()
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
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_ACCEPT))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeAccept(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("deny")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_DENY))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(new TprReceivedRequestsPlayerNameSuggestionProvider())
                                .executes(ctx -> executeDeny(ctx, StringArgumentType.getString(ctx, "player")))
                        )
                )
                .then(Commands.literal("request")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_REQUEST))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), ""))
                                .then(Commands.argument("comment", StringArgumentType.string())
                                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_REQUEST_COMMENT))
                                        .executes(ctx -> executeRequest(ctx, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "comment")))
                                )
                        )
                )
                .then(Commands.literal("cancel")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_TPR_CANCEL))
                        .executes(this::executeCancel)
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_TPR);
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

            ComponentBuilder otherComponentBuilder = new ComponentBuilder(otherPlayer);
            Component acceptBtn = StylingUtil.getButton(textDecorators.getCheckmark(), Component.literal(TranslationManager.translate(otherPlayer, "commands.helpfulcommands.tpr.request.accept")), textStyles.getAffectedPositive().withClickEvent(new ClickEvent.RunCommand("/tpr accept " + sourcePlayer.getName().getString())));
            otherComponentBuilder
                    .appendComponent(StylingUtil.getTitle(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpfulcommands.tpr.request.title"))))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpfulcommands.tpr.request.from")).setStyle(textStyles.getTertiary()))
                    .appendWhitespace()
                    .appendComponent(Component.literal(sourcePlayer.getName().getString()).setStyle(textStyles.getSecondary()));
            if (comment != null && !comment.isEmpty() && !comment.equals(" ")) {
                otherComponentBuilder
                        .appendNewline()
                        .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                        .appendComponent(Component.literal(TranslationManager.translate(otherPlayer, "commands.helpfulcommands.tpr.request.comment")).setStyle(textStyles.getTertiary()))
                        .appendWhitespace()
                        .appendLiteral(comment);
            }
            otherComponentBuilder
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(new ComponentBuilder(otherPlayer).setStyle(textStyles.getTertiary()).appendTranslatable("commands.helpfulcommands.tpr.request.timeout", StylingUtil.formatDuration(timeout, otherPlayer)).build())
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(acceptBtn);
            if (canDeny(otherPlayer)) {
                Component denyBtn = StylingUtil.getButton(textDecorators.getCross(), Component.literal(TranslationManager.translate(otherPlayer, "commands.helpfulcommands.tpr.request.deny")), textStyles.getAffectedNegative().withClickEvent(new ClickEvent.RunCommand("/tpr deny " + sourcePlayer.getName().getString())));
                otherComponentBuilder
                        .appendWhitespace()
                        .appendComponent(denyBtn);
            }

            otherPlayer.sendSystemMessage(otherComponentBuilder.build());

            ComponentBuilder componentBuilder = new ComponentBuilder(src);
            componentBuilder.appendTranslatable("commands.helpfulcommands.tpr.request", StylingUtil.getAffectedEntityNameText(otherPlayer), StylingUtil.formatDuration(timeout, sourcePlayer));
            componentBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(componentBuilder::build, false);
        } catch (TpRequestsManager.PendingRequestExistsException e) {
            throw PENDING_REQUEST_EXISTS.create(src);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executeCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        TpRequestsManager.Request request = TpRequestsManager.getSentRequest(sourcePlayer);

        if (request == null) {
            throw NO_PENDING_REQUEST.create(src);
        }

        TpRequestsManager.removeRequest(sourcePlayer);
        CooldownManager.applyCooldown(sourcePlayer, CooldownManager.CooldownType.TPR_REQUEST, PermissionsUtil.getMetaOrElseConfigValue(sourcePlayer, ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_CANCEL));

        ServerPlayer otherPlayer = src.getServer().getPlayerList().getPlayer(request.getTo());
        if (otherPlayer == null) {
            componentBuilder.appendTranslatable("commands.helpfulcommands.tpr.cancel.noPlayer");
        } else {
            componentBuilder.appendTranslatable("commands.helpfulcommands.tpr.cancel.withPlayer", StylingUtil.getAffectedEntityNameText(otherPlayer));
        }

        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, false);

        return Command.SINGLE_SUCCESS;
    }

    private int executeAccept(CommandContext<CommandSourceStack> ctx, String otherPlayerName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ServerPlayer otherPlayer = src.getServer().getPlayerList().getPlayerByName(otherPlayerName);
        if (otherPlayer == null) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

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
            CooldownManager.applyCooldown(otherPlayer, CooldownManager.CooldownType.TPR_REQUEST, PermissionsUtil.getMetaOrElseConfigValue(otherPlayer, ConfigManager.CONFIG_FIELD.TPR_REQUEST_COOLDOWN_ON_ACCEPTED));

            ComponentBuilder otherPlayerComponentBuilder = new ComponentBuilder(src);
            otherPlayerComponentBuilder.setStyle(textStyles.getAffectedPositive());
            otherPlayerComponentBuilder.appendTranslatable("commands.helpfulcommands.tpr.accept.affected", StylingUtil.getAffectedEntityNameText(sourcePlayer));
            otherPlayer.sendSystemMessage(otherPlayerComponentBuilder.build());

            componentBuilder.appendTranslatable("commands.helpfulcommands.tpr.accept", StylingUtil.getAffectedEntityNameText(otherPlayer));
            componentBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(componentBuilder::build, true);
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

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

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

        ComponentBuilder otherPlayerComponentBuilder = new ComponentBuilder(src);
        otherPlayerComponentBuilder.setStyle(textStyles.getAffectedNegative());
        otherPlayerComponentBuilder.appendTranslatable("commands.helpfulcommands.tpr.deny.affected", StylingUtil.getAffectedEntityNameText(sourcePlayer));
        otherPlayer.sendSystemMessage(otherPlayerComponentBuilder.build());

        componentBuilder.appendTranslatable("commands.helpfulcommands.tpr.deny", StylingUtil.getAffectedEntityNameText(otherPlayer));
        componentBuilder.setStyle(textStyles.getSuccess());
        src.sendSuccess(componentBuilder::build, true);

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
