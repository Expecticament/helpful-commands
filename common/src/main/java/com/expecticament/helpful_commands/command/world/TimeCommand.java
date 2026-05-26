package com.expecticament.helpful_commands.command.world;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.dimension.DimensionType;

public class TimeCommand extends HelpfulCommandsCommand {
    private final int time;
    private final ModPermissions.Permission permission;

    private static final DynamicCommandExceptionType ERROR_NO_DEFAULT_CLOCK = new DynamicCommandExceptionType((dimension) -> Component.translatableEscape("commands.time.no_default_clock", new Object[]{dimension}));

    public TimeCommand(ModCommandManager.ModCommand modCommand, int time, ModPermissions.Permission permission) {
        super(modCommand);
        this.time = time;
        this.permission = permission;
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::execute)
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, permission);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        validateAnySource(src);

        MinecraftServer server = src.getServer();

        ServerClockManager clockManager = src.getServer().clockManager();
        ServerLevel level = src.getLevel();
        Holder<WorldClock> clock = getDefaultClock(level.dimensionTypeRegistration());

        long currentTotal = clockManager.getTotalTicks(clock);
        long currentTimeOfDay = currentTotal % 24000L;
        long ticksToAdd = time - currentTimeOfDay;
        if (ticksToAdd <= 0) {
            ticksToAdd += 24000L;
        }

        clockManager.setTotalTicks(clock, currentTotal + ticksToAdd);
        server.forceGameTimeSynchronization();

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TextBuilder hoverTextBuilder = new TextBuilder(src);
        hoverTextBuilder.appendTranslatable("commands.helpful_commands.time.ticks", Component.literal(String.valueOf(clockManager.getTotalTicks(clock))));
        TextBuilder timeTextBuilder = new TextBuilder(src);
        timeTextBuilder.appendTranslatable("commands.helpful_commands.time." + time).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(hoverTextBuilder.getComponent())));

        TextBuilder messageTextBuilder = new TextBuilder(src);
        messageTextBuilder.appendTranslatable("commands.helpful_commands.time", Component.literal(ServerLevelUtil.getLevelLocation(level)).setStyle(textStyles.getPrimary()), timeTextBuilder.getComponent()).setStyle(textStyles.getSuccess());

        src.sendSuccess(messageTextBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private static Holder<WorldClock> getDefaultClock(Holder<DimensionType> dimensionType) throws CommandSyntaxException {
        return (Holder)((DimensionType)dimensionType.value()).defaultClock().orElseThrow(() -> ERROR_NO_DEFAULT_CLOCK.create(dimensionType.getRegisteredName()));
    }
}
