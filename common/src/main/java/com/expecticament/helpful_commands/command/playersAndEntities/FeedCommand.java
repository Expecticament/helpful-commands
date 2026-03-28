package com.expecticament.helpful_commands.command.playersAndEntities;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.helper.GameRulesHelper;
import com.expecticament.helpful_commands.helper.PermissionHelper;
import com.expecticament.helpful_commands.helper.StylingHelper;
import com.expecticament.helpful_commands.manager.ModCommandManager;
import com.expecticament.helpful_commands.manager.StylingManager;
import com.expecticament.helpful_commands.manager.TranslationManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.style.HelpfulCommandsStyle;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import java.util.Collection;
import java.util.List;

public class FeedCommand extends HelpfulCommandsCommand {
    public FeedCommand(ModCommandManager.ModCommand modCommand) {
        super(modCommand);
    }

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection commandSelection) {
        ModCommandManager.ModCommand modCommand = getModCommand();

        dispatcher.register(Commands.literal(modCommand.getName())
                .requires(this::canExecute)
                .executes(this::executeSelf)
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(src -> PermissionHelper.hasPermission(src, ModPermissions.Permission.COMMAND_FEED_OTHERS))
                        .executes(ctx -> executeOther(ctx, EntityArgument.getPlayers(ctx, "players")))
                )
        );
    }

    @Override
    protected boolean checkBaseCommandRequirements(CommandSourceStack source) {
        return PermissionHelper.hasPermission(source, ModPermissions.Permission.COMMAND_FEED);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validatePlayerOnly(src);

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);

        if (!feed(sourcePlayer)) {
            return 0;
        }

        textBuilder.appendTranslatable("commands.helpful_commands.feed.self");
        textBuilder.setStyle(textStyles.getAffectedPositive());

        src.sendSuccess(textBuilder::getComponent, true);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer sourcePlayer = validateAnySource(src);

        if (sourcePlayer != null && players.size() == 1 && players.contains(sourcePlayer)) {
            return executeSelf(ctx);
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        boolean commandFeedback = GameRulesHelper.commandFeedbackEnabled(src.getLevel());

        List<ServerPlayer> affected = players.stream()
                .filter(this::feed)
                .peek(player -> {
                    if (commandFeedback) {
                        if (player != sourcePlayer) {
                            TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(player);
                            textBuilder.appendTranslatable("commands.helpful_commands.feed.affected").setStyle(textStyles.getAffectedPositive());
                            player.sendSystemMessage(textBuilder.getComponent());
                        }
                    }
                })
                .toList();

        if (affected.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }

        MutableComponent affectedText = Component.empty();
        if (affected.size() == 1) {
            affectedText.append(StylingHelper.getAffectedEntityNameText(affected.getFirst()));
        } else {
            affectedText
                    .append(StylingHelper.getAffectedEntitiesNumberText(affected))
                    .append(" ")
                    .append(TranslationManager.translate(src, "commands.helpful_commands.feed.other.multiple"));
        }

        TranslationManager.TextBuilder textBuilder = new TranslationManager.TextBuilder(src);
        textBuilder.appendTranslatable("commands.helpful_commands.feed.other", affectedText).setStyle(textStyles.getSuccess());

        src.sendSuccess(textBuilder::getComponent, true);

        return affected.size();
    }

    private boolean feed(ServerPlayer player) {
        FoodData foodData = player.getFoodData();

        if (!(player.isAlive() && foodData.needsFood())) {
            return false;
        }

        foodData.setFoodLevel(20);
        foodData.setSaturation(5);

        return true;
    }
}
