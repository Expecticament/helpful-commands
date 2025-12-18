package com.expecticament.helpfulcommands.command;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.helper.PermissionHelper;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager.CommandData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public abstract class HelpfulCommandsCommand {
    protected static final SimpleCommandExceptionType NO_ITEMS_FOUND = new SimpleCommandExceptionType(Component.translatable("error.helpful_commands.noItemsFound"));
    protected static final SimpleCommandExceptionType UNKNOWN_DIMENSION = new SimpleCommandExceptionType(Component.translatable("error.helpful_commands.unknownDimension"));
    protected static final SimpleCommandExceptionType EMPTY_ITEM_STACK_MAIN_HAND = new SimpleCommandExceptionType(Component.translatable("error.helpful_commands.emptyItemStack.mainHand"));
    protected static final SimpleCommandExceptionType HC_COMMAND_EXCEPTION = new SimpleCommandExceptionType(Component.empty());

    private final CommandData data;

    public HelpfulCommandsCommand(CommandData commandData) {
        this.data = commandData;
    }

    public CommandData getCommandData() {
        return data;
    }

    public abstract void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection);

    public abstract boolean canExecuteBaseCommand(CommandSourceStack source);

    public boolean canExecute(CommandSourceStack source) {
        return canExecute(source, "", data.getDefaultOpLevel());
    }

    public boolean canExecute(CommandSourceStack source, String permCheckAppend) {
        return canExecute(source, permCheckAppend, data.getDefaultOpLevel());
    }

    public boolean canExecute(CommandSourceStack source, String permCheckAppend, int permLevelOverride) {
        if (data.getCategory() == ModCommandManager.CommandCategory.MAIN) {
            return true;
        }

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        if (!config.getCommandState(data.getName())) {
            return false;
        }

        if (!permCheckAppend.isEmpty()) {
            permCheckAppend = "." + permCheckAppend;
        }

        return PermissionHelper.hasPermission(source, HelpfulCommands.MOD_ID + ".command." + data.getCategory().toString().toLowerCase() + "." + data.getName() + permCheckAppend, permLevelOverride);
    }

    protected ServerPlayer validatePlayerOnly(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    protected ServerPlayer validateAnySource(CommandSourceStack source) {
        return source.getPlayer();
    }
}
