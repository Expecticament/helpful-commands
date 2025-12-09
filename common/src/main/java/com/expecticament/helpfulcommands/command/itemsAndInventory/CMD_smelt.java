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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.*;

public class CMD_smelt extends HelpfulCommandsCommand {

    public CMD_smelt(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> canExecute(src, "other"))
                        .executes(ctx -> execute(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
                .executes(this::execute)
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return execute(ctx, null);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        List<ServerPlayer> targets = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = src.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);

        if (targets.isEmpty() || (targets.size() == 1 && sourcePlayer != null && targets.contains(sourcePlayer))) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }

            smeltAndSendMessage(sourcePlayer, src, textStyles, commandFeedback, true, true);

            return Command.SINGLE_SUCCESS;
        }

        Map<ServerPlayer, String> affected = new HashMap<>();
        for (ServerPlayer player : targets) {
            ItemStack itemStack = smeltAndSendMessage(player, src, textStyles, commandFeedback, player == sourcePlayer, false);
            if (itemStack.isEmpty()) {
                continue;
            }

            affected.put(player, itemStack.getItemName().getString());
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
        textBuilder.setStyle(textStyles.getSuccess());
        textBuilder
                .appendTranslatable("commands.helpful_commands.smelt.output.success.other.part1")
                .appendWhitespace();

        if (affected.size() == 1) {
            Component affectedPlayerNameText = StylingHelper.getAffectedEntityNameText(affected.keySet().iterator().next());
            Component itemNameText = Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary());
            textBuilder
                    .appendComponent(affectedPlayerNameText)
                    .appendTranslatable("commands.helpful_commands.smelt.output.success.other.part2.single")
                    .appendWhitespace()
                    .appendComponent(itemNameText);
        } else {
            textBuilder
                    .appendComponent(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.smelt.output.success.other.part2.multiple");
        }

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private ItemStack smeltAndSendMessage(ServerPlayer player, CommandSourceStack commandSourceStack, HelpfulCommandsStyle.TextStyles textStyles, boolean commandFeedback, boolean isSource, boolean feedbackSource) {
        ItemStack itemStack = player.getMainHandItem();
        if (!itemStack.isEmpty()) {
            ServerLevel level = player.level();
            SingleRecipeInput input = new SingleRecipeInput(itemStack.copy());
            RecipeManager recipeManager = level.recipeAccess();
            RegistryAccess registryAccess = level.registryAccess();
            Optional<RecipeHolder<SmeltingRecipe>> optionalRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, input, level);

            ItemStack output = optionalRecipe
                    .map(holder -> holder.value().assemble(input, registryAccess))
                    .orElse(ItemStack.EMPTY);

            if (output.isEmpty()) {
                return ItemStack.EMPTY;
            }

            output.setCount(itemStack.getCount());
            player.setItemInHand(InteractionHand.MAIN_HAND, output);

            String itemName = itemStack.getItemName().getString();
            Component itemNameComponent = Component.literal(itemName).setStyle(textStyles.getPrimary());

            if (commandFeedback) {
                if (isSource) {
                    if (feedbackSource) {
                        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        textBuilder
                                .appendTranslatable("commands.helpful_commands.smelt.output.success.self")
                                .appendWhitespace()
                                .appendComponent(itemNameComponent);
                        commandSourceStack.sendSuccess(textBuilder::getComponent, true);
                    }
                } else {
                    DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(player);
                    textBuilder.setStyle(textStyles.getAffectedPositive());
                    textBuilder
                            .appendTranslatable("commands.helpful_commands.smelt.output.success.affected.part1")
                            .appendWhitespace()
                            .appendComponent(itemNameComponent)
                            .appendWhitespace()
                            .appendTranslatable("commands.helpful_commands.smelt.output.success.affected.part2");
                    player.sendSystemMessage(textBuilder.getComponent());
                }
            }

            return itemStack;
        }

        return ItemStack.EMPTY;
    }
}
