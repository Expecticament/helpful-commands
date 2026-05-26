package com.expecticament.helpful_commands.command.movementAndTeleportation;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.GameRulesUtil;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.manager.WarpManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.expecticament.helpful_commands.suggestionProvider.WarpDescriptionSuggestionProvider;
import com.expecticament.helpful_commands.suggestionProvider.WarpNameSuggestionProvider;
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
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Position;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class WarpCommand extends HelpfulCommandsCommand {
    private static final Dynamic2CommandExceptionType WARP_DOESNT_EXIST = new Dynamic2CommandExceptionType((src, warpName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.warp.error.warpDoesntExist", Component.literal(warpName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final Dynamic2CommandExceptionType WARP_ALREADY_EXISTS = new Dynamic2CommandExceptionType((src, warpName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.warp.error.warpAlreadyExists", Component.literal(warpName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final Dynamic2CommandExceptionType SAME_WARP_NAME_PROVIDED = new Dynamic2CommandExceptionType((src, warpName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.warp.edit.name.error.sameWarpNameProvided", Component.literal(warpName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    private static final DynamicCommandExceptionType NO_POSITION_PROVIDED = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.warp.error.noPositionProvided").getComponent()
    );
    private static final DynamicCommandExceptionType NO_DIMENSION_PROVIDED = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.warp.error.noDimensionProvided").getComponent()
    );

    public WarpCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        WarpNameSuggestionProvider warpNameSuggestionProvider = new WarpNameSuggestionProvider();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("tp")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_TP))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .then(Commands.argument("entities", EntityArgument.entities())
                                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_TP_OTHERS))
                                        .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "warp_name"), EntityArgument.getEntities(ctx, "entities")))
                                )
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "warp_name")))
                                .suggests(warpNameSuggestionProvider)
                        )
                )
                .then(Commands.literal("add")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_ADD))
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
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_REMOVE))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .executes(ctx -> removeWarp(ctx, StringArgumentType.getString(ctx, "warp_name")))
                                .suggests(warpNameSuggestionProvider)
                        )
                )
                .then(Commands.literal("edit")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_EDIT))
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
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_INFO))
                        .then(Commands.argument("warp_name", StringArgumentType.word())
                                .suggests(warpNameSuggestionProvider)
                                .executes(ctx -> warpInfo(ctx, StringArgumentType.getString(ctx, "warp_name")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_WARP);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String warpName) throws CommandSyntaxException {
        return teleport(ctx, warpName, null);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String warpName, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<Entity> targets = new ArrayList<>(Objects.requireNonNullElse(entities, List.of()));

        if (targets.isEmpty() && sourcePlayer == null) {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }

        if (entities == null) {
            targets.add(sourcePlayer);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.Warp warp = WarpManager.getWarp(warpName);
            try {
                ServerLevel dimension = ServerLevelUtil.getLevel(warp.dimension);

                boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

                List<Entity> affected = targets.stream()
                        .filter(e -> e.teleportTo(dimension, warp.x, warp.y, warp.z, Relative.DELTA, e.getYRot(), e.getXRot(), false))
                        .peek(e -> {
                            if (e.isAlwaysTicking() && e != sourcePlayer && commandFeedback) {
                                ServerPlayer plr = (ServerPlayer) e;
                                TextBuilder affectedMsgTextBuilder = new TextBuilder(src);
                                affectedMsgTextBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.affected", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getAffectedNeutral());
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
            } catch (ServerLevelUtil.UnknownServerLevelException e) {
                throw UNKNOWN_DIMENSION.create(src, warp.dimension);
            }
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
        }
    }

    private Component buildTeleportFeedbackMessage(CommandSourceStack src, boolean multiple, boolean self, String warpName, List<Entity> affected) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        MutableComponent warpNameText = Component.literal(warpName).setStyle(textStyles.getPrimary());

        TextBuilder textBuilder = new TextBuilder(src);

        if (multiple) {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.others", StylingUtil.getAffectedEntitiesNumberText(affected), warpNameText);
        } else if (self) {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.self", warpNameText);
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.warp.teleport.others", StylingUtil.getAffectedEntityNameText(affected.getFirst()), warpNameText);
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
                throw NO_POSITION_PROVIDED.create(src);
            } else {
                position = sourcePlayer.position();
            }
        }

        if (serverLevel == null) {
            if (sourcePlayer == null) {
                throw NO_DIMENSION_PROVIDED.create(src);
            } else {
                serverLevel = sourcePlayer.level();
            }
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.addWarp(warpName, warpDescription, position, serverLevel);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.add", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpAlreadyExistsException e) {
            throw WARP_ALREADY_EXISTS.create(src, warpName);
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
            textBuilder.appendTranslatable("commands.helpful_commands.warp.remove", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
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
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.name", Component.literal(warpName).setStyle(textStyles.getPrimary()), Component.literal(newName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
        } catch (WarpManager.SameWarpNameProvided e) {
            throw SAME_WARP_NAME_PROVIDED.create(src, newName);
        } catch (WarpManager.WarpAlreadyExistsException e) {
            throw WARP_ALREADY_EXISTS.create(src, newName);
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
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.description", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
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
                throw NO_POSITION_PROVIDED.create(src);
            } else {
                newPosition = sourcePlayer.position();
            }
        }

        if (newServerLevel == null) {
            if (sourcePlayer == null) {
                throw NO_DIMENSION_PROVIDED.create(src);
            } else {
                newServerLevel = sourcePlayer.level();
            }
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        try {
            WarpManager.editWarpLocation(warpName, newPosition, newServerLevel);
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("commands.helpful_commands.warp.edit.location", Component.literal(warpName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());
            src.sendSuccess(textBuilder::getComponent, true);
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
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

            Component bulletPointComponent = Component.literal(textDecorators.getBulletPoint()).setStyle(textStyles.getTertiary());
            Component colonComponent = Component.literal(": ").setStyle(textStyles.getTertiary());

            textBuilder.appendComponent(StylingUtil.getTitle(Component.literal(TranslationManager.translate(src, "commands.helpful_commands.warp.info.title")), Component.literal(warpName)));
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
                    .appendComponent(StylingUtil.getPositionText(warp.x, warp.y, warp.z))
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
                boolean canTp = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_TP);
                boolean canEdit = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_EDIT);
                boolean canRemove = PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_WARP_REMOVE);
                if (canTp || canEdit || canRemove) {
                    textBuilder
                            .appendNewline()
                            .appendNewline();

                    if (canTp) {
                        HoverEvent tpBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToTeleport")));
                        ClickEvent tpBtnClickEvent = new ClickEvent.RunCommand("/warp tp " + warpName);
                        Style tpBtnStyle = textStyles.getSecondary().withHoverEvent(tpBtnHoverEvent).withClickEvent(tpBtnClickEvent);
                        textBuilder.appendComponent(StylingUtil.getButton(textDecorators.getTeleport(), Component.literal(TranslationManager.translate(src, "helpful_commands.common.teleport")), tpBtnStyle));
                    }
                    if (canEdit) {
                        HoverEvent editBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToEdit")));
                        ClickEvent editBtnClickEvent = new ClickEvent.SuggestCommand("/warp edit " + warpName + " ");
                        Style editBtnStyle = textStyles.getTertiary().withHoverEvent(editBtnHoverEvent).withClickEvent(editBtnClickEvent);
                        if (canTp) {
                            textBuilder.appendWhitespace();
                        }
                        textBuilder.appendComponent(StylingUtil.getButton(Component.literal(textDecorators.getEdit()), editBtnStyle));
                    }
                    if (canRemove) {
                        HoverEvent removeBtnHoverEvent = new HoverEvent.ShowText(Component.literal(TranslationManager.translate(src, "hover.helpful_commands.clickToRemove")));
                        ClickEvent removeBtnClickEvent = new ClickEvent.RunCommand("/warp remove " + warpName);
                        Style removeBtnStyle = textStyles.getDangerousAction().withHoverEvent(removeBtnHoverEvent).withClickEvent(removeBtnClickEvent);
                        if (canTp || canEdit) {
                            textBuilder.appendWhitespace();
                        }
                        textBuilder.appendComponent(StylingUtil.getButton(Component.literal(textDecorators.getRemove()), removeBtnStyle));
                    }
                }
            }

            src.sendSystemMessage(textBuilder.getComponent());
        } catch (WarpManager.WarpDoesntExistException e) {
            throw WARP_DOESNT_EXIST.create(src, warpName);
        }

        return Command.SINGLE_SUCCESS;
    }
}
