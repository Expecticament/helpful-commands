package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.HomeNameSuggestionProvider;
import com.expecticament.helpfulcommands.manager.HomeManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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
import com.expecticament.helpfulcommands.manager.TranslationManager.DeprecatedTextBuilder;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;

public class CMD_home extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType HOME_DOESNT_EXIST = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType HOME_ALREADY_EXISTS = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType HOME_LIMIT_EXCEEDED = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType SAME_NAME_PROVIDED = new SimpleCommandExceptionType(Component.empty());

    public CMD_home(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        HomeNameSuggestionProvider homeNameSuggestionProvider = new HomeNameSuggestionProvider();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("tp")
                        .requires(ctx -> canExecute(ctx, "tp"))
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .executes(ctx -> addHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> removeHome(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
                .then(Commands.literal("edit")
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
                        .then(Commands.argument("home_name", StringArgumentType.word())
                                .suggests(homeNameSuggestionProvider)
                                .executes(ctx -> homeInfo(ctx, StringArgumentType.getString(ctx, "home_name")))
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "tp");
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.Home home = HomeManager.getHome(sourcePlayer, homeName);
            try {
                ServerLevel level = ServerLevelHelper.getLevel(home.dimension);
                sourcePlayer.teleportTo(level, home.x, home.y, home.z, Relative.DELTA, sourcePlayer.getYRot(), sourcePlayer.getXRot(), false);

                DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
                textBuilder
                        .appendTranslatable("commands.helpful_commands.home.teleport.output.success.part1")
                        .appendWhitespace()
                        .appendLiteral(homeName, textStyles.getPrimary())
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.home.teleport.output.success.part2");
                textBuilder.setStyle(textStyles.getSuccess());
                src.sendSuccess(textBuilder::getComponent, true);
            } catch (ServerLevelHelper.UnknownServerLevelException e) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(home.dimension).setStyle(textStyles.getPrimary()));
                throw new CommandSyntaxException(UNKNOWN_DIMENSION, textBuilder.getComponent());
            }
        } catch (HomeManager.HomeDoesntExistException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeDoesntExist");
            throw new CommandSyntaxException(HOME_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int addHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.addHome(sourcePlayer, homeName);
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendTranslatable("commands.helpful_commands.home.add.output.success")
                    .appendWhitespace()
                    .appendLiteral(homeName, textStyles.getPrimary());
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeAlreadyExistsException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeAlreadyExists");
            throw new CommandSyntaxException(HOME_ALREADY_EXISTS, textBuilder.getComponent());
        } catch (HomeManager.HomeLimitExceededException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder.appendTranslatable("commands.helpful_commands.home.add.output.error.homeLimitExceeded");
            throw new CommandSyntaxException(HOME_LIMIT_EXCEEDED, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int removeHome(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.removeHome(sourcePlayer, homeName);
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendTranslatable("commands.helpful_commands.home.remove.output.success")
                    .appendWhitespace()
                    .appendLiteral(homeName, textStyles.getPrimary());
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeDoesntExist");
            throw new CommandSyntaxException(HOME_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeName(CommandContext<CommandSourceStack> ctx, String homeName, String newName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.editHomeName(sourcePlayer, homeName, newName);
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendTranslatable("commands.helpful_commands.home.edit.name.output.success.part1")
                    .appendWhitespace()
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.home.edit.name.output.success.part2")
                    .appendWhitespace()
                    .appendLiteral(newName, textStyles.getPrimary());
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeDoesntExist");
            throw new CommandSyntaxException(HOME_DOESNT_EXIST, textBuilder.getComponent());
        } catch (HomeManager.SameHomeNameProvided e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.edit.name.output.error.sameName");
            throw new CommandSyntaxException(SAME_NAME_PROVIDED, textBuilder.getComponent());
        } catch (HomeManager.HomeAlreadyExistsException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(newName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeAlreadyExists");
            throw new CommandSyntaxException(HOME_ALREADY_EXISTS, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editHomeLocation(CommandContext<CommandSourceStack> ctx, String homeName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            HomeManager.editHomeLocation(sourcePlayer, homeName, sourcePlayer.position(), sourcePlayer.level());
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendTranslatable("commands.helpful_commands.home.edit.location.output.success.part1")
                    .appendWhitespace()
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.home.edit.location.output.success.part2");
            textBuilder.setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (HomeManager.HomeDoesntExistException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeDoesntExist");
            throw new CommandSyntaxException(HOME_DOESNT_EXIST, textBuilder.getComponent());
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
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);

            HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToTeleport")));
            ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/home tp " + homeName);
            Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);
            HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToEdit")));
            ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/home edit " + homeName + " ");
            Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);
            HoverEvent removeBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(sourcePlayer, "hover.helpful_commands.clickToRemove")));
            ClickEvent removeBtnClickEvent = new ClickEvent.RunCommand("/home remove " + homeName);
            Style removeBtnStyle = textStyles.getDangerousAction().withHoverEvent(removeBtnHoverEvent).withClickEvent(removeBtnClickEvent);

            textBuilder
                    .appendComponent(StylingHelper.getTitle(Component.literal(TranslationManager.translate(sourcePlayer, "commands.helpful_commands.home.info.title")), Component.literal(homeName)))
                    .appendNewline()
                    .appendLiteral(textDecorators.getBulletPoint(), textStyles.getTertiary())
                    .appendTranslatable("helpful_commands.common.position", textStyles.getTertiary())
                    .appendLiteral(": ", textStyles.getTertiary())
                    .appendComponent(StylingHelper.getPositionText(home.x, home.y, home.z))
                    .appendNewline()
                    .appendLiteral(textDecorators.getBulletPoint(), textStyles.getTertiary())
                    .appendTranslatable("helpful_commands.common.dimension", textStyles.getTertiary())
                    .appendLiteral(": ", textStyles.getTertiary())
                    .appendLiteral(home.dimension, textStyles.getSecondary())
                    .appendNewline()
                    .appendNewline();

            if (src.isPlayer() && canExecute(src, "tp")) {
                textBuilder
                        .appendComponent(StylingHelper.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(sourcePlayer, "helpful_commands.common.teleport")), tpBtnStyle))
                        .appendWhitespace();
            }

            textBuilder
                    .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle))
                    .appendWhitespace()
                    .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getRemove()), removeBtnStyle));

            src.sendSystemMessage(textBuilder.getComponent());
        } catch (HomeManager.HomeDoesntExistException e) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(sourcePlayer);
            textBuilder
                    .appendLiteral(homeName, textStyles.getPrimary())
                    .appendTranslatable("commands.helpful_commands.home.output.error.homeDoesntExist");
            throw new CommandSyntaxException(HOME_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }
}
