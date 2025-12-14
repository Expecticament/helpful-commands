package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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

import java.util.*;

public class SmeltCommand extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType ITEM_NOT_SMELTABLE = new SimpleCommandExceptionType(Component.empty());

    public SmeltCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> canExecute(src, "other"))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other");
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ItemStack mainHandItemStack = sourcePlayer.getMainHandItem();
        if (mainHandItemStack.isEmpty()) {
            throw new CommandSyntaxException(EMPTY_ITEM_STACK_MAIN_HAND, Component.literal(TranslationManager.translate(src, "error.helpful_commands.emptyItemStack.mainHand")));
        }

        if (!smelt(mainHandItemStack, sourcePlayer)) {
            throw new CommandSyntaxException(ITEM_NOT_SMELTABLE, Component.literal(TranslationManager.translate(src, "commands.helpful_commands.smelt.error.itemNotSmeltable")));
        }

        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.smelt.self", StylingHelper.getItemStackName(mainHandItemStack)).setStyle(textStyles.getPrimary());
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        Map<ServerPlayer, String> affected = new HashMap<>();
        for (ServerPlayer player : players) {
            ItemStack mainHandItemStack = player.getMainHandItem();
            if (mainHandItemStack.isEmpty()) {
                continue;
            }

            Component itemNameComponent = StylingHelper.getItemStackName(mainHandItemStack);

            if (!smelt(player.getMainHandItem(), player)) {
                continue;
            }

            if (commandFeedback && sourcePlayer != player) {
                TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(player);
                textBuilder.setStyle(textStyles.getAffectedNeutral());
                textBuilder.appendTranslatable("commands.helpful_commands.smelt.affected", itemNameComponent);

                player.sendSystemMessage(textBuilder.getComponent());
            }

            affected.put(player, itemNameComponent.getString());
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        TranslationManager.TextBuilder feedback = new TranslationManager.TextBuilder(src);
        feedback.setStyle(textStyles.getSuccess());
        if (affected.size() == 1) {
            feedback.appendTranslatable("commands.helpful_commands.smelt.other", StylingHelper.getAffectedEntityNameText(affected.keySet().iterator().next()), Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary()));
        } else {
            feedback.appendTranslatable("commands.helpful_commands.smelt.others", StylingHelper.getAffectedEntitiesNumberText(affected));
        }

        src.sendSuccess(feedback::getComponent, true);

        return affected.size();
    }

    private boolean smelt(ItemStack itemStack, ServerPlayer player) {
        ServerLevel serverLevel = player.level();
        SingleRecipeInput input = new SingleRecipeInput(itemStack.copy());
        RecipeManager recipeManager = serverLevel.recipeAccess();
        RegistryAccess registryAccess = serverLevel.registryAccess();
        Optional<RecipeHolder<SmeltingRecipe>> optionalRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, input, serverLevel);

        ItemStack output = optionalRecipe
                .map(holder -> holder.value().assemble(input, registryAccess))
                .orElse(ItemStack.EMPTY);

        if (output.isEmpty()) {
            return false;
        }

        output.setCount(itemStack.getCount());
        player.setItemInHand(InteractionHand.MAIN_HAND, output);

        return true;
    }
}
