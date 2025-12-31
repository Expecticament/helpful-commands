package com.expecticament.helpfulcommands.command.itemsAndInventory;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class InvseeCommand extends HelpfulCommandsCommand {
    public InvseeCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecute)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_INVSEE);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        if (sourcePlayer == player) {
            throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
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
            Inventory dummyInventory = new Inventory(target, new EntityEquipment());

            this.addStandardInventorySlots(targetInventory, 0, 0);

            this.addSlot(new EmptySlot(dummyInventory, 0, 0, 0));
            this.addSlot(new InfoSlot(dummyInventory, 1, 0, 0));

            for(int i = 0; i < 5; i++) {
                this.addSlot(new Slot(targetInventory, 36 + i, 0, 0));
            }

            for(int i = 2; i < 4; i++) {
                this.addSlot(new EmptySlot(dummyInventory, i, 0, 0));
            }

            this.addStandardInventorySlots(sourceInventory, 0, 0);
        }

        @Override
        public @NonNull ItemStack quickMoveStack(@NonNull Player player, int slotIndex) {
            ItemStack newStack = ItemStack.EMPTY;
            Slot slot = this.slots.get(slotIndex);

            if (slot instanceof EmptySlot || slot instanceof InfoSlot) {
                return ItemStack.EMPTY;
            }

            if (slot.hasItem()) {
                ItemStack originalStack = slot.getItem();
                newStack = originalStack.copy();

                int targetSlotCount = 45; // 36 for main inventory + 4 for armor + offhand + info slot + 3 empty slots
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
        public boolean stillValid(@NonNull Player player) {
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
        public boolean mayPlace(@NonNull ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(@NonNull Player playerEntity) {
            return false;
        }
    }

    public static class InfoSlot extends Slot {
        public InfoSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);

            ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK);
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.infoSlot.name")));
            itemStack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.infoSlot.lore")))));

            set(itemStack);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(@NonNull Player playerEntity) {
            return false;
        }
    }
}
