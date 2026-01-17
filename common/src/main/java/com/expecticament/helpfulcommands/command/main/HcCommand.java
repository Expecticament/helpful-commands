package com.expecticament.helpfulcommands.command.main;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.*;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.HelpfulCommandsCommandSuggestionProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.net.URI;
import java.util.*;

public class HcCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType INVALID_HC_COMMAND = new Dynamic2CommandExceptionType((src, commandName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.hc.config.command.error.invalidHcCommand", Component.literal(commandName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final Dynamic2CommandExceptionType COMMAND_NOT_CONFIGURABLE = new Dynamic2CommandExceptionType((src, commandName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.hc.config.command.error.commandNotConfigurable", Component.literal(commandName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final DynamicCommandExceptionType NO_ACTIVE_COOLDOWNS_FOUND = new DynamicCommandExceptionType((src) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.hc.cooldowns.error.noActiveCooldownsFound").getComponent()
    );

    public HcCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        LiteralArgumentBuilder<CommandSourceStack> configFieldArgumentBuilder = Commands.literal("field");
        configFieldArgumentBuilder.requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_FIELD));
        configFieldArgumentBuilder.executes(this::configField);
        for (ConfigManager.CONFIG_FIELD configField : ConfigManager.CONFIG_FIELD.values()) {
            String name = configField.name().toLowerCase();
            ConfigManager.ConfigFieldProperties properties = configField.properties();

            LiteralArgumentBuilder<CommandSourceStack> field = Commands.literal(name);

            LiteralArgumentBuilder<CommandSourceStack> setArgument = Commands.literal("set");

            switch (properties.getValueType()) {
                case ConfigManager.ConfigFieldProperties.ValueType.Double:
                    setArgument.then(Commands.argument("new_value", DoubleArgumentType.doubleArg(properties.getMin(), properties.getMax()))
                            .executes(ctx -> setConfigField(ctx, name, DoubleArgumentType.getDouble(ctx, "new_value")))
                    );
                    break;
                case ConfigManager.ConfigFieldProperties.ValueType.Integer:
                    setArgument.then(Commands.argument("new_value", IntegerArgumentType.integer(properties.getMin().intValue(), properties.getMax().intValue()))
                            .executes(ctx -> setConfigField(ctx, name, IntegerArgumentType.getInteger(ctx, "new_value")))
                    );
                    break;
                case ConfigManager.ConfigFieldProperties.ValueType.Boolean:
                    setArgument.then(Commands.argument("new_value", BoolArgumentType.bool())
                            .executes(ctx -> setConfigField(ctx, name, BoolArgumentType.getBool(ctx, "new_value")))
                    );
                    break;
            }

            field
                    .then(setArgument)
                    .then(Commands.literal("reset")
                            .executes(ctx -> setConfigField(ctx, name, properties.getDefaultValue()))
                    )
                    .then(Commands.literal("query")
                            .executes(ctx -> queryConfigField(ctx, name))
                    );
            configFieldArgumentBuilder.then(field);
        }

        LiteralArgumentBuilder<CommandSourceStack> cooldownsClearArgumentBuilder = Commands.literal("clear");
        cooldownsClearArgumentBuilder.requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_COOLDOWNS_CLEAR));
        for (CooldownManager.CooldownType cooldownType : CooldownManager.CooldownType.values()) {
            String name = cooldownType.name().toLowerCase();

            cooldownsClearArgumentBuilder.then(Commands.literal(name)
                    .executes(ctx -> clearCooldown(ctx, cooldownType))
                    .then(Commands.argument("players", EntityArgument.players())
                            .executes(ctx -> clearCooldown(ctx, cooldownType, EntityArgument.getPlayers(ctx, "players")))
                    )
            );
        }

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::about)
                .then(Commands.literal("about")
                        .executes(this::about)
                )
                .then(Commands.literal("commands")
                        .executes(ctx -> commandList(ctx, dispatcher, null))
                        .then(Commands.argument("show_all", BoolArgumentType.bool())
                                .requires(src -> src.isPlayer() && (!PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND_STATE) || !singleplayerOwnerCheck(src)))
                                .executes(ctx -> commandList(ctx, dispatcher, BoolArgumentType.getBool(ctx, "show_all")))
                        )
                )
                .then(Commands.literal("config")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG) && singleplayerOwnerCheck(src))
                        .executes(this::config)
                        .then(configFieldArgumentBuilder)
                        .then(Commands.literal("command")
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND))
                                .then(Commands.literal("state")
                                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND_STATE))
                                        .then(Commands.argument("hc_command", StringArgumentType.word())
                                                .executes(ctx -> queryCommandState(ctx, StringArgumentType.getString(ctx, "hc_command")))
                                                .suggests(new HelpfulCommandsCommandSuggestionProvider())
                                                .then(Commands.argument("new_state", BoolArgumentType.bool())
                                                        .executes(ctx -> setCommandState(ctx, StringArgumentType.getString(ctx, "hc_command"), BoolArgumentType.getBool(ctx, "new_state")))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("cooldowns")
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_COOLDOWNS) && singleplayerOwnerCheck(src))
                        .then(cooldownsClearArgumentBuilder)
                        .then(Commands.literal("clear_all")
                                .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_COOLDOWNS_CLEARALL))
                                .executes(this::clearAllCooldowns)
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return true;
    }

    private boolean singleplayerOwnerCheck(CommandSourceStack source) {
        if (HelpfulCommands.isDedicatedServer()) {
            return true;
        }

        ServerPlayer sourcePlayer = source.getPlayer();

        return sourcePlayer != null && source.getServer().isSingleplayerOwner(new NameAndId(sourcePlayer.getGameProfile()));
    }

    private int about(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        boolean isPlayer = src.isPlayer();

        HelpfulCommandsStyle hcStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = hcStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = hcStyle.getTextDecorators();

        HoverEvent linkHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToOpenTheLink")));

        Component quickActionCommandListBtn = StylingHelper.getButton("\uD83D\uDCC3", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions.commandList")), new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.hover.commandList"))), new ClickEvent.RunCommand("/hc commands"));
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

            if (PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG) && singleplayerOwnerCheck(src)) {
                textBuilder
                        .appendWhitespace()
                        .appendComponent(quickActionConfigureBtn);
            }
        } else {
            textBuilder
                    .appendNewline()
                    .appendTranslatable("commands.helpful_commands.hc.about.actions.commandList")
                    .appendLiteral(": /hc commands")
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
            showAll = !src.isPlayer() || (PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND_STATE) && singleplayerOwnerCheck(src));
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

        boolean toggleStateCommandPermission = PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND_STATE) && singleplayerOwnerCheck(src);

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        for (Map.Entry<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> entry : ModCommandManager.getCommandListByCategory().entrySet()) {
            if (entry.getKey().equals(ModCommandManager.CommandCategory.MAIN)) {
                continue;
            }

            textBuilder.appendNewline().appendComponent(Component.literal(textDecorators.getCategoryStartingChar()).setStyle(categoryStyle)).appendComponent(Component.literal(TranslationManager.translate(src, "helpful_commands.commandCategory.%s".formatted(entry.getKey().toString().toLowerCase()))).setStyle(categoryStyle));
            HelpfulCommandsCommand lastCommand = entry.getValue().getLast();

            for (HelpfulCommandsCommand command : entry.getValue()) {
                ModCommandManager.ModCommand modCommand = command.getModCommand();

                boolean enabled = config.getCommandState(modCommand.getName());
                boolean hasPerms = command.canExecute(src);
                boolean canUse = enabled && hasPerms;

                textBuilder.appendNewline().appendComponent(Component.literal(command.equals(lastCommand) ? textDecorators.getCategoryEndingChar() : textDecorators.getCategoryTrailingChar()).setStyle(categoryStyle));

                if (toggleStateCommandPermission) {
                    HoverEvent cmdStateHoverEvent = getCommandStateHoverEvent(src, textStyles, modCommand.getName(), enabled);
                    ClickEvent cmdStateClickEvent = new ClickEvent.RunCommand("/hc config command state " + modCommand.getName() + " " + String.valueOf(!enabled).toLowerCase());
                    Style cmdStateStyle = (enabled ? textStyles.getEnabled() : textStyles.getDisabled()).withHoverEvent(cmdStateHoverEvent).withClickEvent(cmdStateClickEvent);

                    textBuilder.appendComponent(StylingHelper.getButton(Component.literal(enabled ? textDecorators.getCheckmark() : textDecorators.getCross()), cmdStateStyle)).appendWhitespace();
                }

                HoverEvent cmdNameHoverEvent = getCommandHoverEvent(src, dispatcher, textStyles, command, hasPerms, enabled);
                ClickEvent cmdNameClickEvent = canUse ? new ClickEvent.SuggestCommand("/" + modCommand.getName() + " ") : null;
                Style cmdNameStyle = (canUse ? textStyles.getAvailable() : textStyles.getUnavailable()).withHoverEvent(cmdNameHoverEvent).withClickEvent(cmdNameClickEvent);

                textBuilder.appendComponent(Component.literal("/" + modCommand.getName()).setStyle(cmdNameStyle));
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

            List<HelpfulCommandsCommand> filtered = entry.getValue().stream().filter(cmd -> config.getCommandState(cmd.getModCommand().getName())).filter(cmd -> cmd.canExecute(src)).toList();
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
                .appendComponent(StylingHelper.getButton(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.commandList.availableOnly.showAll")), textStyles.getButton().withClickEvent(new ClickEvent.RunCommand("/hc commands true"))))
                .appendNewline();

        for (Map.Entry<ModCommandManager.CommandCategory, List<HelpfulCommandsCommand>> entry : available.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            textBuilder.appendNewline().appendComponent(Component.literal(textDecorators.getCategoryStartingChar()).setStyle(categoryStyle)).appendComponent(Component.literal(TranslationManager.translate(src, "helpful_commands.commandCategory.%s".formatted(entry.getKey().toString().toLowerCase()))).setStyle(categoryStyle));
            HelpfulCommandsCommand lastCommand = entry.getValue().getLast();

            for (HelpfulCommandsCommand command : entry.getValue()) {
                ModCommandManager.ModCommand modCommand = command.getModCommand();

                textBuilder.appendNewline().appendComponent(Component.literal(command.equals(lastCommand) ? textDecorators.getCategoryEndingChar() : textDecorators.getCategoryTrailingChar()).setStyle(categoryStyle));
                HoverEvent cmdNameHoverEvent = getCommandHoverEvent(src, dispatcher, textStyles, command, true, true);
                ClickEvent cmdNameClickEvent = new ClickEvent.SuggestCommand("/" + modCommand.getName() + " ");
                Style cmdNameStyle = (textStyles.getAvailable()).withHoverEvent(cmdNameHoverEvent).withClickEvent(cmdNameClickEvent);

                textBuilder.appendComponent(Component.literal("/" + modCommand.getName()).setStyle(cmdNameStyle));
            }
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private HoverEvent getCommandHoverEvent(CommandSourceStack src, CommandDispatcher<CommandSourceStack> dispatcher, HelpfulCommandsStyle.TextStyles textStyles, HelpfulCommandsCommand command, boolean hasPerms, boolean enabled) {
        String commandName = command.getModCommand().getName();

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

        boolean commandConfig = PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_COMMAND) && singleplayerOwnerCheck(src);
        if (commandConfig) {
            textBuilder
                    .appendNewline()
                    .appendComponent(bulletPoint)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.command.title")).setStyle(textStyles.getTertiary()))
                    .appendNewline()
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.hc.config.command.description", Component.literal("/hc config command").setStyle(textStyles.getSecondary().withClickEvent(new ClickEvent.SuggestCommand("/hc config command ")).withHoverEvent(new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToPasteCommand"))))));
            if (isPlayer) {
                Component commandListBtn = StylingHelper.getButton("\uD83D\uDCC3", Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.actions.commandList")), new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.about.hover.commandList"))), new ClickEvent.RunCommand("/hc commands"));
                textBuilder
                        .appendNewline()
                        .appendWhitespace()
                        .appendTranslatable("Tip: Use %s for quick and easy command configuration", commandListBtn);
            }
        }

        if (PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_HC_CONFIG_FIELD) && singleplayerOwnerCheck(src)) {
            if (commandConfig) {
                textBuilder.appendNewline();
            }
            textBuilder
                    .appendNewline()
                    .appendComponent(bulletPoint);
            if (isPlayer) {
                Style btnStyle = textStyles.getPrimary().withClickEvent(new ClickEvent.RunCommand("/hc config field"));
                Component btn = StylingHelper.getButton(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.title")), btnStyle);
                textBuilder
                        .appendComponent(btn)
                        .appendNewline()
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.hc.config.field.description");
            } else {
                textBuilder
                        .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.title")).setStyle(textStyles.getTertiary()))
                        .appendNewline()
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.hc.config.field.description.use", Component.literal("/hc config field").setStyle(textStyles.getSecondary().withClickEvent(new ClickEvent.SuggestCommand("/hc config field ")).withHoverEvent(new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToPasteCommand"))))))
                        .appendNewline()
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.hc.config.field.description");
            }
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private int configField(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        boolean isPlayer = src.isPlayer();

        HelpfulCommandsStyle currentStyle = StylingManager.getCurrentStyle();
        HelpfulCommandsStyle.TextStyles textStyles = currentStyle.getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = currentStyle.getTextDecorators();

        Component bulletPoint = Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary());

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendComponent(StylingHelper.getTitle(Component.literal("Helpful Commands"), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.title"))));

        boolean luckPermsAvailable = PermissionHelper.isLuckPermsAvailable();

        for (ConfigManager.CONFIG_FIELD configField : ConfigManager.CONFIG_FIELD.values()) {
            String name = configField.name().toLowerCase();
            ConfigManager.ConfigFieldProperties properties = configField.properties();
            Object value = config.readField(configField);

            TextBuilder fieldNameTextBuilder = new TextBuilder(src);
            fieldNameTextBuilder
                    .appendComponent(Component.literal(name).setStyle(textStyles.getTertiary()))
                    .appendNewline()
                    .appendLiteral(TranslationManager.translate(src, "%s.configField.%s".formatted(HelpfulCommands.MOD_ID, name)))
                    .appendNewline()
                    .appendNewline()
                    .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.defaultValue")).setStyle(textStyles.getSubtle()))
                    .appendWhitespace()
                    .appendComponent(Component.literal(String.valueOf(properties.getDefaultValue())).setStyle(textStyles.getPrimary()));
            Component fieldNameComponent = Component.literal(name).setStyle(textStyles.getTertiary().withHoverEvent(new HoverEvent.ShowText(fieldNameTextBuilder.getComponent())));

            textBuilder
                    .appendNewline()
                    .appendComponent(bulletPoint)
                    .appendComponent(fieldNameComponent)
                    .appendLiteral(": ")
                    .appendComponent(Component.literal(String.valueOf(value)).setStyle(textStyles.getPrimary()));

            if (isPlayer) {
                HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToEdit")));
                ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/hc config field %s set ".formatted(name));
                Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);

                HoverEvent resetBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.hover.clickToReset")));
                ClickEvent resetBtnClickEvent = new ClickEvent.RunCommand("/hc config field %s reset".formatted(name));
                Style resetBtnStyle = textStyles.getDangerousAction().withHoverEvent(resetBtnHoverEvent).withClickEvent(resetBtnClickEvent);

                textBuilder
                        .appendWhitespace()
                        .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle))
                        .appendWhitespace()
                        .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getReset()), resetBtnStyle));
            }

            if (luckPermsAvailable && configField.lpMetaSupport()) {
                String metaPermId = "meta.%s_%s.(value)".formatted(HelpfulCommands.SHORT_MOD_ID, name);
                Style lpStyle = Style.EMPTY.applyFormat(ChatFormatting.GREEN);

                TextBuilder metaTextBuilder = new TextBuilder(src);
                metaTextBuilder
                        .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.lpMeta")).setStyle(lpStyle))
                        .appendNewline()
                        .appendTranslatable("commands.helpful_commands.hc.config.field.lpMeta.permissionID", Component.literal(metaPermId).setStyle(textStyles.getPrimary()))
                        .appendNewline()
                        .appendNewline()
                        .appendComponent(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.config.field.lpMeta.fallback")).setStyle(textStyles.getSubtle()));
                HoverEvent metaBtnHoverEvent = new HoverEvent.ShowText(metaTextBuilder.getComponent());

                ClickEvent metaBtnClickEvent = new ClickEvent.CopyToClipboard(metaPermId);
                Style metaBtnStyle = lpStyle.withHoverEvent(metaBtnHoverEvent).withClickEvent(metaBtnClickEvent);

                textBuilder
                        .appendWhitespace()
                        .appendComponent(StylingHelper.getButton(Component.literal("🍀"), metaBtnStyle));
            }
        }

        src.sendSystemMessage(textBuilder.getComponent());

        return Command.SINGLE_SUCCESS;
    }

    private int setCommandState(CommandContext<CommandSourceStack> ctx, String command, boolean newState) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        boolean valid = false;
        for (HelpfulCommandsCommand hcCmd : ModCommandManager.getCommandList()) {
            ModCommandManager.ModCommand modCommand = hcCmd.getModCommand();
            if (modCommand.getName().equals(command)) {
                if (modCommand.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                    throw COMMAND_NOT_CONFIGURABLE.create(src, command);
                }
                valid = true;
                break;
            }
        }

        if (!valid) {
            throw INVALID_HC_COMMAND.create(src, command);
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
            ModCommandManager.ModCommand modCommand = hcCmd.getModCommand();
            if (modCommand.getName().equals(command)) {
                if (modCommand.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                    throw COMMAND_NOT_CONFIGURABLE.create(src, command);
                }
                valid = true;
                break;
            }
        }

        if (!valid) {
            throw INVALID_HC_COMMAND.create(src, command);
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

    private int clearCooldown(CommandContext<CommandSourceStack> ctx, CooldownManager.CooldownType cooldownType) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ArrayList<ServerPlayer> players = new ArrayList<>();
        players.add(sourcePlayer);

        return clearCooldown(ctx, cooldownType, players);
    }

    private int clearCooldown(CommandContext<CommandSourceStack> ctx, CooldownManager.CooldownType cooldownType, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int removed = 0;

        for (ServerPlayer player : players) {
            removed += CooldownManager.removeCooldown(player, cooldownType) ? 1 : 0;
        }

        if (removed == 0) {
            throw NO_ACTIVE_COOLDOWNS_FOUND.create(src);
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.cooldowns.clear", Component.literal(String.valueOf(removed)).setStyle(textStyles.getPrimary()), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.cooldowns.clear." + (removed == 1 ? "single" : "multiple"))));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return removed;
    }

    private int clearAllCooldowns(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        int removed = CooldownManager.removeAllCooldowns();

        if (removed == 0) {
            throw NO_ACTIVE_COOLDOWNS_FOUND.create(src);
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.hc.cooldowns.clear", Component.literal(String.valueOf(removed)).setStyle(textStyles.getPrimary()), Component.literal(TranslationManager.translate(src, "commands.helpful_commands.hc.cooldowns.clear." + (removed == 1 ? "single" : "multiple"))));
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return removed;
    }
}
