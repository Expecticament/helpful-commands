package com.expecticament.helpful_commands.command.itemsAndInventory;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.GameRulesUtil;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

public class DisenchantCommand extends HelpfulCommandsCommand {
    public record EnchantmentData(Enchantment enchantment, int level) {}

    private static final Dynamic3CommandExceptionType NO_ENCHANTMENT_SELF = new Dynamic3CommandExceptionType((src, itemName, enchantmentName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.disenchant.error.noEnchantment",
                    Component.literal(itemName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary()),
                    (Component) enchantmentName).getComponent()
    );
    private static final Dynamic2CommandExceptionType NO_ENCHANTMENTS_SELF = new Dynamic2CommandExceptionType((src, itemName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("commands.helpful_commands.disenchant.error.noEnchantments",
                    Component.literal(itemName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );

    public DisenchantCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(ctx -> executeSelf(ctx, null))
                .then(Commands.argument("enchantment", ResourceArgument.resource(buildContext, Registries.ENCHANTMENT))
                        .executes(ctx -> executeSelf(ctx, ResourceArgument.getEnchantment(ctx, "enchantment")))
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DISENCHANT_OTHERS))
                                .executes(ctx -> executeOther(ctx, ResourceArgument.getEnchantment(ctx, "enchantment"), EntityArgument.getEntities(ctx, "entities")))
                        )
                )
                .then(Commands.literal("*")
                        .executes(ctx -> executeSelf(ctx, null))
                        .then(Commands.argument("entities", EntityArgument.entities())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_DISENCHANT_OTHERS))
                                .executes(ctx -> executeOther(ctx, null, EntityArgument.getEntities(ctx, "entities")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_DISENCHANT);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Holder<Enchantment> enchantmentHolder) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        ItemStack mainHandItemStack = sourcePlayer.getMainHandItem();
        if (mainHandItemStack.isEmpty()) {
            throw EMPTY_ITEM_STACK_MAIN_HAND.create(src);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        TextBuilder srcFeedback = new TextBuilder(src);
        Component itemNameComponent = StylingUtil.getItemStackName(mainHandItemStack);

        if (enchantmentHolder != null) {
            EnchantmentData removedEnchantment = removeEnchantment(mainHandItemStack, enchantmentHolder);
            if (removedEnchantment != null) {
                srcFeedback.appendTranslatable("commands.helpful_commands.disenchant.self",
                        getEnchantmentNameAndLevelComponent(removedEnchantment.enchantment(), removedEnchantment.level(), textStyles.getPrimary()),
                        new TextBuilder(src).appendTranslatable("commands.helpful_commands.disenchant.enchantment").getComponent(),
                        itemNameComponent);
                srcFeedback.setStyle(textStyles.getSuccess());
                src.sendSuccess(srcFeedback::getComponent, true);
                return Command.SINGLE_SUCCESS;
            } else {
                throw NO_ENCHANTMENT_SELF.create(src, itemNameComponent.getString(), Component.literal(enchantmentHolder.value().description().getString()).setStyle(textStyles.getPrimary()));
            }
        } else {
            List<EnchantmentData> removedEnchantments = removeAllEnchantments(mainHandItemStack);
            int count = removedEnchantments.size();
            if (count > 0) {
                TextBuilder enchantmentTextBuilder = new TextBuilder(src);
                enchantmentTextBuilder.appendTranslatable("commands.helpful_commands.disenchant.enchantment" + (count == 1 ? "" : "s"));
                srcFeedback.appendTranslatable("commands.helpful_commands.disenchant.self",
                        getRemovedEnchantmentsCountComponent(removedEnchantments, textStyles),
                        enchantmentTextBuilder.getComponent(),
                        itemNameComponent);
                srcFeedback.setStyle(textStyles.getSuccess());
                src.sendSuccess(srcFeedback::getComponent, true);
                return count;
            } else {
                throw NO_ENCHANTMENTS_SELF.create(src, itemNameComponent.getString());
            }
        }
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Holder<Enchantment> enchantmentHolder, Collection<? extends Entity> entities) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && entities.size() == 1 && entities.contains(sourcePlayer)) {
            return executeSelf(ctx, enchantmentHolder);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        Map<Entity, List<EnchantmentData>> affected = new HashMap<>();
        Map<Entity, ItemStack> affectedItems = new HashMap<>();
        int removedCount = 0;
        for (Entity entity : entities) {
            ItemStack itemStack = entity.getWeaponItem();
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            List<EnchantmentData> list = affected.getOrDefault(entity, new ArrayList<>());
            affectedItems.put(entity, itemStack);

            if (enchantmentHolder != null) {
                EnchantmentData removedEnchantment = removeEnchantment(itemStack, enchantmentHolder);
                if (removedEnchantment == null) {
                    continue;
                }

                if (entity.isAlwaysTicking() && commandFeedback) {
                    ServerPlayer player = (ServerPlayer) entity;
                    if (player != sourcePlayer) {
                        TextBuilder textBuilder = new TextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        textBuilder.appendTranslatable("commands.helpful_commands.disenchant.affected",
                                getEnchantmentNameAndLevelComponent(removedEnchantment.enchantment(), removedEnchantment.level(), textStyles.getPrimary()),
                                new TextBuilder(player).appendTranslatable("commands.helpful_commands.disenchant.enchantment").getComponent(),
                                new TextBuilder(player).appendTranslatable("commands.helpful_commands.disenchant.affected.single").getComponent(),
                                StylingUtil.getItemStackName(itemStack))
                                .setStyle(textStyles.getAffectedNeutral());

                        player.sendSystemMessage(textBuilder.getComponent());
                    }
                }

                removedCount++;
                list.add(removedEnchantment);
            } else {
                List<EnchantmentData> removedEnchantments = removeAllEnchantments(itemStack);
                if (removedEnchantments.isEmpty()) {
                    continue;
                }

                if (entity.isAlwaysTicking() && commandFeedback) {
                    ServerPlayer player = (ServerPlayer) entity;
                    if (player != sourcePlayer) {
                        int removedSize = removedEnchantments.size();
                        TextBuilder textBuilder = new TextBuilder(player);
                        textBuilder.setStyle(textStyles.getAffectedPositive());
                        textBuilder.appendTranslatable("commands.helpful_commands.disenchant.affected",
                                        getRemovedEnchantmentsCountComponent(removedEnchantments, textStyles),
                                        new TextBuilder(player).appendTranslatable("commands.helpful_commands.disenchant.enchantment" + (removedSize == 1 ? "" : "s")).getComponent(),
                                        new TextBuilder(player).appendTranslatable("commands.helpful_commands.disenchant.affected." + (removedSize == 1 ? "single" : "multiple")).getComponent(),
                                        StylingUtil.getItemStackName(itemStack))
                                .setStyle(textStyles.getAffectedNeutral());

                        player.sendSystemMessage(textBuilder.getComponent());
                    }
                }

                removedCount += removedEnchantments.size();
                list.addAll(removedEnchantments);
            }

            affected.put(entity, list);
        }

        if (removedCount == 0) {
            throw NO_ITEMS_FOUND.create(src);
        }

        TextBuilder feedback = new TextBuilder(src);
        feedback.appendTranslatable("commands.helpful_commands.disenchant.others",
                getRemovedEnchantmentsCountComponent(src, affected, affectedItems, removedCount, textStyles),
                new TextBuilder(src).appendTranslatable("commands.helpful_commands.disenchant.enchantment" + (removedCount == 1 ? "" : "s")).getComponent())
                .setStyle(textStyles.getSuccess());

        src.sendSuccess(feedback::getComponent, true);

        return affected.size();
    }

    private static EnchantmentData removeEnchantment(ItemStack itemStack, Holder<Enchantment> enchantmentHolder) {
        ItemEnchantments currentEnchantments = itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable newEnchantments = new ItemEnchantments.Mutable(currentEnchantments);

        int level = currentEnchantments.getLevel(enchantmentHolder);
        if (level > 0) {
            newEnchantments.set(enchantmentHolder, 0);
            itemStack.set(DataComponents.ENCHANTMENTS, newEnchantments.toImmutable());
            return new EnchantmentData(enchantmentHolder.value(), level);
        }

        return null;
    }

    private static List<EnchantmentData> removeAllEnchantments(ItemStack itemStack) {
        ItemEnchantments currentEnchantments = itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable newEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

        List<EnchantmentData> removedEnchantments = new ArrayList<>();

        if (!currentEnchantments.isEmpty()) {
            itemStack.set(DataComponents.ENCHANTMENTS, newEnchantments.toImmutable());

            for (Holder<Enchantment> holder : currentEnchantments.keySet()) {
                int level = currentEnchantments.getLevel(holder);
                removedEnchantments.add(new EnchantmentData(holder.value(), level));
            }
        }

        return removedEnchantments;
    }

    private MutableComponent getEnchantmentLevelComponent(int level) {
        return (level <= 5 ? Component.translatable("enchantment.level.%d".formatted(level)) : Component.literal(String.valueOf(level)));
    }

    private Component getEnchantmentNameAndLevelComponent(Enchantment enchantment, int level) {
        return getEnchantmentNameAndLevelComponent(enchantment, level, Style.EMPTY);
    }

    private Component getEnchantmentNameAndLevelComponent(Enchantment enchantment, int level, Style style) {
        MutableComponent component = Component.empty();
        component
                .append(enchantment.description())
                .append(" ")
                .append(getEnchantmentLevelComponent(level))
                .setStyle(style);
        return component;
    }

    private Component getRemovedEnchantmentsCountComponent(List<EnchantmentData> removedEnchantments, HelpfulCommandsStyle.TextStyles textStyles) {
        MutableComponent enchantmentList = Component.empty();
        boolean isFirst = true;
        for (EnchantmentData removed : removedEnchantments) {
            if (!isFirst) {
                enchantmentList.append("\n");
            }

            enchantmentList.append(getEnchantmentNameAndLevelComponent(removed.enchantment(), removed.level));

            isFirst = false;
        }

        return Component.literal(String.valueOf(removedEnchantments.size())).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(enchantmentList)));
    }

    private Component getRemovedEnchantmentsCountComponent(CommandSourceStack src, Map<Entity, List<EnchantmentData>> affected, Map<Entity, ItemStack> affectedItems, int count, HelpfulCommandsStyle.TextStyles textStyles) {
        TextBuilder textBuilder = new TextBuilder(src);

        boolean isFirstEntry = true;
        for (Map.Entry<Entity, List<EnchantmentData>> entry : affected.entrySet()) {
            if (!isFirstEntry) {
                textBuilder.appendNewline();
            }

            Entity entity = entry.getKey();
            List<EnchantmentData> removedEnchantments = entry.getValue();

            String entityName = entity.getName().getString();
            Component entityNameComponent = Component.literal(entityName).setStyle(entity.isAlwaysTicking() ? textStyles.getSecondary() : Style.EMPTY);
            Component itemNameComponent = StylingUtil.getItemStackName(affectedItems.get(entity));

            MutableComponent enchantmentList = Component.empty();
            boolean isFirstRemoved = true;
            for (EnchantmentData removed : removedEnchantments) {
                if (!isFirstRemoved) {
                    enchantmentList.append(", ");
                }

                enchantmentList.append(getEnchantmentNameAndLevelComponent(removed.enchantment(), removed.level));

                isFirstRemoved = false;
            }

            textBuilder.appendTranslatable("commands.helpful_commands.disenchant.hover.entry", entityNameComponent, itemNameComponent, enchantmentList);

            isFirstEntry = false;
        }

        return Component.literal(String.valueOf(count)).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(textBuilder.getComponent())));
    }
}
