package com.expecticament.helpful_commands.command.playersAndEntities;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
import com.expecticament.helpful_commands.util.GameRulesUtil;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.translation.TranslationManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;

public class GmCommand extends HelpfulCommandsCommand {
    public GmCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("a")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_A))
                        .executes(ctx -> executeSelf(ctx, GameType.ADVENTURE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_A_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.ADVENTURE, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("c")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_C))
                        .executes(ctx -> executeSelf(ctx, GameType.CREATIVE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_C_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.CREATIVE, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("s")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_S))
                        .executes(ctx -> executeSelf(ctx, GameType.SURVIVAL))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_S_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.SURVIVAL, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
                .then(Commands.literal("sp")
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_SP))
                        .executes(ctx -> executeSelf(ctx, GameType.SPECTATOR))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_GM_SP_OTHERS))
                                .executes(ctx -> executeOther(ctx, GameType.SPECTATOR, EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_GM);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, GameType gameType) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (!sourcePlayer.setGameMode(gameType)) {
            return 0;
        }

        componentBuilder.appendTranslatable("commands.helpful_commands.gm.self", Component.translatable("gameMode." + gameType.getName()).setStyle(textStyles.getPrimary()));
        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, GameType gameType, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx, gameType);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        Component gameModeNameComponent = Component.translatable("gameMode." + gameType.getName()).setStyle(textStyles.getPrimary());

        List<ServerPlayer> affected = players.stream()
                .filter(player -> player.setGameMode(gameType))
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            ComponentBuilder componentBuilder = new ComponentBuilder(player);
                            componentBuilder.appendTranslatable("commands.helpful_commands.gm.affected", gameModeNameComponent).setStyle(textStyles.getAffectedNeutral());
                            player.sendSystemMessage(componentBuilder.build());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingUtil.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingUtil.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpful_commands.gm.other.multiple"));
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpful_commands.gm.other", gameModeNameComponent, affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return affected.size();
    }
}
