package com.expecticament.helpful_commands.command.playersAndEntities;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.GameRulesUtil;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.SoundUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
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
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HatCommand extends HelpfulCommandsCommand {
    public HatCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HAT_OTHERS))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players"), null))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HAT_ITEM))
                                .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players"), ItemArgument.getItem(ctx, "item").createItemStack(1)))
                        )
                )
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HAT_ITEM) && !PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_HAT_OTHERS))
                        .executes(ctx -> executeSelf(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1), false))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_HAT);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        return executeSelf(ctx, sourcePlayer.getMainHandItem().copy(), true);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, ItemStack itemStack, boolean itemFromMainHand) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (itemFromMainHand) {
            Inventory inventory = sourcePlayer.getInventory();
            inventory.setItem(inventory.getSelectedSlot(), inventory.getItem(39));
        }
        if (!put(sourcePlayer, itemStack)) {
            return 0;
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        if (itemStack.getItem() != Items.AIR) {
            componentBuilder.appendTranslatable("commands.helpful_commands.hat.self", getItemName(itemStack));
        } else {
            componentBuilder.appendTranslatable("commands.helpful_commands.hat.remove.self");
        }
        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, ItemStack itemStack) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            if (itemStack == null) {
                return executeSelf(ctx);
            } else {
                return executeSelf(ctx, itemStack, false);
            }
        }

        if (itemStack == null) {
            if (sourcePlayer == null) {
                throw CommandSourceStack.ERROR_NOT_PLAYER.create();
            }
            itemStack = sourcePlayer.getMainHandItem().copy();
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        boolean isAir = itemStack.getItem() == Items.AIR;

        Component itemNameComponent = getItemName(itemStack);

        List<ServerPlayer> affected = new ArrayList<>();

        for (ServerPlayer player : players) {
            if (!put(player, itemStack)) {
                continue;
            }
            if (commandFeedback && player != sourcePlayer) {
                ComponentBuilder componentBuilder = new ComponentBuilder(player);
                if (isAir) {
                    componentBuilder.appendTranslatable("commands.helpful_commands.hat.remove.affected");

                } else {
                    componentBuilder.appendTranslatable("commands.helpful_commands.hat.affected", itemNameComponent);
                }
                componentBuilder.setStyle(textStyles.getAffectedNeutral());
                player.sendSystemMessage(componentBuilder.build());
            }
            affected.add(player);
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        if (affected.size() == 1) {
            if (isAir) {
                componentBuilder.appendTranslatable("commands.helpful_commands.hat.remove.other", StylingUtil.getAffectedEntityNameText(affected.getFirst()));
            } else {
                componentBuilder.appendTranslatable("commands.helpful_commands.hat.other", itemNameComponent, StylingUtil.getAffectedEntityNameText(affected.getFirst()));
            }
        } else {
            if (isAir) {
                componentBuilder.appendTranslatable("commands.helpful_commands.hat.remove.others", StylingUtil.getAffectedEntitiesNumberText(affected));
            } else {
                componentBuilder.appendTranslatable("commands.helpful_commands.hat.others", itemNameComponent, StylingUtil.getAffectedEntitiesNumberText(affected));
            }
        }

        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return players.size();
    }

    private Component getItemName(ItemStack itemStack) {
        return Component.literal(itemStack.getItemName().getString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary());
    }

    private boolean put(ServerPlayer player, ItemStack itemStack) {
        Inventory inventory = player.getInventory();

        if (itemStack.getItem() == Items.AIR && inventory.getItem(39).getItem() == itemStack.getItem()) {
            return false;
        }

        inventory.setItem(39, itemStack.copy());

        SoundUtil.playSound(player, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5f, 1);

        return true;
    }
}
