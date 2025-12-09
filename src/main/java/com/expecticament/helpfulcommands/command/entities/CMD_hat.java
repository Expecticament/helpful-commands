package com.expecticament.helpfulcommands.command.entities;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CMD_hat implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
                .executes(CMD_hat::execute)
                .then(CommandManager.argument("target", EntityArgumentType.players())
                        .executes(ctx -> execute(ctx, EntityArgumentType.getPlayers(ctx, "target")))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(ctx -> execute(ctx, EntityArgumentType.getPlayers(ctx, "target"), ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, true)))
                        )
                )
                .requires(src -> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        Collection<ServerPlayerEntity> collection = new ArrayList<>();
        collection.add(ctx.getSource().getPlayer());
        return execute(ctx, collection, null);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends ServerPlayerEntity> targets) {
        return execute(ctx, targets, null);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends ServerPlayerEntity> targets, ItemStack specifiedItemStack) {
        ServerCommandSource src = ctx.getSource();

        ServerPlayerEntity plr = src.getPlayer();

        ItemStack itemStack = specifiedItemStack;

        final Text targetAmountText = getTargetAmountText(new ArrayList<>(targets));

        if(specifiedItemStack == null) {
            if(!src.isExecutedByPlayer()) {
                src.sendError(Text.translatable("error.inGameOnly"));
                return -1;
            }
            ItemStack handItemStack = plr.getMainHandStack();
            if(handItemStack == null || handItemStack == ItemStack.EMPTY) {
                src.sendError(Text.translatable("error.nothingInMainHand"));
                return -1;
            }
            itemStack = handItemStack.copy();

            if (!plr.isCreative() && !plr.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(HelpfulCommands.defaultCommandLevel)))) {
                if(targets.size() > itemStack.getCount()) {
                    src.sendError(Text.translatable("commands.hat.error.notEnough", getItemNameText(handItemStack), targetAmountText));
                    return -1;
                }

                handItemStack.setCount(handItemStack.getCount() - targets.size());
            }
        }

        itemStack.setCount(1);

        boolean isSelfOnly = (targets.size() == 1 && targets.contains(plr));
        boolean isAir = (itemStack.getItem() == Items.AIR);

        final Text itemNameText = getItemNameText(itemStack);

        for(ServerPlayerEntity i : targets) {
            i.getInventory().setStack(39, itemStack.copy());

            Vec3d vec3 = i.getEntityPos();
            RegistryEntry<SoundEvent> registryEntry = RegistryEntry.of(SoundEvent.of(SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value().id()));
            i.networkHandler.sendPacket(new PlaySoundS2CPacket(registryEntry, SoundCategory.PLAYERS, vec3.getX(), vec3.getY(), vec3.getZ(), 1, 1, i.getEntityWorld().getRandom().nextLong()));

            if(i != plr) {
                if(isAir) {
                    i.sendMessage(Text.translatable("commands.hat.removed.self").setStyle(HelpfulCommands.style.tertiary));
                }
                else {
                    i.sendMessage(Text.translatable("commands.hat.success.self", itemNameText).setStyle(HelpfulCommands.style.tertiary));
                }
            }
        }

        if(!isSelfOnly) {
            if(isAir) {
                src.sendFeedback(()-> Text.translatable("commands.hat.removed.other", targetAmountText).setStyle(HelpfulCommands.style.success), true);
            } else {
                src.sendFeedback(()-> Text.translatable("commands.hat.success.other", itemNameText, targetAmountText).setStyle(HelpfulCommands.style.success), true);
            }
        } else {
            if(isAir) {
                plr.sendMessage(Text.translatable("commands.hat.removed.self").setStyle(HelpfulCommands.style.success));
            }  else {
                plr.sendMessage(Text.translatable("commands.hat.success.self", itemNameText).setStyle(HelpfulCommands.style.success));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static Text getTargetAmountText(List<ServerPlayerEntity> targets) {
        StringBuilder names = new StringBuilder();
        for(ServerPlayerEntity i : targets) {
            names.append(i.getDisplayName().getString());
            if(i != targets.getLast()) {
                names.append("\n");
            }
        }

        return Text.literal(String.valueOf(targets.size())).setStyle(HelpfulCommands.style.primary.withHoverEvent(
                new HoverEvent.ShowText(Text.literal(names.toString()))
        ));
    }

    private static Text getItemNameText(ItemStack itemStack) {
        String name = (itemStack.get(DataComponentTypes.CUSTOM_NAME) == null) ? itemStack.getItemName().getString() : itemStack.getCustomName().getString();
        return Text.literal(name).setStyle(HelpfulCommands.style.primary);
    }
}
