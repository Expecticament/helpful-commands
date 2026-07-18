package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.*;
import com.expecticament.helpfulcommands.manager.home.Home;
import com.expecticament.helpfulcommands.manager.home.HomeException;
import com.expecticament.helpfulcommands.manager.home.HomeManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.manager.translation.TranslationManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.HomeNameSuggestionProvider;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.ServerLevelUtil;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

public class HomeCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType HOME_DOESNT_EXIST = new Dynamic2CommandExceptionType((src, homeName) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.home.error.doesnt_exist", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );
    private static final Dynamic2CommandExceptionType HOME_ALREADY_EXISTS = new Dynamic2CommandExceptionType((src, homeName) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.home.error.already_exists", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );
    private static final DynamicCommandExceptionType HOME_LIMIT_REACHED = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.home.add.error.limit_reached").build()
    );
    private static final Dynamic2CommandExceptionType SAME_HOME_NAME_PROVIDED = new Dynamic2CommandExceptionType((src, homeName) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.home.edit.name.error.same_name", Component.literal(homeName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
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
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_TP))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("add")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_ADD))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .executes(ctx -> addHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("remove")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_REMOVE))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> removeHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("edit")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_EDIT))
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
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_INFO))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> homeInfo(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_HOME);
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
            Home home = HomeManager.getHome(sourcePlayer, homeName);
            try {
                ServerLevel level = ServerLevelUtil.getLevel(home.dimension);
                sourcePlayer.teleportTo(level, home.x, home.y, home.z, Relative.DELTA, sourcePlayer.getYRot(), sourcePlayer.getXRot(), false);

                CooldownManager.applyCooldown(sourcePlayer, CooldownManager.CooldownType.HOME_TP, PermissionsUtil.getMetaOrElseConfigValue(sourcePlayer, ConfigManager.CONFIG_FIELD.HOME_TP_COOLDOWN));

                ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);
                componentBuilder.appendTranslatable("commands.helpfulcommands.home.teleport", Component.literal(homeName).setStyle(textStyles.getPrimary()));
                componentBuilder.setStyle(textStyles.getSuccess());

                src.sendSuccess(componentBuilder::build, true);
            } catch (ServerLevelUtil.UnknownServerLevelException e) {
                throw UNKNOWN_DIMENSION.create(src, home.dimension);
            }
        } catch (HomeException.DoesntExist e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int addHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.createHome(sourcePlayer, homeName);

            ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);
            componentBuilder.appendTranslatable("commands.helpfulcommands.home.add", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            componentBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(componentBuilder::build, true);
        } catch (HomeException.AlreadyExists e) {
            throw HOME_ALREADY_EXISTS.create(src, homeName);
        } catch (HomeException.LimitReached e) {
            throw HOME_LIMIT_REACHED.create(src);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int removeHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.removeHome(sourcePlayer, homeName);

            ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);
            componentBuilder.appendTranslatable("commands.helpfulcommands.home.remove", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            componentBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(componentBuilder::build, true);
        } catch (HomeException.DoesntExist e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeName(CommandContext<CommandSourceStack> ctx, String homeName, String newName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.setHomeName(sourcePlayer, homeName, newName);

            ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);
            componentBuilder.appendTranslatable("commands.helpfulcommands.home.edit.name", Component.literal(homeName).setStyle(textStyles.getPrimary()), Component.literal(newName).setStyle(textStyles.getPrimary()));
            componentBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(componentBuilder::build, true);
        } catch (HomeException.DoesntExist e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        } catch (HomeException.SameName e) {
            throw SAME_HOME_NAME_PROVIDED.create(src, newName);
        } catch (HomeException.AlreadyExists e) {
            throw HOME_ALREADY_EXISTS.create(src, newName);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeLocation(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.setHomeLocation(sourcePlayer, homeName, sourcePlayer.position(), sourcePlayer.level());

            ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);
            componentBuilder.appendTranslatable("commands.helpfulcommands.home.edit.location", Component.literal(homeName).setStyle(textStyles.getPrimary()));
            componentBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(componentBuilder::build, true);
        } catch (HomeException.DoesntExist e) {
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
            Home home = HomeManager.getHome(sourcePlayer, homeName);
            ComponentBuilder componentBuilder = new ComponentBuilder(sourcePlayer);

            componentBuilder
                    .appendComponent(StylingUtil.getTitle(Component.literal(TranslationManager.translate(sourcePlayer, "commands.helpfulcommands.home.info.title")), Component.literal(homeName)))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(sourcePlayer, "helpfulcommands.common.position")).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(": ").setStyle(textStyles.getTertiary()))
                    .appendComponent(StylingUtil.getPositionText(home.x, home.y, home.z))
                    .appendNewline()
                    .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(TranslationManager.translate(sourcePlayer, "helpfulcommands.common.dimension")).setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(": ").setStyle(textStyles.getTertiary()))
                    .appendComponent(Component.literal(home.dimension).setStyle(textStyles.getSecondary()));

            boolean canTp = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_TP);
            boolean canEdit = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_EDIT);
            boolean canRemove = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HOME_REMOVE);

            if (canTp || canEdit || canRemove) {
                componentBuilder
                        .appendNewline()
                        .appendNewline();

                if (canTp) {
                    HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "helpfulcommands.hover.click_to_teleport")));
                    ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/home tp " + homeName);
                    Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);
                    componentBuilder.appendComponent(StylingUtil.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpfulcommands.common.teleport")), tpBtnStyle));
                }

                if (canEdit) {
                    HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "helpfulcommands.hover.click_to_edit")));
                    ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/home edit " + homeName + " ");
                    Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);
                    if (canTp) {
                        componentBuilder.appendWhitespace();
                    }
                    componentBuilder.appendComponent(StylingUtil.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle));
                }

                if (canRemove) {
                    HoverEvent removeBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "helpfulcommands.hover.click_to_remove")));
                    ClickEvent removeBtnClickEvent = new ClickEvent.RunCommand("/home remove " + homeName);
                    Style removeBtnStyle = textStyles.getDangerousAction().withHoverEvent(removeBtnHoverEvent).withClickEvent(removeBtnClickEvent);
                    if (canTp || canEdit) {
                        componentBuilder.appendWhitespace();
                    }
                    componentBuilder.appendComponent(StylingUtil.getButton(Component.literal(textDecorators.getRemove()), removeBtnStyle));
                }
            }

            src.sendSystemMessage(componentBuilder.build());
        } catch (HomeException.DoesntExist e) {
            throw HOME_DOESNT_EXIST.create(src, homeName);
        }

        return Command.SINGLE_SUCCESS;
    }
}
