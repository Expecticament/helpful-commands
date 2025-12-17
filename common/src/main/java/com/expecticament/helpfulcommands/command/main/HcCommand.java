package com.expecticament.helpfulcommands.command.main;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager.CommandData;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.HelpfulCommandsCommandSuggestionProvider;
import com.expecticament.helpfulcommands.suggestionProvider.StyleSuggestionProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.network.chat.*;

import java.net.URI;
import java.util.*;

public class HcCommand extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType INVALID_HC_COMMAND = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType STYLE_DOESNT_EXIST = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType STYLE_ALREADY_IN_USE = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType COMMAND_NOT_CONFIGURABLE = new SimpleCommandExceptionType(Component.empty());

    private record CommandListEntry(HelpfulCommandsCommand command, boolean enabled, boolean hasPerms) {
        private boolean canUse() {
            return enabled && hasPerms;
        }
    }

    public HcCommand(CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        CommandData commandData = getCommandData();

        LiteralArgumentBuilder<CommandSourceStack> configField = Commands.literal("field");
        configField.requires(src -> PermissionHelper.canConfigure(src, "field"));
        for (Map.Entry<String, ConfigManager.ConfigFieldProperties> entry : ConfigManager.DEFAULT_FIELDS.entrySet()) {
            String name = entry.getKey();
            ConfigManager.ConfigFieldProperties properties = entry.getValue();

            LiteralArgumentBuilder<CommandSourceStack> field = Commands.literal(entry.getKey());

            LiteralArgumentBuilder<CommandSourceStack> setArgument = Commands.literal("set");

            switch (properties.valueType) {
                case ConfigManager.ConfigFieldProperties.ValueType.Double:
                    setArgument.then(Commands.argument("value", DoubleArgumentType.doubleArg(properties.min, properties.max))
                            .executes(ctx -> setConfigField(ctx, name, DoubleArgumentType.getDouble(ctx, "value")))
                    );
                    break;
                case ConfigManager.ConfigFieldProperties.ValueType.Integer:
                    setArgument.then(Commands.argument("value", IntegerArgumentType.integer(properties.min.intValue(), properties.max.intValue()))
                            .executes(ctx -> setConfigField(ctx, name, IntegerArgumentType.getInteger(ctx, "value")))
                    );
                    break;
                case ConfigManager.ConfigFieldProperties.ValueType.Boolean:
                    setArgument.then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> setConfigField(ctx, name, BoolArgumentType.getBool(ctx, "value")))
                    );
                    break;
            }

            field
                    .then(setArgument)
                    .then(Commands.literal("reset")
                            .executes(ctx -> setConfigField(ctx, name, properties.defaultValue))
                    )
                    .then(Commands.literal("query")
                            .executes(ctx -> queryConfigField(ctx, name))
                    );
            configField.then(field);
        }

        dispatcher.register(Commands.literal(commandData.getName())
                .executes(this::about)
                .then(Commands.literal("about")
                        .executes(this::about)
                )
                .then(Commands.literal("commandList")
                        .executes(ctx -> commandList(ctx, dispatcher, null))
                        .then(Commands.argument("show_all", BoolArgumentType.bool())
                                .requires(src -> !PermissionHelper.canConfigure(src, "command.state") && src.isPlayer())
                                .executes(ctx -> commandList(ctx, dispatcher, BoolArgumentType.getBool(ctx, "show_all")))
                        )
                )
                .then(Commands.literal("config")
                        .requires(PermissionHelper::canConfigure)
                        .executes(this::config)
                        .then(configField)
                        .then(Commands.literal("command")
                                .requires(src -> PermissionHelper.canConfigure(src, "command") || PermissionHelper.canConfigure(src, "command.state"))
                                .then(Commands.literal("state")
                                        .then(Commands.argument("command", StringArgumentType.word())
                                                .requires(src -> PermissionHelper.canConfigure(src, "command.state"))
                                                .executes(ctx -> queryCommandState(ctx, StringArgumentType.getString(ctx, "command")))
                                                .suggests(new HelpfulCommandsCommandSuggestionProvider())
                                                .then(Commands.argument("new_state", BoolArgumentType.bool())
                                                        .executes(ctx -> setCommandState(ctx, StringArgumentType.getString(ctx, "command"), BoolArgumentType.getBool(ctx, "new_state")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("styling")
                                .requires(src -> PermissionHelper.canConfigure(src, "styling"))
                                .then(Commands.literal("style")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("style_name", StringArgumentType.word())
                                                        .suggests(new StyleSuggestionProvider())
                                                        .executes(ctx -> setStyle(ctx, StringArgumentType.getString(ctx, "style_name")))
                                                )
                                        )
                                        .then(Commands.literal("query")
                                                .executes(this::queryStyle)
                                        )
                                )
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return true;
    }

    private int about(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        boolean isPlayer = src.isPlayer();

        HelpfulCommandsStyle hcStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = hcStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = hcStyle.getTextDecorators();

        HoverEvent linkHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToOpenTheLink")));

        Component quickActionCommandListBtn = StylingHelper.getButton("\uD83D\uDCC3", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions.commandList")), new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.hover.commandList"))), new ClickEvent.RunCommand("/hc commandList"));
        Component quickActionConfigureBtn = StylingHelper.getButton("\uD83D\uDD27", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions.config")), new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.hover.config"))), new ClickEvent.RunCommand("/hc config"));

        String linkSeparator = isPlayer ? " " + textDecorators.getBulletPoint() : "\n";
        String authorLink = "https://expecticament.com";
        String docsLink = "https://helpfulcommands.expecticament.com";
        String gitHubLink = "https://github.com/Expecticament/helpful-commands";
        String curseForgeLink = "https://www.curseforge.com/minecraft/mc-mods/helpful-commands";
        String modrinthLink = "https://modrinth.com/mod/helpful-commands";
        Component authorComponent;
        Component docsComponent;
        Component gitHubComponent;
        Component curseForgeComponent;
        Component modrinthComponent;
        if (isPlayer) {
            authorComponent = Component.literal("Expecticament").setStyle(textStyles.getSecondary().withUnderlined(true).withHoverEvent(linkHoverEvent).withClickEvent(new ClickEvent.OpenUrl(URI.create(authorLink))));
            docsComponent = Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.docs")).setStyle(textStyles.getSecondary().withUnderlined(true).withHoverEvent(linkHoverEvent).withClickEvent(new ClickEvent.OpenUrl(URI.create(docsLink))));
            gitHubComponent = Component.literal("GitHub").setStyle(textStyles.getSecondary().withUnderlined(true).withHoverEvent(linkHoverEvent).withClickEvent(new ClickEvent.OpenUrl(URI.create(gitHubLink))));
            curseForgeComponent = Component.literal("CurseForge").setStyle(textStyles.getSecondary().withUnderlined(true).withHoverEvent(linkHoverEvent).withClickEvent(new ClickEvent.OpenUrl(URI.create(curseForgeLink))));
            modrinthComponent = Component.literal("Modrinth").setStyle(textStyles.getSecondary().withUnderlined(true).withHoverEvent(linkHoverEvent).withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthLink))));
        } else {
            authorComponent = Component.literal("Expecticament (%s)".formatted(authorLink));
            docsComponent = Component.literal("\n" + TranslationManager.translate(src, "commands.helpful_commands.hc.about.docs")).append(": %s".formatted(docsLink));
            gitHubComponent = Component.literal("GitHub: %s".formatted(gitHubLink));
            curseForgeComponent = Component.literal("CurseForge: %s".formatted(curseForgeLink));
            modrinthComponent = Component.literal("Modrinth: %s".formatted(modrinthLink));
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder
                .appendComponent(StylingHelper.getTitle(Component.literal("Helpful Commands"), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.title"))))
                .appendNewline()
                .appendWhitespace()
                .appendTranslatable("modmenu.descriptionTranslation.helpful_commands")
                .appendNewline()
                .appendNewline()
                .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.version")).setStyle(textStyles.getTertiary()))
                .appendWhitespace()
                .appendComponent(Component.literal(HelpfulCommands.getModVersion()).setStyle(textStyles.getSecondary()))
                .appendNewline()
                .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.author")).setStyle(textStyles.getTertiary()))
                .appendWhitespace()
                .appendComponent(authorComponent)
                .appendNewline()
                .appendNewline()
                .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.links")).setStyle(textStyles.getTertiary()))
                .appendWhitespace()
                .appendComponent(docsComponent)
                .appendLiteral(linkSeparator)
                .appendComponent(gitHubComponent)
                .appendLiteral(linkSeparator)
                .appendComponent(curseForgeComponent)
                .appendLiteral(linkSeparator)
                .appendComponent(modrinthComponent)
                .appendNewline()
                .appendNewline()
                .appendComponent(Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary()))
                .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions")).setStyle(textStyles.getTertiary()));

        if (isPlayer) {
            textBuilder
                    .appendWhitespace()
                    .appendComponent(quickActionCommandListBtn);

            if (PermissionHelper.canConfigure(src)) {
                textBuilder
                        .appendWhitespace()
                        .appendComponent(quickActionConfigureBtn);
            }
        } else {
            textBuilder
                    .appendNewline()
                    .appendTranslatable("commands.helpful_commands.hc.about.actions.commandList")
                    .appendLiteral(": /hc commandList")
                    .appendNewline()
                    .appendTranslatable("commands.helpful_commands.hc.about.actions.config")
                    .appendLiteral(": /hc config");
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private int commandList(CommandContext<CommandSourceStack> ctx, CommandDispatcher<CommandSourceStack> dispatcher, Boolean showAll) {
        CommandSourceStack src = ctx.getSource();

        if (showAll == null) {
            showAll = !src.isPlayer() || PermissionHelper.canConfigure(src, "command.state");
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendComponent(StylingHelper.getTitle(Component.literal("Helpful Commands"), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.commandList.title"))));

        return showAll ? commandListAll(ctx, dispatcher, textBuilder) : commandListAvailableOnly(ctx, dispatcher, textBuilder);
    }

    private int commandListAll(CommandContext<CommandSourceStack> ctx, CommandDispatcher<CommandSourceStack> dispatcher, TextBuilder textBuilder) {
        CommandSourceStack src = ctx.getSource();

        HelpfulCommandsStyle hcStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = hcStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = hcStyle.getTextDecorators();
        Style categoryStyle = textStyles.getTertiary();

        boolean toggleStateCommandPermission = PermissionHelper.canConfigure(src, "command.state");

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        for (Map.Entry<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> entry : ModCommandManager.getCommandListByCategory().entrySet()) {
            if (entry.getKey().equals(ModCommandManager.CommandCategory.MAIN)) {
                continue;
            }

            textBuilder.appendNewline().appendComponent(Component.literal(textDecorators.getCategoryStartingChar()).setStyle(categoryStyle)).appendComponent(Component.literal(TranslationManager.translate(src, "command.category.helpful_commands." + entry.getKey().toString().toLowerCase())).setStyle(categoryStyle));
            HelpfulCommandsCommand lastCommand = entry.getValue().getLast();

            for (HelpfulCommandsCommand command : entry.getValue()) {
                CommandData cmdData = command.getCommandData();

                boolean enabled = config.getCommandState(cmdData.getName());
                boolean hasPerms = command.canExecuteBaseCommand(src);
                boolean canUse = enabled && hasPerms;

                textBuilder.appendNewline().appendComponent(Component.literal(command.equals(lastCommand) ? textDecorators.getCategoryEndingChar() : textDecorators.getCategoryTrailingChar()).setStyle(categoryStyle));

                if (toggleStateCommandPermission) {
                    HoverEvent cmdStateHoverEvent = getCommandStateHoverEvent(src, textStyles, cmdData.getName(), enabled);
                    ClickEvent cmdStateClickEvent = new ClickEvent.RunCommand("/hc config command state " + cmdData.getName() + " " + String.valueOf(!enabled).toLowerCase());
                    Style cmdStateStyle = (enabled ? textStyles.getEnabled() : textStyles.getDisabled()).withHoverEvent(cmdStateHoverEvent).withClickEvent(cmdStateClickEvent);

                    textBuilder.appendComponent(StylingHelper.getButton(Component.literal(enabled ? textDecorators.getEnabled() : textDecorators.getDisabled()), cmdStateStyle)).appendWhitespace();
                }

                HoverEvent cmdNameHoverEvent = getCommandHoverEvent(src, dispatcher, textStyles, command, hasPerms, enabled);
                ClickEvent cmdNameClickEvent = canUse ? new ClickEvent.SuggestCommand("/" + cmdData.getName() + " ") : null;
                Style cmdNameStyle = (canUse ? textStyles.getAvailable() : textStyles.getUnavailable()).withHoverEvent(cmdNameHoverEvent).withClickEvent(cmdNameClickEvent);

                textBuilder.appendComponent(Component.literal("/" + cmdData.getName()).setStyle(cmdNameStyle));
            }
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private int commandListAvailableOnly(CommandContext<CommandSourceStack> ctx, CommandDispatcher<CommandSourceStack> dispatcher, TextBuilder textBuilder) {
        CommandSourceStack src = ctx.getSource();

        if (!src.isPlayer()) {
            return commandListAvailableOnly(ctx, dispatcher, textBuilder);
        }

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        Map<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> available = new LinkedHashMap<>();

        int totalCommands = 0;
        int availableCommands = 0;

        for (Map.Entry<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> entry : ModCommandManager.getCommandListByCategory().entrySet()) {
            if (entry.getKey().equals(ModCommandManager.CommandCategory.MAIN)) {
                continue;
            }

            totalCommands += entry.getValue().size();

            List<HelpfulCommandsCommand> filtered = entry.getValue().stream().filter(cmd -> config.getCommandState(cmd.getCommandData().getName())).filter(cmd -> cmd.canExecuteBaseCommand(src)).toList();
            if (!filtered.isEmpty()) {
                available.put(entry.getKey(), filtered);
                availableCommands += filtered.size();
            }
        }

        if (available.isEmpty() || availableCommands == totalCommands) {
            return commandListAll(ctx, dispatcher, textBuilder);
        }

        HelpfulCommandsStyle hcStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = hcStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = hcStyle.getTextDecorators();
        Style categoryStyle = textStyles.getTertiary();

        textBuilder
                .appendNewline()
                .appendComponent(Component.literal("[!] ").setStyle(textStyles.getWarning()))
                .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.commandList.availableOnly")).setStyle(textStyles.getWarning()))
                .appendWhitespace()
                .appendComponent(StylingHelper.getButton(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.commandList.availableOnly.showAll")), textStyles.getButton().withClickEvent(new ClickEvent.RunCommand("/hc commandList true"))))
                .appendNewline();

        for (Map.Entry<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> entry : available.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            textBuilder.appendNewline().appendComponent(Component.literal(textDecorators.getCategoryStartingChar()).setStyle(categoryStyle)).appendComponent(Component.literal(TranslationManager.translate(src, "command.category.helpful_commands." + entry.getKey().toString().toLowerCase())).setStyle(categoryStyle));
            HelpfulCommandsCommand lastCommand = entry.getValue().getLast();

            for (HelpfulCommandsCommand command : entry.getValue()) {
                CommandData cmdData = command.getCommandData();

                textBuilder.appendNewline().appendComponent(Component.literal(command.equals(lastCommand) ? textDecorators.getCategoryEndingChar() : textDecorators.getCategoryTrailingChar()).setStyle(categoryStyle));
                HoverEvent cmdNameHoverEvent = getCommandHoverEvent(src, dispatcher, textStyles, command, true, true);
                ClickEvent cmdNameClickEvent = new ClickEvent.SuggestCommand("/" + cmdData.getName() + " ");
                Style cmdNameStyle = (textStyles.getAvailable()).withHoverEvent(cmdNameHoverEvent).withClickEvent(cmdNameClickEvent);

                textBuilder.appendComponent(Component.literal("/" + cmdData.getName()).setStyle(cmdNameStyle));
            }
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private HoverEvent getCommandHoverEvent(CommandSourceStack src, CommandDispatcher<CommandSourceStack> dispatcher, HelpfulCommandsStyle.TextStyles textStyles, HelpfulCommandsCommand command, boolean hasPerms, boolean enabled) {
        String commandName = command.getCommandData().getName();

        boolean canUse = enabled && hasPerms;

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder
                .appendComponent(Component.literal("/" + commandName).setStyle(canUse ? textStyles.getAvailable() : textStyles.getUnavailable()))
                .appendNewline()
                .appendTranslatable("commands.helpful_commands." + commandName + ".description");

        if (canUse) {
            MutableComponent usagesText = Component.empty().setStyle(textStyles.getSubtle());
            CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(commandName);

            if (node != null) {
                Map<CommandNode<CommandSourceStack>, String> usages = dispatcher.getSmartUsage(node, src);
                if (!usages.isEmpty()) {
                    usagesText.append("\n");
                    for (String usage : usages.values()) {
                        usagesText.append(Component.literal("\n/" + commandName + " " + usage));
                    }
                }
            }

            textBuilder.appendComponent(usagesText);
        } else {
            textBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(Component.literal(TranslationManager.translate(src,"commands.helpful_commands.hc.commandList.cantUse")).setStyle(textStyles.getUnavailable()))
                    .appendWhitespace()
                    .appendComponent(Component.literal(TranslationManager.translate(src, !enabled ? "error.helpful_commands.commandDisabled" : "error.helpful_commands.notPermitted").toLowerCase()).setStyle(textStyles.getUnavailable()));
        }

        return new HoverEvent.ShowText(textBuilder.getComponent());
    }

    private HoverEvent getCommandStateHoverEvent(CommandSourceStack source, HelpfulCommandsStyle.TextStyles textStyles, String commandName, boolean currentState) {
        TextBuilder textBuilder = new TextBuilder(source);
        textBuilder
                .appendComponent(Component.literal(TranslationManager.translate(source, "helpful_commands.common." + (currentState ? "enabled" : "disabled"))).setStyle(currentState ? textStyles.getEnabled() : textStyles.getDisabled()))
                .appendNewline()
                .appendNewline()
                .appendTranslatable("commands.helpful_commands.hc.commandList.clickToToggleCommand", Component.literal(TranslationManager.translate(source, "commands.helpful_commands.hc.commandList.clickToToggleCommand." + String.valueOf(currentState).toLowerCase())), Component.literal("/" + commandName).setStyle(textStyles.getPrimary()));

        return new HoverEvent.ShowText(textBuilder.getComponent());
    }

    private int config(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        boolean isPlayer = src.isPlayer();

        HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = currentStyle.getTextDecorators();

        Component bulletPoint = Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary());

        TextBuilder textBuilder = new TextBuilder(src);

        textBuilder.appendComponent(StylingHelper.getTitle(Component.literal("Helpful Commands"), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.title"))));

        if (PermissionHelper.canConfigure(src, "command") || PermissionHelper.canConfigure(src, "command.state")) {
            textBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(bulletPoint)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.command.title")).setStyle(textStyles.getTertiary()))
                    .appendNewline()
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.hc.config.command.description", Component.literal("/hc config command").setStyle(textStyles.getSecondary().withClickEvent(new ClickEvent.SuggestCommand("/hc config command ")).withHoverEvent(new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToPasteCommand"))))));
            if (isPlayer) {
                Component commandListBtn = StylingHelper.getButton("\uD83D\uDCC3", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions.commandList")), new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.hover.commandList"))), new ClickEvent.RunCommand("/hc commandList"));
                textBuilder
                        .appendNewline()
                        .appendWhitespace()
                        .appendTranslatable("Tip: Use %s for quick and easy command configuration", commandListBtn);
            }
        }

        if (PermissionHelper.canConfigure(src, "field")) {
            textBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(bulletPoint)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.title")).setStyle(textStyles.getTertiary()))
                    .appendNewline()
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.hc.config.field.description", Component.literal("/hc config field").setStyle(textStyles.getSecondary().withClickEvent(new ClickEvent.SuggestCommand("/hc config field ")).withHoverEvent(new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToPasteCommand"))))));
        }

        if (PermissionHelper.canConfigure(src, "styling")) {
            textBuilder
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(bulletPoint)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.styling.title")).setStyle(textStyles.getTertiary()))
                    .appendNewline()
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.hc.config.styling.description", Component.literal("/hc config styling").setStyle(textStyles.getSecondary().withClickEvent(new ClickEvent.SuggestCommand("/hc config styling ")).withHoverEvent(new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToPasteCommand"))))));
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private int setStyle(CommandContext<CommandSourceStack> ctx, String styleName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();
        Component styleNameText = Component.literal(styleName).setStyle(textStyles.getPrimary());

        if (currentStyle.getDisplayName().equals(styleName)) {
            throw new CommandSyntaxException(STYLE_ALREADY_IN_USE, new TextBuilder(src).appendTranslatable("commands.helpful_commands.hc.config.styling.style.set.error.styleAlreadyInUse", styleNameText).getComponent());
        }

        try {
            StylingManager.setCurrentStyle(styleName);

            currentStyle = StylingManager.getCurrentStyle();
            textStyles = currentStyle.getTextStyles();

            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.hc.config.styling.style.set", styleNameText).setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);

            return Command.SINGLE_SUCCESS;
        } catch (StylingManager.StyleDoesntExistException e) {
            throw new CommandSyntaxException(STYLE_DOESNT_EXIST, new TextBuilder(src).appendTranslatable("commands.helpful_commands.hc.config.styling.style.set.error.styleDoesntExist", styleNameText).getComponent());
        }
    }

    private int queryStyle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.config.styling.style.query", Component.literal(currentStyle.getDisplayName()).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int setCommandState(CommandContext<CommandSourceStack> ctx, String command, boolean newState) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        boolean valid = false;
        for (HelpfulCommandsCommand hcCmd : ModCommandManager.getCommandList()) {
            CommandData data = hcCmd.getCommandData();
            if (data.getName().equals(command)) {
                if (data.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                    TextBuilder textBuilder = new TextBuilder(src);
                    textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.error.commandNotConfigurable", Component.literal(command).setStyle(textStyles.getPrimary()));
                    throw new CommandSyntaxException(COMMAND_NOT_CONFIGURABLE, textBuilder.getComponent());
                }
                valid = true;
                break;
            }
        }

        if (!valid) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.error.invalidCommand", Component.literal(command).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(INVALID_HC_COMMAND, textBuilder.getComponent());
        }

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        if (config.setCommandState(command, newState)) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.state.set", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.command.state." + String.valueOf(newState).toLowerCase())), Component.literal("/" + command).setStyle(textStyles.getPrimary()));
            textBuilder.setStyle(newState ? textStyles.getEnabled() : textStyles.getDisabled());

            ModCommandManager.sendCommandsToEveryone(src);

            src.sendSuccess(textBuilder::getComponent, true);

            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }

    private int queryCommandState(CommandContext<CommandSourceStack> ctx, String command) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        boolean valid = false;
        for (HelpfulCommandsCommand hcCmd : ModCommandManager.getCommandList()) {
            CommandData data = hcCmd.getCommandData();
            if (data.getName().equals(command)) {
                if (data.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                    TextBuilder textBuilder = new TextBuilder(src);
                    textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.error.commandNotConfigurable", Component.literal("/" + command).setStyle(textStyles.getPrimary()));
                    throw new CommandSyntaxException(COMMAND_NOT_CONFIGURABLE, textBuilder.getComponent());
                }
                valid = true;
                break;
            }
        }

        if (!valid) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.error.invalidCommand", Component.literal("/" + command).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(INVALID_HC_COMMAND, textBuilder.getComponent());
        }

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        boolean state = config.getCommandState(command);

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.config.command.state.query", Component.literal("/" + command).setStyle(textStyles.getPrimary()), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.command.state." + String.valueOf(state).toLowerCase()).toLowerCase()));
        textBuilder.setStyle(state ? textStyles.getEnabled() : textStyles.getDisabled());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int setConfigField(CommandContext<CommandSourceStack> ctx, String field, Object newValue) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        config.writeField(field, newValue);

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.config.field.set", Component.literal(field).setStyle(textStyles.getPrimary()), Component.literal(String.valueOf(newValue)).setStyle(textStyles.getPrimary()));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int queryConfigField(CommandContext<CommandSourceStack> ctx, String field) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        Object value = config.readField(field);

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.config.field.query", Component.literal(field).setStyle(textStyles.getPrimary()), Component.literal(String.valueOf(value)).setStyle(textStyles.getPrimary()));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
