package com.expecticament.helpful_commands.command.movementAndTeleportation;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.helper.PermissionHelper;
import com.expecticament.helpful_commands.helper.ServerLevelHelper;
import com.expecticament.helpful_commands.helper.StylingHelper;
import com.expecticament.helpful_commands.manager.*;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.expecticament.helpful_commands.suggestionProvider.HomeNameSuggestionProvider;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

public class HomeCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType HOME_DOESNT_EXIST = new Dynamic2CommandExceptionType((src, homeName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.home.error.homeDoesntExist", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final Dynamic2CommandExceptionType HOME_ALREADY_EXISTS = new Dynamic2CommandExceptionType((src, homeName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.home.error.homeAlreadyExists", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final DynamicCommandExceptionType HOME_LIMIT_EXCEEDED = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.home.add.error.homeLimitExceeded").getComponent()
    );
    private static final Dynamic2CommandExceptionType SAME_HOME_NAME_PROVIDED = new Dynamic2CommandExceptionType((src, homeName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.home.edit.name.error.sameHomeNameProvided", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );

    public HomeCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        HomeNameSuggestionProvider homeNameSuggestionProvider = new HomeNameSuggestionProvider();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("tp")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_TP))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("add")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_ADD))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .executes(ctx -> addHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("remove")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_REMOVE))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> removeHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("edit")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_EDIT))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .then(Commands.literal("name")
                                        .then(Commands.argument("new_name", StringArgumentType.word())
                                                .executes(ctx -> editHomeName(ctx, StringArgumentType.getString(ctx, "home_name"), StringArgumentType.getString(ctx, "new_name")))
                                        )
                                )
                                .then(Commands.literal("location")
                                        .executes(ctx -> editHomeLocation(ctx, StringArgumentType.getString(ctx, "home_name")))
                                )
                        )
                )
                .then(Commands.literal("info")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_INFO))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> homeInfo(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_HOME);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        long remainingCooldown = CooldownManager.getRemainingCooldown(sourcePlayer, CooldownManager.CooldownType.HOME_TP);
        if (remainingCooldown > 0) {
            throw ON_COOLDOWN_TELEPORT.create(src, remainingCooldown);
        }

        try {
            HomeManager.Home home = HomeManager.getHome(sourcePlayer, homeName);
            try {
                ServerLevel level = ServerLevelHelper.getLevel(home.dimension);
                sourcePlayer.teleportTo(level, home.x, home.y, home.z, Relative.DELTA, sourcePlayer.getYRot(), sourcePlayer.getXRot(), false);

                CooldownManager.applyCooldown(sourcePlayer, CooldownManager.CooldownType.HOME_TP, PermissionHelper.getMetaOrElseConfigValue(sourcePlayer, ConfigManager.CONFIG_FIELD.HOME_TP_COOLDOWN));

                TextBuilder textBuilder = new TextBuilder(sourcePlayer);
                textBuilder.appendTranslatable("commands.helpful_commands.home.teleport", Component.literal(homeName).setStyle(textStyles.getPrimary()));
                textBuilder.setStyle(textStyles.getSuccess());

                src.sendSuccess(textBuilder::getComponent, true);
            } catch (ServerLevelHelper.UnknownServerLevelException e) {
                throw UNKNOWN_DIMENSION.create(src, home.dimension);
            }
        } catch (HomeManager.HomeDoesntExistException e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int addHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.addHome(sourcePlayer, homeName);

            TextBuilder textBuilder = new TextBuilder(sourcePlayer);
            textBuilder.appendTranslatable("commands.helpful_commands.home.add", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeAlreadyExistsException e) {
            throw HOME_ALREADY_EXISTS.create(src, homeName);
        } catch (HomeManager.HomeLimitExceededException e) {
            throw HOME_LIMIT_EXCEEDED.create(src);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int removeHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.removeHome(sourcePlayer, homeName);

            TextBuilder textBuilder = new TextBuilder(sourcePlayer);
            textBuilder.appendTranslatable("commands.helpful_commands.home.remove", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeName(CommandContext<CommandSourceStack> ctx, String homeName, String newName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.editHomeName(sourcePlayer, homeName, newName);

            TextBuilder textBuilder = new TextBuilder(sourcePlayer);
            textBuilder.appendTranslatable("commands.helpful_commands.home.edit.name", Component.literal(homeName).setStyle(textStyles.getPrimary()), Component.literal(newName).setStyle(textStyles.getPrimary()));
            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        } catch (HomeManager.SameHomeNameProvidedException e) {
            throw SAME_HOME_NAME_PROVIDED.create(src, newName);
        } catch (HomeManager.HomeAlreadyExistsException e) {
            throw HOME_ALREADY_EXISTS.create(src, newName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeLocation(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.editHomeLocation(sourcePlayer, homeName, sourcePlayer.position(), sourcePlayer.level());

            TextBuilder textBuilder = new TextBuilder(sourcePlayer);
            textBuilder.appendTranslatable("commands.helpful_commands.home.edit.location", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int homeInfo(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = StylingManager.getCurrentStyle().getTextDecorators();

        try {
            HomeManager.Home home = HomeManager.getHome(sourcePlayer, homeName);
            TextBuilder textBuilder = new TextBuilder(sourcePlayer);

            textBuilder
                    .appendComponent(StylingHelper.getTitle(Component.literal(TranslationManager.translate(sourcePlayer, "commands.helpful_commands.home.info.title")), Component.literal(homeName)))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(sourcePlayer, "helpful_commands.common.position")).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(": ").setStyle(textStyles.getTertiary()))
                    .appendComponent(StylingHelper.getPositionText(home.x, home.y, home.z))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(sourcePlayer, "helpful_commands.common.dimension")).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(": ").setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(home.dimension).setStyle(textStyles.getSecondary()));

            boolean canTp = PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_TP);
            boolean canEdit = PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_EDIT);
            boolean canRemove = PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_REMOVE);

            if (canTp || canEdit || canRemove) {
                textBuilder
                        .appendNewline()
                        .appendNewline();

                if (canTp) {
                    HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToTeleport")));
                    ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/home tp " + homeName);
                    Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);
                    textBuilder.appendComponent(StylingHelper.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpful_commands.common.teleport")), tpBtnStyle));
                }

                if (canEdit) {
                    HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToEdit")));
                    ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/home edit " + homeName + " ");
                    Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);
                    if (canTp) {
                        textBuilder.appendWhitespace();
                    }
                    textBuilder.appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle));
                }

                if (canRemove) {
                    HoverEvent removeBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToRemove")));
                    ClickEvent removeBtnClickEvent = new ClickEvent.RunCommand("/home remove " + homeName);
                    Style removeBtnStyle = textStyles.getDangerousAction().withHoverEvent(removeBtnHoverEvent).withClickEvent(removeBtnClickEvent);
                    if (canTp || canEdit) {
                        textBuilder.appendWhitespace();
                    }
                    textBuilder.appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getRemove()), removeBtnStyle));
                }
            }

            src.sendSystemMessage(textBuilder.getComponent());
        } catch (HomeManager.HomeDoesntExistException e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }
}
