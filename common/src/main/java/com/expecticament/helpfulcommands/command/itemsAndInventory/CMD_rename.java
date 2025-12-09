package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.DeprecatedTextBuilder;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.*;

public class CMD_rename extends HelpfulCommandsCommand {

    public CMD_rename(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("new_name", StringArgumentType.string())
                        .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "new_name")))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> canExecute(src, "other"))
                                .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "new_name"), EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx, String newName) throws CommandSyntaxException {
        return execute(ctx, newName, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, String newName, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<ServerPlayer> targets = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            renameAndSendMessage(sourcePlayer, src, newName, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        Map<ServerPlayer, String> affected = new HashMap<>();
        for (ServerPlayer player : targets) {
            ItemStack itemStack = renameAndSendMessage(player, src, newName, textStyles, commandFeedback, player == sourcePlayer, false);
            if (itemStack.isEmpty()) {
                continue;
            }

            affected.put(player, itemStack.getItemName().getString());
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        Component newItemNameComponent = Component.literal(newName).setStyle(textStyles.getPrimary());
        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        if (newName.isEmpty()) {
            textBuilder
                    .appendTranslatable("commands.helpful_commands.rename.remove.output.success.other.part1")
                    .appendWhitespace();

            if (affected.size() == 1) {
                Component affectedPlayerNameText = StylingHelper.getAffectedEntityNameText(affected.keySet().iterator().next());
                Component itemNameText = Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary());
                textBuilder
                        .appendComponent(affectedPlayerNameText)
                        .appendTranslatable("commands.helpful_commands.rename.remove.output.success.other.part2.single")
                        .appendWhitespace()
                        .appendComponent(itemNameText);
            } else {
                textBuilder
                        .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.rename.remove.output.success.other.part2.multiple");
            }
        } else {
            textBuilder
                    .appendTranslatable("commands.helpful_commands.rename.output.success.other.part1")
                    .appendWhitespace();

            if (affected.size() == 1) {
                Component affectedPlayerNameText = StylingHelper.getAffectedEntityNameText(affected.keySet().iterator().next());
                Component itemNameText = Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary());
                textBuilder
                        .appendComponent(affectedPlayerNameText)
                        .appendTranslatable("commands.helpful_commands.rename.output.success.other.part2.single")
                        .appendWhitespace()
                        .appendComponent(itemNameText)
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.rename.output.success.self.part2")
                        .appendWhitespace()
                        .appendComponent(newItemNameComponent);
            } else {
                textBuilder
                        .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.rename.output.success.other.part2.multiple")
                        .appendWhitespace()
                        .appendTranslatable("commands.helpful_commands.rename.output.success.self.part2")
                        .appendWhitespace()
                        .appendComponent(newItemNameComponent);
            }
        }



        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private ItemStack renameAndSendMessage(ServerPlayer player, CommandSourceStack commandSourceStack, String newName, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        ItemStack itemStack = player.getMainHandItem();
        if (!itemStack.isEmpty()) {
            Component oldCustomName = itemStack.getCustomName();
            String oldItemName = (oldCustomName != null ? oldCustomName.getString() : itemStack.getItemName().getString());
            if (oldItemName.equals(newName)) {
                return ItemStack.EMPTY;
            }
            Component oldItemNameComponent = Component.literal(oldItemName).setStyle(textStyles.getPrimary());
            Component newItemNameComponent = Component.literal(newName).setStyle(textStyles.getPrimary());

            boolean remove = newName.isEmpty();
            if (remove) {
                if (itemStack.get(DataComponents.CUSTOM_NAME) != null) {
                    itemStack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    return ItemStack.EMPTY;
                }
            } else {
                itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(newName));
            }

            if (commandFeedback) {
                if (isSource) {
                    if (feedbackSource) {
                        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        if (remove) {
                            textBuilder
                                    .appendTranslatable("commands.helpful_commands.rename.remove.output.success.self")
                                    .appendWhitespace()
                                    .appendComponent(oldItemNameComponent);
                        } else {
                            textBuilder
                                    .appendTranslatable("commands.helpful_commands.rename.output.success.self.part1")
                                    .appendWhitespace()
                                    .appendComponent(oldItemNameComponent)
                                    .appendWhitespace()
                                    .appendTranslatable("commands.helpful_commands.rename.output.success.self.part2")
                                    .appendWhitespace()
                                    .appendComponent(newItemNameComponent);
                        }
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                    textBuilder.setStyle(textStyles.getAffectedNeutral());
                    if (remove) {
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.rename.remove.output.success.affected.part1")
                                .appendWhitespace()
                                .appendComponent(oldItemNameComponent)
                                .appendTranslatable("commands.helpful_commands.rename.remove.output.success.affected.part2");
                    } else {
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.rename.output.success.affected.part1")
                                .appendWhitespace()
                                .appendComponent(oldItemNameComponent)
                                .appendWhitespace()
                                .appendTranslatable("commands.helpful_commands.rename.output.success.affected.part2")
                                .appendWhitespace()
                                .appendComponent(newItemNameComponent);
                    }
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return itemStack;
        }

        return ItemStack.EMPTY;
    }
}
