package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InvseeCommand extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType OTHER_PLAYER_ONLY = new SimpleCommandExceptionType(Component.empty());

    public InvseeCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player")))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        if (sourcePlayer == player) {
            throw new CommandSyntaxException(OTHER_PLAYER_ONLY, Component.literal(TranslationManager.translate(src, "commands.helpful_commands.invsee.error.otherPlayerOnly")));
        }

        TextBuilder screenTitleTextBuilder = new TextBuilder(src);
        screenTitleTextBuilder.appendTranslatable("commands.helpful_commands.invsee.screenTitle", Component.literal(player.getName().getString()));
        sourcePlayer.openMenu(new SimpleMenuProvider((syncId, inv, playerEntity) -> new InvseeAbstractContainerMenu(syncId, inv, player), screenTitleTextBuilder.getComponent()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.invsee", StylingHelper.getAffectedEntityNameText(player)).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private static class InvseeAbstractContainerMenu extends AbstractContainerMenu {

        public InvseeAbstractContainerMenu(int syncId, Inventory sourceInventory, Player target) {
            super(MenuType.GENERIC_9x5, syncId);

            Inventory targetInventory = target.getInventory();
            Inventory emptySlotInventory = new Inventory(target, null);
            this.addStandardInventorySlots(targetInventory, 0, 0);
            for(int i = 0; i < 2; i++) {
                this.addSlot(new EmptySlot(emptySlotInventory, 0, 0, 0));
            }
            for(int i = 0; i < 5; i++) {
                this.addSlot(new Slot(targetInventory, 36 + i, 0, 0));
            }
            for(int i = 0; i < 2; i++) {
                this.addSlot(new EmptySlot(emptySlotInventory, 0, 0, 0));
            }
            this.addStandardInventorySlots(sourceInventory, 0, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            ItemStack newStack = ItemStack.EMPTY;
            Slot slot = this.slots.get(slotIndex);

            if (slot.hasItem()) {
                ItemStack originalStack = slot.getItem();
                newStack = originalStack.copy();

                int targetSlotCount = 45; // 36 for main inventory + 4 for armor + offhand + 4 empties
                int totalSlots = this.slots.size();

                if (slotIndex < targetSlotCount) {
                    // target to source
                    if (!this.moveItemStackTo(originalStack, targetSlotCount, totalSlots, true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // source to target
                    if (!this.moveItemStackTo(originalStack, 0, targetSlotCount, false)) {
                        return ItemStack.EMPTY;
                    }
                }

                if (originalStack.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }

            return newStack;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    public static class EmptySlot extends Slot {
        public EmptySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);

            ItemStack itemStack = new ItemStack(Items.BARRIER);
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.emptySlot")).setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));

            set(itemStack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player playerEntity) {
            return false;
        }

//        @Override
//        public Identifier getBackgroundSprite() {
//            return ;
//        }
    }
}
