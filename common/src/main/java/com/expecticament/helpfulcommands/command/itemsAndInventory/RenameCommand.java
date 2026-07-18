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
import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RenameCommand extends HelpfulCommandsCommand {
    private static final DynamicCommandExceptionType SAME_NAME_PROVIDED = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.rename.error.same_name_provided").build()
    );
    private static final DynamicCommandExceptionType NO_CUSTOM_NAME = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("commands.helpfulcommands.rename.error.no_custom_name").build()
    );

    public RenameCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.argument("new_name", StringArgumentType.string())
                        .suggests((ctx, suggestionsBuilder) -> {
                            suggestionsBuilder.suggest("\"\"");
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(ctx -> executeSelf(ctx, StringArgumentType.getString(ctx, "new_name")))
                        .then(Commands.argument("players", EntityArgument.players())
                                .requires(src -> PermissionsUtil.hasPermission(src, ModPermissions.Permission.COMMAND_RENAME_OTHERS))
                                .executes(ctx -> executeOther(ctx, StringArgumentType.getString(ctx, "new_name"), EntityArgument.getPlayers(ctx, "players")))
                        )
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_RENAME);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, String newName) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        ItemStack mainHandItemStack = sourcePlayer.getMainHandItem();
        if (mainHandItemStack.isEmpty()) {
            throw EMPTY_ITEM_STACK_MAIN_HAND.create(src);
        }

        Component oldNameComponent = StylingUtil.getItemStackName(mainHandItemStack);

        ComponentBuilder componentBuilder = new ComponentBuilder(src);

        int result = rename(mainHandItemStack, newName);

        switch (result) {
            case -1:
                throw SAME_NAME_PROVIDED.create(src);
            case -2:
                throw NO_CUSTOM_NAME.create(src);
            case 1:
                Component newNameComponent = Component.literal(newName).setStyle(textStyles.getPrimary());
                componentBuilder.appendTranslatable("commands.helpfulcommands.rename.self", oldNameComponent, newNameComponent).setStyle(textStyles.getPrimary());
                break;
            case 2:
                componentBuilder.appendTranslatable("commands.helpfulcommands.rename.remove.self", oldNameComponent).setStyle(textStyles.getPrimary());
                break;
        }

        componentBuilder.setStyle(textStyles.getSuccess());

        src.sendSuccess(componentBuilder::build, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, String newName, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx, newName);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesUtil.isCommandFeedbackEnabled(src.getLevel());

        Component newNameComponent = Component.literal(newName).setStyle(textStyles.getPrimary());

        Map<ServerPlayer, String> affected = new HashMap<>();
        for (ServerPlayer player : players) {
            ItemStack mainHandItemStack = player.getMainHandItem();
            if (mainHandItemStack.isEmpty()) {
                continue;
            }

            String oldName = StylingUtil.getItemStackName(mainHandItemStack).getString();

            int result = rename(player.getMainHandItem(), newName);
            if (result < 0) {
                continue;
            }

            if (commandFeedback && sourcePlayer != player) {
                ComponentBuilder componentBuilder = new ComponentBuilder(player);
                componentBuilder.setStyle(textStyles.getAffectedNeutral());
                Component oldNameComponent = Component.literal(oldName).setStyle(textStyles.getPrimary());
                switch (result) {
                    case 1:
                        componentBuilder.appendTranslatable("commands.helpfulcommands.rename.affected", oldNameComponent, newNameComponent);
                        break;
                    case 2:
                        componentBuilder.appendTranslatable("commands.helpfulcommands.rename.remove.affected", oldNameComponent);
                        break;
                }

                player.sendSystemMessage(componentBuilder.build());
            }

            affected.put(player, oldName);
        }

        if (affected.isEmpty()) {
            throw NO_ITEMS_FOUND.create(src);
        }

        ComponentBuilder feedback = new ComponentBuilder(src);
        feedback.setStyle(textStyles.getSuccess());
        if (affected.size() == 1) {
            Component affectedPlayer = StylingUtil.getAffectedEntityNameText(affected.keySet().iterator().next());
            Component oldName = Component.literal(affected.values().iterator().next()).setStyle(textStyles.getPrimary());
            if (newName.isEmpty()) {
                feedback.appendTranslatable("commands.helpfulcommands.rename.remove.other", affectedPlayer, oldName);
            } else {
                feedback.appendTranslatable("commands.helpfulcommands.rename.other", affectedPlayer, oldName, newNameComponent);
            }
        } else {
            if (newName.isEmpty()) {
                feedback.appendTranslatable("commands.helpfulcommands.rename.remove.others", StylingUtil.getAffectedEntitiesNumberText(affected));
            } else {
                feedback.appendTranslatable("commands.helpfulcommands.rename.others", StylingUtil.getAffectedEntitiesNumberText(affected), newNameComponent);
            }
        }

        src.sendSuccess(feedback::build, true);

        return affected.size();
    }

    private int rename(ItemStack itemStack, String newName) {
        if (newName.isEmpty()) {
            if (itemStack.get(DataComponents.CUSTOM_NAME) == null) {
                return -2;
            }

            itemStack.remove(DataComponents.CUSTOM_NAME);

            return 2;
        } else {
            if (StylingUtil.getItemStackName(itemStack).getString().equals(newName)) {
                return -1;
            }

            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(newName));

            return 1;
        }
    }
}
