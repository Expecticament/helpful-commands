package com.expecticament.helpfulcommands.command;

import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.translation.ComponentBuilder;
import com.expecticament.helpfulcommands.util.StylingUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public abstract class HelpfulCommandsCommand {
    protected static final DynamicCommandExceptionType TARGET_MUST_BE_OTHER_PLAYER = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.target_must_be_other_player").build()
    );
    protected static final DynamicCommandExceptionType NO_ITEMS_FOUND = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.no_items_found").build()
    );
    protected static final DynamicCommandExceptionType FAILED_TO_TELEPORT = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.failed_to_teleport").build()
    );
    protected static final DynamicCommandExceptionType EMPTY_ITEM_STACK_MAIN_HAND = new DynamicCommandExceptionType(src ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.empty_item_stack.main_hand").build()
    );

    protected static final Dynamic2CommandExceptionType PLAYER_DATA_NOT_FOUND = new Dynamic2CommandExceptionType((src, playerName) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.player_data_not_found", Component.literal(playerName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );
    protected static final Dynamic2CommandExceptionType UNKNOWN_DIMENSION = new Dynamic2CommandExceptionType((src, dimensionName) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.unknown_dimension", Component.literal(dimensionName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).build()
    );
    protected static final Dynamic2CommandExceptionType ON_COOLDOWN_TELEPORT = new Dynamic2CommandExceptionType((src, remaining) ->
            new ComponentBuilder((CommandSourceStack) src).appendTranslatable("helpfulcommands.error.on_cooldown.teleport", StylingUtil.formatDuration((long) remaining, (CommandSourceStack) src)).build()
    );

    private final ModCommandManager.ModCommand modCommand;

    public HelpfulCommandsCommand(ModCommandManager.ModCommand modCommand) {
        this.modCommand = modCommand;
    }

    public ModCommandManager.ModCommand getModCommand() {
        return modCommand;
    }

    public abstract void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection);

    public boolean canExecute(CommandSourceStack source) {
        if (modCommand.getCategory().equals(ModCommandManager.CommandCategory.MAIN)) {
            return true;
        }
        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        return config.getCommandState(modCommand.getName()) && checkBaseCommandRequirements(source);
    }

    protected abstract boolean checkBaseCommandRequirements(CommandSourceStack source);

    protected ServerPlayer validatePlayerOnly(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    protected ServerPlayer validateAnySource(CommandSourceStack source) {
        return source.getPlayer();
    }
}
