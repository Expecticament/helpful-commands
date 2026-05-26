package com.expecticament.helpful_commands.command.social;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.SoundUtil;
import com.expecticament.helpful_commands.util.StylingUtil;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.manager.TranslationManager.TextBuilder;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public class CoinflipCommand extends HelpfulCommandsCommand {
    public CoinflipCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
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
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .then(Commands.literal("heads")
                        .executes(ctx -> execute(ctx, CoinSide.HEADS))
                )
                .then(Commands.literal("tails")
                        .executes(ctx -> execute(ctx, CoinSide.TAILS))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionsUtil.hasPermission(source, ModPermissions.Permission.COMMAND_COINFLIP);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, CoinSide guessedCoinSide) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        CoinSide landedCoinSide = CoinSide.randomSide(sourcePlayer.level().getRandom());
        boolean won = guessedCoinSide == landedCoinSide;

        List<ServerPlayer> playerList = new ArrayList<>(sourcePlayer.level().getServer().getPlayerList().getPlayers());

        Component affectedPlayerText = StylingUtil.getAffectedEntityNameText(sourcePlayer);

        for (ServerPlayer player : playerList) {
            Component pickedCoinSideComponent = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + guessedCoinSide.toString().toLowerCase())).setStyle(textStyles.getPrimary());
            Component landedCoinSideComponent = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + landedCoinSide.toString().toLowerCase())).setStyle(textStyles.getPrimary());
            Component resultComponent = Component.literal(TranslationManager.translate(player, "commands.helpful_commands.coinflip." + (won ? "won" : "lost"))).setStyle(won ? textStyles.getAffectedPositive() : textStyles.getAffectedNegative());

            TextBuilder textBuilder = new TextBuilder(player);
            textBuilder.appendTranslatable("commands.helpful_commands.coinflip", landedCoinSideComponent, affectedPlayerText, pickedCoinSideComponent, resultComponent);
            if (won) {
                textBuilder.appendLiteral("!");
            }

            player.sendSystemMessage(textBuilder.getComponent());
        }

        SoundUtil.playSound(sourcePlayer, won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WANDERING_TRADER_NO, 0.5f, 1);

        return Command.SINGLE_SUCCESS;
    }
}
