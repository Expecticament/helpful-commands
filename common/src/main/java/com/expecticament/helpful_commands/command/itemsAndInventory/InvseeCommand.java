package com.expecticament.helpful_commands.command.itemsAndInventory;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.PlayerDataUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleContainer;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class InvseeCommand extends HelpfulCommandsCommand {
    public InvseeCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> execute(ctx, GameProfileArgument.getGameProfiles(ctx, "player"))))
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_INVSEE);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, Collection<NameAndId> gameProfiles) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);
        MinecraftServer server = src.getServer();

        NameAndId targetProfile = gameProfiles.iterator().next();
        String targetName = targetProfile.name();
        UUID targetUUID;

        if (server.usesAuthentication()) {
            targetUUID = targetProfile.id();
        } else {
            targetUUID = UUIDUtil.createOfflinePlayerUUID(targetName);
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(targetName);
        if (onlinePlayer != null) {
            if (onlinePlayer == sourcePlayer) {
                throw TARGET_MUST_BE_OTHER_PLAYER.create(src);
            }
            openOnlineInventory(src, sourcePlayer, onlinePlayer);
            return Command.SINGLE_SUCCESS;
        }

        CompoundTag playerData = PlayerDataUtil.loadOfflinePlayerData(server, targetUUID);
        if (playerData == null) {
            throw PLAYER_DATA_NOT_FOUND.create(src, targetName);
        }

        openOfflineInventory(src, sourcePlayer, targetName, targetUUID, playerData);

        return Command.SINGLE_SUCCESS;
    }

    private void openOnlineInventory(CommandSourceStack src, ServerPlayer viewer, ServerPlayer onlinePlayer) {
        TextBuilder screenTitleTextBuilder = new TextBuilder(src);
        screenTitleTextBuilder.appendTranslatable("commands.helpful_commands.invsee.screen_title", Component.literal(onlinePlayer.getName().getString()));
        viewer.openMenu(new SimpleMenuProvider((syncId, inv, player) -> new OnlineInvseeMenu(syncId, inv, onlinePlayer), screenTitleTextBuilder.getComponent()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder textBuilder = new TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.invsee", StylingUtil.getAffectedEntityNameText(onlinePlayer)).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);
    }

    private void openOfflineInventory(CommandSourceStack src, ServerPlayer viewer, String targetName, UUID targetUUID, CompoundTag playerData) {
        SimpleContainer offlineInv = new SimpleContainer(41);

        RegistryAccess registryAccess = viewer.level().registryAccess();
        RegistryOps<Tag> ops = registryAccess.createSerializationContext(NbtOps.INSTANCE);

        Optional<ListTag> inventoryListTag = playerData.getList("Inventory");
        if (inventoryListTag.isPresent()) {
            ListTag inventory = inventoryListTag.get();
            for (int i = 0; i < inventory.size(); i++) {
                Optional<CompoundTag> slotTagOpt = inventory.getCompound(i);
                if (slotTagOpt.isEmpty()){
                    continue;
                }
                CompoundTag slotTag = slotTagOpt.get();

                int slot = slotTag.getByte("Slot").orElse((byte) -1) & 0xFF;

                ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, slotTag).result().orElse(ItemStack.EMPTY);

                if (stack.isEmpty()){
                    continue;
                }

                if (slot < 36) {
                    offlineInv.setItem(slot, stack);
                }
            }
        }

        Optional<CompoundTag> equipmentTag = playerData.getCompound("equipment");
        if (equipmentTag.isPresent()) {
            CompoundTag equipment = equipmentTag.get();
            offlineInv.setItem(36, readEquipmentSlot(equipment, "feet", ops));
            offlineInv.setItem(37, readEquipmentSlot(equipment, "legs", ops));
            offlineInv.setItem(38, readEquipmentSlot(equipment, "chest", ops));
            offlineInv.setItem(39, readEquipmentSlot(equipment, "head", ops));
            offlineInv.setItem(40, readEquipmentSlot(equipment, "offhand", ops));
        }

        MinecraftServer server = src.getServer();

        TextBuilder screenTitleTextBuilder = new TextBuilder(src);
        screenTitleTextBuilder.appendTranslatable("commands.helpful_commands.invsee.screen_title", Component.literal(targetName), Component.literal(TranslationManager.translate(src, "helpful_commands.common.offline")));
        viewer.openMenu(new SimpleMenuProvider((syncId, viewerInv, player) -> new OfflineInvseeMenu(syncId, viewerInv, offlineInv, updatedInv -> saveOfflineInventory(server, targetUUID, playerData, updatedInv, ops)), screenTitleTextBuilder.getComponent()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder feedbackTextBuilder = new TextBuilder(src);
        feedbackTextBuilder.appendTranslatable("commands.helpful_commands.invsee", Component.literal(targetName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());

        src.sendSuccess(feedbackTextBuilder::getComponent, true);
    }

    private void saveOfflineInventory(MinecraftServer server, UUID uuid, CompoundTag originalData, SimpleContainer inv, RegistryOps<Tag> ops) {
        ListTag inventoryTag = new ListTag();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()){
                continue;
            }

            Optional<Tag> encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result();
            if (encoded.isEmpty()){
                continue;
            }

            if (encoded.get() instanceof CompoundTag compound) {
                compound.putByte("Slot", (byte) i);
                inventoryTag.add(compound);
            }
        }

        originalData.put("Inventory", inventoryTag);

        CompoundTag equipmentTag = new CompoundTag();
        writeEquipmentSlot(equipmentTag, "feet", ops, inv.getItem(36));
        writeEquipmentSlot(equipmentTag, "legs", ops, inv.getItem(37));
        writeEquipmentSlot(equipmentTag, "chest", ops, inv.getItem(38));
        writeEquipmentSlot(equipmentTag, "head", ops, inv.getItem(39));
        writeEquipmentSlot(equipmentTag, "offhand", ops, inv.getItem(40));
        originalData.put("equipment", equipmentTag);

        PlayerDataUtil.saveOfflinePlayerData(server, uuid, originalData);
    }

    private ItemStack readEquipmentSlot(CompoundTag equipment, String key, RegistryOps<Tag> ops) {
        Optional<CompoundTag> slotTag = equipment.getCompound(key);
        if (slotTag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return ItemStack.OPTIONAL_CODEC.parse(ops, slotTag.get()).result().orElse(ItemStack.EMPTY);
    }

    private void writeEquipmentSlot(CompoundTag equipment, String key, RegistryOps<Tag> ops, ItemStack stack) {
        if (stack.isEmpty()){
            return;
        }

        Optional<Tag> encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result();
        if (encoded.isEmpty()){
            return;
        }

        if (encoded.get() instanceof CompoundTag compound) {
            equipment.put(key, compound);
        }
    }

    private static class OnlineInvseeMenu extends AbstractContainerMenu {
        public OnlineInvseeMenu(int syncId, Inventory sourceInventory, Player target) {
            super(MenuType.GENERIC_9x5, syncId); // 36 main inventory + 4 armor + offhand + info slot + 3 empty slots

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

                int targetSlotCount = 45;
                int totalSlots = this.slots.size();

                if (slotIndex < targetSlotCount) {
                    // Target to Viewer
                    if (!this.moveItemStackTo(originalStack, targetSlotCount, totalSlots, true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Viewer to Target
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

    private static class OfflineInvseeMenu extends AbstractContainerMenu {
        private final SimpleContainer offlineContainer;
        private final Consumer<SimpleContainer> onClose;

        public OfflineInvseeMenu(int syncId, Inventory viewerInventory, SimpleContainer offlineInv, Consumer<SimpleContainer> onClose) {
            super(MenuType.GENERIC_9x5, syncId); // 36 main inventory + 4 armor + offhand + info slot + 3 empty slots
            this.offlineContainer = offlineInv;
            this.onClose = onClose;

            Inventory dummyInventory = new Inventory(viewerInventory.player, new EntityEquipment());

            // Main inventory except hotbar
            for (int row = 1; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(offlineInv, row * 9 + col, 0, 0));
                }
            }

            // Hotbar
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(offlineInv, col, 0, 0));
            }

            this.addSlot(new EmptySlot(dummyInventory, 0, 0, 0));
            this.addSlot(new InfoSlot(dummyInventory, 1, 0, 0));

            // Equipment
            for (int i = 0; i < 4; i++) {
                this.addSlot(new Slot(offlineInv, 36 + i, 0, 0));
            }

            // Offhand
            this.addSlot(new Slot(offlineInv, 40, 0, 0));

            for(int i = 2; i < 4; i++) {
                this.addSlot(new EmptySlot(dummyInventory, i, 0, 0));
            }

            this.addStandardInventorySlots(viewerInventory, 0, 0);
        }

        @Override
        public void removed(@NonNull Player player) {
            super.removed(player);
            onClose.accept(offlineContainer);
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

                int targetSlotCount = 45;
                int totalSlots = this.slots.size();

                if (slotIndex < targetSlotCount) {
                    // Target to Viewer
                    if (!this.moveItemStackTo(originalStack, targetSlotCount, totalSlots, true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Viewer to Target
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
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.empty_slot")).setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));

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
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.info_slot.name")));
            itemStack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(TranslationManager.translate((ServerPlayer) inventory.player, "commands.helpful_commands.invsee.info_slot.lore")))));

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
