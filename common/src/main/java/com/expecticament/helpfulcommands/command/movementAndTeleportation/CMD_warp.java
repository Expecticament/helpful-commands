package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.suggestionProvider.WarpDescriptionSuggestionProvider;
import com.expecticament.helpfulcommands.suggestionProvider.WarpNameSuggestionProvider;
import com.expecticament.helpfulcommands.manager.WarpManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Position;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CMD_warp extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType WARP_DOESNT_EXIST = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType WARP_ALREADY_EXISTS = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType SAME_NAME_PROVIDED = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType NO_POSITION_PROVIDED = new SimpleCommandExceptionType(Component.empty());
    protected static final SimpleCommandExceptionType NO_DIMENSION_PROVIDED = new SimpleCommandExceptionType(Component.empty());

    public CMD_warp(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        WarpNameSuggestionProvider warpNameSuggestionProvider = new WarpNameSuggestionProvider();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("tp")
                        .requires(src -> canExecute(src, "tp"))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .then(Commands.argument("entities", EntityArgument.entities())
                                        .requires(src -> canExecute(src, "tp.other", 2))
                                        .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "warp_name"), EntityArgument.getEntities(ctx, "entities")))
                                )
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "warp_name")))
                                .suggests(warpNameSuggestionProvider)
                        )
                )
                .then(Commands.literal("add")
                        .requires(src -> canExecute(src, "manage", 3))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .then(Commands.argument("description", StringArgumentType.string())
                                        .executes(ctx -> addWarp(ctx, StringArgumentType.getString(ctx, "warp_name"), StringArgumentType.getString(ctx, "description")))
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(ctx -> addWarp(ctx, StringArgumentType.getString(ctx, "warp_name"), StringArgumentType.getString(ctx, "description"), Vec3Argument.getVec3(ctx, "position")))
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(ctx -> addWarp(ctx, StringArgumentType.getString(ctx, "warp_name"), StringArgumentType.getString(ctx, "description"), Vec3Argument.getVec3(ctx, "position"), DimensionArgument.getDimension(ctx, "dimension")))
                                                )
                                        )
                                )
                                .executes(ctx -> addWarp(ctx, StringArgumentType.getString(ctx, "warp_name")))
                        )
                )
                .then(Commands.literal("remove")
                        .requires(src -> canExecute(src, "manage", 3))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .executes(ctx -> removeWarp(ctx, StringArgumentType.getString(ctx, "warp_name")))
                                .suggests(warpNameSuggestionProvider)
                        )
                )
                .then(Commands.literal("edit")
                        .requires(src -> canExecute(src, "manage", 3))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .then(Commands.literal("name")
                                        .then(Commands.argument("new_name", StringArgumentType.word())
                                                .executes(ctx -> editWarpName(ctx, StringArgumentType.getString(ctx, "warp_name"), StringArgumentType.getString(ctx, "new_name")))
                                        )
                                )
                                .then(Commands.literal("description")
                                        .then(Commands.argument("new_description", StringArgumentType.string())
                                                .executes(ctx -> editWarpDescription(ctx, StringArgumentType.getString(ctx, "warp_name"), StringArgumentType.getString(ctx, "new_description")))
                                                .suggests(new WarpDescriptionSuggestionProvider())
                                        )
                                )
                                .then(Commands.literal("location")
                                        .then(Commands.argument("new_position", Vec3Argument.vec3())
                                                .then(Commands.argument("new_dimension", DimensionArgument.dimension())
                                                        .executes(ctx -> editWarpLocation(ctx, StringArgumentType.getString(ctx, "warp_name"), Vec3Argument.getVec3(ctx, "new_position"), DimensionArgument.getDimension(ctx, "new_dimension")))
                                                )
                                                .executes(ctx -> editWarpLocation(ctx, StringArgumentType.getString(ctx, "warp_name"), Vec3Argument.getVec3(ctx, "new_position")))
                                        )
                                        .executes(ctx -> editWarpLocation(ctx, StringArgumentType.getString(ctx, "warp_name")))
                                )
                                .suggests(warpNameSuggestionProvider)
                        )
                )
                .then(Commands.literal("info")
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .suggests(warpNameSuggestionProvider)
                                .executes(ctx -> warpInfo(ctx, StringArgumentType.getString(ctx, "warp_name")))
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "tp") || canExecute(source, "tp.other") || canExecute(source, "manage");
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        return teleport(ctx, warpName, null);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String warpName, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<Entity> targets = new ArrayList<>(Objects.requireNonNullElse(entities, List.of()));

        if (targets.isEmpty() && sourcePlayer == null) {
            throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
        }

        if (entities == null) {
            targets.add(sourcePlayer);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.Warp warp = WarpManager.getWarp(warpName);
            try {
                ServerLevel dimension = ServerLevelHelper.getLevel(warp.dimension);

                boolean commandFeedback = src.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);

                List<Entity> affected = targets.stream()
                        .filter(e -> e.teleportTo(dimension, warp.x, warp.y, warp.z, Relative.DELTA, e.getYRot(), e.getXRot(), false))
                        .peek(e -> {
                            if (e.isAlwaysTicking() && e != sourcePlayer && commandFeedback) {
                                ServerPlayer plr = (ServerPlayer) e;
                                TextBuilder affectedMsgTextBuilder = new TextBuilder(src);
                                affectedMsgTextBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.output.success.affected", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getAffectedNeutral());
                                plr.sendSystemMessage(affectedMsgTextBuilder.getComponent());
                            }
                        })
                        .toList();

                if (affected.isEmpty()) {
                    throw EntityArgument.NO_ENTITIES_FOUND.create();
                }

                boolean multiple = affected.size() > 1;
                boolean self = affected.contains(sourcePlayer);
                src.sendSuccess(() -> buildTeleportFeedbackMessage(src, multiple, self, warpName, affected), true);

                return affected.size();
            } catch (ServerLevelHelper.UnknownServerLevelException e) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(warp.dimension).setStyle(textStyles.getPrimary()));
                throw new CommandSyntaxException(UNKNOWN_DIMENSION, textBuilder.getComponent());
            }
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        }
    }

    private Component buildTeleportFeedbackMessage(CommandSourceStack src, boolean multiple, boolean self, String warpName, List<Entity> affected) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        MutableComponent warpNameText = Component.literal(warpName).setStyle(textStyles.getPrimary());

        TextBuilder textBuilder = new TextBuilder(src);

        if (multiple) {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.output.success.others", StylingHelper.getAffectedEntitiesNumberText(affected), warpNameText);
        } else if (self) {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.output.success.self", warpNameText);
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.output.success.others", StylingHelper.getAffectedEntityNameText(affected.getFirst()), warpNameText);
        }

        textBuilder.setStyle(textStyles.getSuccess());

        return textBuilder.getComponent();
    }

    private int addWarp(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        return addWarp(ctx, warpName, "", null, null);
    }

    private int addWarp(CommandContext<CommandSourceStack> ctx, String warpName, String warpDescription) throws CommandSyntaxException {
        return addWarp(ctx, warpName, warpDescription, null, null);
    }

    private int addWarp(CommandContext<CommandSourceStack> ctx, String warpName, String warpDescription, Position position) throws CommandSyntaxException {
        return addWarp(ctx, warpName, warpDescription, position, null);
    }

    private int addWarp(CommandContext<CommandSourceStack> ctx, String warpName, String warpDescription, Position position, ServerLevel serverLevel) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (position == null) {
            if (sourcePlayer == null) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.specifyPosition");
                throw new CommandSyntaxException(NO_POSITION_PROVIDED, textBuilder.getComponent());
            } else {
                position = sourcePlayer.position();
            }
        }

        if (serverLevel == null) {
            if (sourcePlayer == null) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.specifyDimension");
                throw new CommandSyntaxException(NO_DIMENSION_PROVIDED, textBuilder.getComponent());
            } else {
                serverLevel = sourcePlayer.level();
            }
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.addWarp(warpName, warpDescription, position, serverLevel);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.add.output.success", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpAlreadyExistsException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpAlreadyExists", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_ALREADY_EXISTS, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int removeWarp(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.removeWarp(warpName);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.remove.output.success", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editWarpName(CommandContext<CommandSourceStack> ctx, String warpName, String newName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.editWarpName(warpName, newName);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.name.output.success", Component.literal(warpName).setStyle(textStyles.getPrimary()), Component.literal(newName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        } catch (WarpManager.SameWarpNameProvided e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.name.output.error.sameName", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(SAME_NAME_PROVIDED, textBuilder.getComponent());
        } catch (WarpManager.WarpAlreadyExistsException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpAlreadyExists", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_ALREADY_EXISTS, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editWarpDescription(CommandContext<CommandSourceStack> ctx, String warpName, String newDescription) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.editWarpDescription(warpName, newDescription);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.description.output.success", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int editWarpLocation(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        return editWarpLocation(ctx, warpName, null, null);
    }

    private int editWarpLocation(CommandContext<CommandSourceStack> ctx, String warpName, Position newPosition) throws CommandSyntaxException {
        return editWarpLocation(ctx, warpName, newPosition, null);
    }

    private int editWarpLocation(CommandContext<CommandSourceStack> ctx, String warpName, Position newPosition, ServerLevel newServerLevel) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (newPosition == null) {
            if (sourcePlayer == null) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.specifyPosition");
                throw new CommandSyntaxException(NO_POSITION_PROVIDED, textBuilder.getComponent());
            } else {
                newPosition = sourcePlayer.position();
            }
        }

        if (newServerLevel == null) {
            if (sourcePlayer == null) {
                TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
                textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.specifyDimension");
                throw new CommandSyntaxException(NO_DIMENSION_PROVIDED, textBuilder.getComponent());
            } else {
                newServerLevel = sourcePlayer.level();
            }
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.editWarpLocation(warpName, newPosition, newServerLevel);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.location.output.success", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int warpInfo(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = StylingManager.getCurrentStyle().getTextDecorators();

        try{
            WarpManager.Warp warp = WarpManager.getWarp(warpName);
            TextBuilder textBuilder = new TextBuilder(src);

            HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToTeleport")));
            ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/warp tp " + warpName);
            Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);
            HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToEdit")));
            ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/warp edit " + warpName + " ");
            Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);
            HoverEvent removeBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToRemove")));
            ClickEvent removeBtnClickEvent = new ClickEvent.RunCommand("/warp remove " + warpName);
            Style removeBtnStyle = textStyles.getDangerousAction().withHoverEvent(removeBtnHoverEvent).withClickEvent(removeBtnClickEvent);

            Component bulletPointComponent = Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary());
            Component colonComponent = Component.literal(": ").setStyle(textStyles.getTertiary());

            textBuilder.appendComponent(StylingHelper.getTitle(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.warp.info.title")), Component.literal(warpName)));
            if (!warp.description.isEmpty() && !warp.description.equals(" ")) {
                textBuilder
                        .appendNewline()
                        .appendComponent(bulletPointComponent)
                        .appendComponent(Component.literal(TranslationManager.translate(src, "helpful_commands.common.description")).setStyle(textStyles.getTertiary()))
                        .appendComponent(colonComponent)
                        .appendLiteral(warp.description);
            }
            textBuilder
                    .appendNewline()
                    .appendComponent(bulletPointComponent)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "helpful_commands.common.position")).setStyle(textStyles.getTertiary()))
                    .appendComponent(colonComponent)
                    .appendComponent(StylingHelper.getPositionText(warp.x, warp.y, warp.z))
                    .appendNewline()
                    .appendComponent(bulletPointComponent)
                    .appendComponent(Component.literal(TranslationManager.translate(src, "helpful_commands.common.dimension")).setStyle(textStyles.getTertiary()))
                    .appendComponent(colonComponent)
                    .appendComponent(Component.literal(warp.dimension).setStyle(textStyles.getSecondary()));

            if (!src.isPlayer()) {
                textBuilder
                        .appendNewline()
                        .appendNewline()
                        .appendLiteral(TranslationManager.translate(src, "helpful_commands.common.teleport") + ": /warp tp %s <entities>".formatted(warpName))
                        .appendNewline()
                        .appendLiteral(TranslationManager.translate(src, "helpful_commands.common.edit") + ": /warp edit %s ...".formatted(warpName))
                        .appendNewline()
                        .appendLiteral(TranslationManager.translate(src, "helpful_commands.common.remove") + ": /warp remove %s".formatted(warpName));
            } else {
                boolean canTp = canExecute(src, "tp");
                boolean canManage = canExecute(src, "manage", 3);
                if (canTp || canManage) {
                    textBuilder
                            .appendNewline()
                            .appendNewline();

                    if (canTp) {
                        textBuilder.appendComponent(StylingHelper.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpful_commands.common.teleport")), tpBtnStyle)).appendWhitespace();
                    }
                    if (canManage) {
                        textBuilder
                                .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle))
                                .appendWhitespace()
                                .appendComponent(StylingHelper.getButton(Component.literal(textDecorators.getRemove()), removeBtnStyle));
                    }
                }
            }

            src.sendSystemMessage(textBuilder.getComponent());
        } catch (WarpManager.WarpDoesntExistException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.output.error.warpDoesntExist", Component.literal(warpName).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(WARP_DOESNT_EXIST, textBuilder.getComponent());
        }

        return Command.SINGLE_SUCCESS;
    }
}
