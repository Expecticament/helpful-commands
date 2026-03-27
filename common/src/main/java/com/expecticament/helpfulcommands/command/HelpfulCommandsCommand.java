package com.expecticament.helpfulcommands.command;

import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
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
    protected static final DynamicCommandExceptionType NO_ITEMS_FOUND = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.noItemsFound").getComponent()
    );
    protected static final DynamicCommandExceptionType EMPTY_ITEM_STACK_MAIN_HAND = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.emptyItemStack.mainHand").getComponent()
    );
    protected static final Dynamic2CommandExceptionType UNKNOWN_DIMENSION = new Dynamic2CommandExceptionType((src, dimensionName) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(dimensionName.toString()).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary())).getComponent()
    );
    protected static final DynamicCommandExceptionType TARGET_MUST_BE_OTHER_PLAYER = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.targetMustBeOtherPlayer").getComponent()
    );
    protected static final DynamicCommandExceptionType FAILED_TO_TELEPORT = new DynamicCommandExceptionType(src ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.failedToTeleport").getComponent()
    );
    protected static final Dynamic2CommandExceptionType ON_COOLDOWN_TELEPORT = new Dynamic2CommandExceptionType((src, remaining) ->
            new TextBuilder((CommandSourceStack) src).appendTranslatable("error.helpful_commands.onCooldown.teleport", StylingHelper.formatDuration((long) remaining, (CommandSourceStack) src)).getComponent()
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
