package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class RepairCommand extends HelpfulCommandsCommand {
    private static final DynamicCommandExceptionType ITEM_NOT_DAMAGEABLE = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.repair.error.itemNotDamageable").getComponent()
    );
    private static final DynamicCommandExceptionType ITEM_NOT_DAMAGED = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.repair.error.itemNotDamaged").getComponent()
    );

    public RepairCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_REPAIR_OTHERS))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_REPAIR);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ItemStack mainHandItemStack = sourcePlayer.getMainHandItem();
        if (mainHandItemStack.isEmpty()) {
            throw EMPTY_ITEM_STACK_MAIN_HAND.create(src);
        }

        TextBuilder textBuilder = new TextBuilder(src);

        int result = repair(mainHandItemStack);

        switch (result) {
            case -2:
                throw ITEM_NOT_DAMAGED.create(src);
            case -1:
                throw ITEM_NOT_DAMAGEABLE.create(src);
            case 0:
                textBuilder.appendTranslatable("commands.helpful_commands.repair.self", StylingHelper.getItemStackName(mainHandItemStack)).setStyle(textStyles.getPrimary());
                break;
        }

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

            int result = repair(player.getMainHandItem());
            if (result < 0) {
                continue;
            }

            Component itemNameComponent = StylingHelper.getItemStackName(mainHandItemStack);

            if (commandFeedback && sourcePlayer != player) {
                TextBuilder textBuilder = new TextBuilder(player);
                textBuilder.setStyle(textStyles.getAffectedPositive());
                textBuilder.appendTranslatable("commands.helpful_commands.repair.affected", itemNameComponent);

                player.sendSystemMessage(textBuilder.getComponent());
            }

            affected.put(player, itemNameComponent.getString());
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        TextBuilder feedback = new TextBuilder(src);
        feedback.setStyle(textStyles.getSuccess());
        if (affected.size() == 1) {
            feedback.appendTranslatable("commands.helpful_commands.repair.other", StylingHelper.getAffectedEntityNameText(affected.keySet().iterator().next()), Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary()));
        } else {
            feedback.appendTranslatable("commands.helpful_commands.repair.others", StylingHelper.getAffectedEntitiesNumberText(affected));
        }

        src.sendSuccess(feedback::getComponent, true);

        return affected.size();
    }

    private int repair(ItemStack itemStack) {
        if (!itemStack.isDamageableItem()) {
            return -1;
        }

        if (!itemStack.isDamaged()) {
            return -2;
        }

        itemStack.set(DataComponents.DAMAGE, 0);

        return 0;
    }
}
