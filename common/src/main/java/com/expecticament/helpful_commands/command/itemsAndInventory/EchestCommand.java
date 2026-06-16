package com.expecticament.helpful_commands.command.itemsAndInventory;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.translation.TranslationManager;
import com.expecticament.helpful_commands.manager.translation.ComponentBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.PlayerDataUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class EchestCommand extends HelpfulCommandsCommand {
    public EchestCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_ECHEST_OTHER))
                                .executes(ctx -> execute(ctx, GameProfileArgument.getGameProfiles(ctx, "player")))
                        )
                .executes(this::execute)
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_ECHEST);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        openOnlineInventory(src, sourcePlayer, sourcePlayer);

        return Command.SINGLE_SUCCESS;
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
                return execute(ctx);
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
        Component screenTitle = Component.translatable("container.enderchest");
        if (viewer != onlinePlayer) {
            ComponentBuilder screenTitleComponentBuilder = new ComponentBuilder(src);
            screenTitleComponentBuilder.appendTranslatable("commands.helpful_commands.echest.screen_title", Component.literal(onlinePlayer.getName().getString()));
            screenTitle = screenTitleComponentBuilder.build();
        }
        viewer.openMenu(new SimpleMenuProvider((syncId, inv, player) -> new OnlineEchestMenu(syncId, inv, onlinePlayer), screenTitle));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        ComponentBuilder componentBuilder = new ComponentBuilder(src);
        componentBuilder.appendTranslatable("commands.helpful_commands.echest." + (viewer == onlinePlayer ? "self" : "other"), StylingUtil.getAffectedEntityNameText(onlinePlayer)).setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);
    }

    private void openOfflineInventory(CommandSourceStack src, ServerPlayer viewer, String targetName, UUID targetUUID, CompoundTag playerData) {
        SimpleContainer offlineInv = new SimpleContainer(41);

        RegistryAccess registryAccess = viewer.level().registryAccess();
        RegistryOps<Tag> ops = registryAccess.createSerializationContext(NbtOps.INSTANCE);

        Optional<ListTag> enderListTag = playerData.getList("EnderItems");
        if (enderListTag.isPresent()) {
            ListTag enderItems = enderListTag.get();
            for (int i = 0; i < enderItems.size(); i++) {
                Optional<CompoundTag> slotTagOpt = enderItems.getCompound(i);
                if (slotTagOpt.isEmpty()){
                    continue;
                }
                CompoundTag slotTag = slotTagOpt.get();

                int slot = slotTag.getByte("Slot").orElse((byte) -1) & 0xFF;
                ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, slotTag).result().orElse(ItemStack.EMPTY);

                if (!stack.isEmpty() && slot < 27) {
                    offlineInv.setItem(slot, stack);
                }
            }
        }

        MinecraftServer server = src.getServer();

        ComponentBuilder screenTitleComponentBuilder = new ComponentBuilder(src);
        screenTitleComponentBuilder.appendTranslatable("commands.helpful_commands.echest.screen_title", Component.literal(targetName), Component.literal(TranslationManager.translate(src, "helpful_commands.common.offline")));
        viewer.openMenu(new SimpleMenuProvider((syncId, viewerInv, player) -> new OfflineEchestMenu(syncId, viewerInv, offlineInv, updatedInv -> saveOfflineInventory(server, targetUUID, playerData, updatedInv, ops)), screenTitleComponentBuilder.build()));

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        ComponentBuilder feedbackComponentBuilder = new ComponentBuilder(src);
        feedbackComponentBuilder.appendTranslatable("commands.helpful_commands.echest.other", Component.literal(targetName).setStyle(textStyles.getPrimary())).setStyle(textStyles.getSuccess());

        src.sendSuccess(feedbackComponentBuilder::build, true);
    }

    private void saveOfflineInventory(MinecraftServer server, UUID uuid, CompoundTag originalData, SimpleContainer inv, RegistryOps<Tag> ops) {
        ListTag enderTag = new ListTag();

        for (int i = 0; i < 27; i++) {
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
                enderTag.add(compound);
            }
        }

        originalData.put("EnderItems", enderTag);
        PlayerDataUtil.saveOfflinePlayerData(server, uuid, originalData);
    }

    private static class OnlineEchestMenu extends AbstractContainerMenu {
        public OnlineEchestMenu(int syncId, Inventory viewerInventory, Player target) {
            super(MenuType.GENERIC_9x3, syncId);

            SimpleContainer enderChest = target.getEnderChestInventory();

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(enderChest, row * 9 + col, 0, 0));
                }
            }

            this.addStandardInventorySlots(viewerInventory, 0, 0);
        }

        @Override
        public @NonNull ItemStack quickMoveStack(@NonNull Player player, int slotIndex) {
            ItemStack newStack = ItemStack.EMPTY;
            Slot slot = this.slots.get(slotIndex);

            if (slot.hasItem()) {
                ItemStack originalStack = slot.getItem();
                newStack = originalStack.copy();

                int targetSlotCount = 27;
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

    private static class OfflineEchestMenu extends AbstractContainerMenu {
        private final SimpleContainer offlineContainer;
        private final Consumer<SimpleContainer> onClose;

        public OfflineEchestMenu(int syncId, Inventory viewerInventory, SimpleContainer offlineInv, Consumer<SimpleContainer> onClose) {
            super(MenuType.GENERIC_9x3, syncId);
            this.offlineContainer = offlineInv;
            this.onClose = onClose;

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(offlineInv, row * 9 + col, 0, 0));
                }
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

            if (slot.hasItem()) {
                ItemStack originalStack = slot.getItem();
                newStack = originalStack.copy();

                int targetSlotCount = 27;
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
}
