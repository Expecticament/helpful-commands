package com.expecticament.helpfulcommands.command.entities;

import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collection;

public class CMD_feed implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.argument("target(s)", EntityArgumentType.players())
                        .executes(ctx -> execute(ctx, EntityArgumentType.getPlayers(ctx,"target(s)")))
                )
                .executes(ctx -> execute(ctx, null))
                .requires(src->ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends ServerPlayerEntity> targets) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();

        if(targets == null || (targets.size() == 1 && targets.contains(player))) {
            if(feed(player)) {
                src.sendFeedback(() -> Text.translatable("commands.feed.success.self").setStyle(HelpfulCommands.style.success), true);
                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("error.nothingChanged"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        ArrayList<Entity> list = new ArrayList<>();
        for(ServerPlayerEntity i : targets) {
            if(feed(i)) {
                list.add(i);
                if(commandFeedback && i != player) {
                    i.sendMessage(Text.translatable("commands.feed.success.self").setStyle(HelpfulCommands.style.success));
                }
            }
        }

        int affectedCount = list.size();

        if(affectedCount < 1) {
            src.sendError(Text.translatable("error.didntFindTargets"));
            return 0;
        }

        if(commandFeedback) {
            MutableText finalCount = Text.literal(String.valueOf(affectedCount)).setStyle(HelpfulCommands.style.primary
                    .withHoverEvent(ModCommandManager.targetListToHoverEvent(list))
            );
            src.sendFeedback(() -> Text.translatable("commands.feed.success.other", finalCount).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }

    private static boolean feed(ServerPlayerEntity player) {
        HungerManager hungerManager = player.getHungerManager();
        if(hungerManager.isNotFull()) {
            hungerManager.setFoodLevel(20);
            hungerManager.setSaturationLevel(20);
            return true;
        }

        return false;
    }
}