package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.expecticament.helpfulcommands.util.GameRulesUtil;
import com.expecticament.helpfulcommands.util.PermissionsUtil;
import com.expecticament.helpfulcommands.util.StylingUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SmeltCommand extends HelpfulCommandsCommand {
    private static final DynamicCommandExceptionType ITEM_NOT_SMELTABLE = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.smelt.error.item_not_smeltable").build()
    );

    public SmeltCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_SMELT_OTHERS))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_SMELT);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ItemStack mainHandItemStack = sourcePlayer.getMainHandItem();
        if (mainHandItemStack.isEmpty()) {
            throw EMPTY_ITEM_STACK_MAIN_HAND.create(src);
        }

        if (!smelt(mainHandItemStack, sourcePlayer)) {
            throw ITEM_NOT_SMELTABLE.create(src);
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpfulcommands.smelt.self", StylingUtil.getItemStackName(mainHandItemStack)).setStyle(textStyles.getPrimary());
        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        Map<ServerPlayer, String> affected = new HashMap<>();
        for (ServerPlayer player : players) {
            ItemStack mainHandItemStack = player.getMainHandItem();
            if (mainHandItemStack.isEmpty()) {
                continue;
            }

            Component itemNameComponent = StylingUtil.getItemStackName(mainHandItemStack);

            if (!smelt(player.getMainHandItem(), player)) {
                continue;
            }

            if (commandFeedback && sourcePlayer != player) {
                ComponentBuilder componentBuilder = new ComponentBuilder(player);
                componentBuilder.setStyle(textStyles.getAffectedNeutral());
                componentBuilder.appendTranslatable("commands.helpfulcommands.smelt.affected", itemNameComponent);

                player.sendSystemMessage(componentBuilder.build());
            }

            affected.put(player, itemNameComponent.getString());
        }

        if (affected.isEmpty()) {
            throw NO_ITEMS_FOUND.create(src);
        }

        ComponentBuilder feedback = new ComponentBuilder(src);
        feedback.setStyle(textStyles.getSuccess());
        if (affected.size() == 1) {
            feedback.appendTranslatable("commands.helpfulcommands.smelt.other", StylingUtil.getAffectedEntityNameText(affected.keySet().iterator().next()), Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary()));
        } else {
            feedback.appendTranslatable("commands.helpfulcommands.smelt.others", StylingUtil.getAffectedEntitiesNumberText(affected));
        }

        src.sendSuccess(feedback::build, true);

        return affected.size();
    }

    private boolean smelt(ItemStack itemStack, ServerPlayer player) {
        ServerLevel serverLevel = player.level();
        SingleRecipeInput input = new SingleRecipeInput(itemStack.copy());
        RecipeManager recipeManager = serverLevel.recipeAccess();
        Optional<RecipeHolder<SmeltingRecipe>> optionalRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, input, serverLevel);

        ItemStack output = optionalRecipe
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);

        if (output.isEmpty()) {
            return false;
        }

        output.setCount(itemStack.getCount());
        player.setItemInHand(InteractionHand.MAIN_HAND, output);

        return true;
    }
}
