package com.expecticament.helpfulcommands.command.movementAndTeleportation;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.expecticament.helpfulcommands.manager.TranslationManager.DeprecatedTextBuilder;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import net.minecraft.world.entity.Relative;

import java.util.Optional;

public class CMD_deathpos extends HelpfulCommandsCommand {

    protected static final SimpleCommandExceptionType NO_DEATH_POS = new SimpleCommandExceptionType(Component.empty());

    public CMD_deathpos(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("tp")
                        .requires(ctx -> canExecute(ctx, "tp") || canExecute(ctx, "tp.other"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> canExecute(src, "tp.other"))
                                .executes(ctx -> teleport(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::teleport)
                )
                .then(Commands.literal("query")
                        .requires(src -> canExecute(src, "query") || canExecute(src, "query.other"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> canExecute(src, "query.other"))
                                .executes(ctx -> query(ctx, EntityArgument.getPlayer(ctx, "player")))
                        )
                        .executes(this::query)
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source) || canExecute(source, "tp") || canExecute(source, "tp.other") || canExecute(source, "query") || canExecute(source, "query.other");
    }

    private int query(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return query(ctx, null);
    }

    private int query(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (player == null) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }
            player = sourcePlayer;
        }

        boolean self = player == sourcePlayer;
        String trKeyEnd = self ? "self" : "other";

        Optional<GlobalPos> deathPos = player.getLastDeathLocation();

        if (deathPos.isEmpty()) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
            if (!self) {
                textBuilder.appendComponent(StylingHelper.getAffectedEntityNameText(player));
            }
            textBuilder.appendTranslatable("commands.helpful_commands.deathpos.output.error.noDeathPos." + trKeyEnd);
            throw new CommandSyntaxException(NO_DEATH_POS, textBuilder.getComponent());
        }

        GlobalPos globalPos = deathPos.get();

        DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);

        if (!self) {
            textBuilder.appendComponent(StylingHelper.getAffectedEntityNameText(player));
        }

        textBuilder
                .appendTranslatable("commands.helpful_commands.deathpos.query.output." + trKeyEnd)
                .appendWhitespace()
                .appendComponent(StylingHelper.getLocationText(globalPos));

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleport(ctx, null);
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        if (player == null) {
            if (sourcePlayer == null) {
                throw new CommandSyntaxException(SELECTOR_REQUIRED, Component.literal(TranslationManager.translate(src, "error.helpful_commands.selectorRequired")));
            }
            player = sourcePlayer;
        }

        boolean self = player == sourcePlayer;
        String trKeyEnd = self ? "self" : "other";

        Optional<GlobalPos> deathPos = player.getLastDeathLocation();

        if (deathPos.isEmpty()) {
            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);
            if (!self) {
                textBuilder.appendComponent(StylingHelper.getAffectedEntityNameText(player));
            }
            textBuilder.appendTranslatable("commands.helpful_commands.deathpos.output.error.noDeathPos." + trKeyEnd);
            throw new CommandSyntaxException(NO_DEATH_POS, textBuilder.getComponent());
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        GlobalPos globalPos = deathPos.get();
        String levelLocation = globalPos.dimension().location().toString();

        try {
            ServerLevel level = ServerLevelHelper.getLevel(levelLocation);
            BlockPos blockPos = globalPos.pos();

            sourcePlayer.teleportTo(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), Relative.DELTA, sourcePlayer.getYRot(), sourcePlayer.getXRot(), false);

            DeprecatedTextBuilder textBuilder = new DeprecatedTextBuilder(src);

            textBuilder
                    .appendTranslatable("commands.helpful_commands.deathpos.teleport.output.success.part1")
                    .appendWhitespace();

            if (self) {
                textBuilder.appendTranslatable("commands.helpful_commands.deathpos.teleport.output.success.part2.self");
            } else {
                textBuilder
                        .appendComponent(StylingHelper.getAffectedEntityNameText(player))
                        .appendTranslatable("commands.helpful_commands.deathpos.teleport.output.success.part2.other");
            }

            textBuilder
                    .appendWhitespace()
                    .appendTranslatable("commands.helpful_commands.deathpos.teleport.output.success.part3");
            textBuilder.setStyle(textStyles.getSuccess());

            src.sendSuccess(textBuilder::getComponent, true);

            return Command.SINGLE_SUCCESS;
        } catch (ServerLevelHelper.UnknownServerLevelException e) {
            TextBuilder textBuilder = new TextBuilder(src);
            textBuilder.appendTranslatable("error.helpful_commands.unknownDimension", Component.literal(levelLocation).setStyle(textStyles.getPrimary()));
            throw new CommandSyntaxException(UNKNOWN_DIMENSION, textBuilder.getComponent());
        }
    }
}
