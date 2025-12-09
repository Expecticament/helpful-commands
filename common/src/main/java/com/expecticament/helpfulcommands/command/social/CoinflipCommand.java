package com.expecticament.helpfulcommands.command.social;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.helper.StylingHelper;
import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.TranslationManager;
import com.expecticament.helpfulcommands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class CoinflipCommand extends HelpfulCommandsCommand {

    public CoinflipCommand(ModCommandManager.CommandData commandData) {
        super(commandData);
    }

    private enum CoinSide {
        HEADS,
        TAILS;

        public static CoinSide randomSide(RandomSource random) {
            return values()[random.nextInt(values().length)];
        }
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.CommandData commandData = getCommandData();

        dispatcher.register(Commands.literal(commandData.getName())
                .requires(this::canExecuteBaseCommand)
                .then(Commands.literal("heads")
                        .executes(ctx -> execute(ctx, CoinSide.HEADS))
                )
                .then(Commands.literal("tails")
                        .executes(ctx -> execute(ctx, CoinSide.TAILS))
                )
        );
    }

    @Override
    public boolean canExecuteBaseCommand(CommandSourceStack source) {
        return canExecute(source);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, CoinSide guessedCoinSide) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        CoinSide landedCoinSide = CoinSide.randomSide(sourcePlayer.level().getRandom());
        boolean won = guessedCoinSide == landedCoinSide;

        List<ServerPlayer> playerList = new ArrayList<>(sourcePlayer.level().getServer().getPlayerList().getPlayers());

        Component affectedPlayerText = StylingHelper.getAffectedEntityNameText(sourcePlayer);

        for (ServerPlayer player : playerList) {
            Component guessedCoinSideText = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + guessedCoinSide.toString().toLowerCase())).setStyle(textStyles.getPrimary());
            Component landedCoinSideText = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + landedCoinSide.toString().toLowerCase())).setStyle(textStyles.getPrimary());
            Component resultText = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + (won ? "won" : "lost"))).setStyle(won ? textStyles.getAffectedPositive() : textStyles.getAffectedNegative());

            TextBuilder textBuilder = new TextBuilder(player);
            textBuilder.appendTranslatable("commands.helpful_commands.coinflip", affectedPlayerText, guessedCoinSideText, landedCoinSideText, resultText);
            if (won) {
                textBuilder.appendLiteral("!");
            }

            player.sendSystemMessage(textBuilder.getComponent());
        }

        Vec3 vec3 = sourcePlayer.position();
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent((won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WANDERING_TRADER_NO).location()));
        sourcePlayer.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, vec3.x(), vec3.y(), vec3.z(), 0.5f, 1, src.getLevel().getRandom().nextLong()));

        return Command.SINGLE_SUCCESS;
    }
}
