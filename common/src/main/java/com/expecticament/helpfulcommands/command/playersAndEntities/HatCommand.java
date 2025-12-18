package com.expecticament.helpfulcommands.command.playersAndEntities;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.GameRulesHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HatCommand extends HelpfulCommandsCommand {
    public HatCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> canExecute(src, "other", 2))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players"), null))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .requires(src -> canExecute(src, "item", 2))
                                .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players"), ItemArgument.getItem(ctx, "item").createItemStack(1, false)))
                        )
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "other", 2) || canExecute(source, "item", 2);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        return executeSelf(ctx, sourcePlayer.getMainHandItem().copy(), true);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, ItemStack itemStack, boolean itemFromMainHand) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        TextBuilder textBuilder = new TextBuilder(src);

        if (itemFromMainHand) {
            Inventory inventory = sourcePlayer.getInventory();
            inventory.setItem(inventory.getSelectedSlot(), inventory.getItem(39));
        }
        if (!put(sourcePlayer, itemStack)) {
            return 0;
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        if (itemStack.getItem() != Items.AIR) {
            textBuilder.appendTranslatable("commands.helpful_commands.hat.self", getItemName(itemStack));
        } else {
            textBuilder.appendTranslatable("commands.helpful_commands.hat.remove.self");
        }
        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

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
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        boolean isAir = itemStack.getItem() == Items.AIR;

        Component itemNameComponent = getItemName(itemStack);

        List<ServerPlayer> affected = new ArrayList<>();

        for (ServerPlayer player : players) {
            if (!put(player, itemStack)) {
                continue;
            }
            if (commandFeedback && player != sourcePlayer) {
                TextBuilder textBuilder = new TextBuilder(player);
                if (isAir) {
                    textBuilder.appendTranslatable("commands.helpful_commands.hat.remove.affected");

                } else {
                    textBuilder.appendTranslatable("commands.helpful_commands.hat.affected", itemNameComponent);
                }
                textBuilder.setStyle(textStyles.getAffectedNeutral());
                player.sendSystemMessage(textBuilder.getComponent());
            }
            affected.add(player);
        }

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        TextBuilder textBuilder = new TextBuilder(src);

        if (affected.size() == 1) {
            if (isAir) {
                textBuilder.appendTranslatable("commands.helpful_commands.hat.remove.other", StylingHelper.getAffectedEntityNameText(affected.getFirst()));
            } else {
                textBuilder.appendTranslatable("commands.helpful_commands.hat.other", itemNameComponent, StylingHelper.getAffectedEntityNameText(affected.getFirst()));
            }
        } else {
            if (isAir) {
                textBuilder.appendTranslatable("commands.helpful_commands.hat.remove.others", StylingHelper.getAffectedEntitiesNumberText(affected));
            } else {
                textBuilder.appendTranslatable("commands.helpful_commands.hat.others", itemNameComponent, StylingHelper.getAffectedEntitiesNumberText(affected));
            }
        }

        textBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

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

        Vec3 vec3 = player.position();
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(SoundEvents.ARMOR_EQUIP_GENERIC.value().location()));
        player.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, vec3.x(), vec3.y(), vec3.z(), 0.5f, 1, player.level().getRandom().nextLong()));

        return true;
    }
}
