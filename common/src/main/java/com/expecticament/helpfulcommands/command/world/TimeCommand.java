package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.expecticament.helpfulcommands.manager.ModCommandManager.CommandData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;

public class TimeCommand extends HelpfulCommandsCommand {

    private final int time;

    public TimeCommand(CommandData commandData, int time) {
        super(commandData);
        this.time = time;
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .executes(this::execute)
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        MinecraftServer server = src.getServer();

        for (ServerLevel serverLevel : server.getAllLevels()) {
            serverLevel.setDayTime(time);
        }

        server.forceTimeSynchronization();

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder hoverTextBuilder = new TextBuilder(src);
        hoverTextBuilder.appendTranslatable("commands.helpful_commands.time." + time);
        Component timeValueText = Component.literal(String.valueOf(time)).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(hoverTextBuilder.getComponent())));

        TextBuilder messageTextBuilder = new TextBuilder(src);
        messageTextBuilder.appendTranslatable("commands.helpful_commands.time", timeValueText).setStyle(textStyles.getSuccess());

        src.sendSuccess(messageTextBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }
}
