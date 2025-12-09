package com.expecticament.helpfulcommands.command.entities;

import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collection;

public class CMD_heal implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.argument("target(s)", EntityArgumentType.entities())
                        .executes(ctx -> execute(ctx, EntityArgumentType.getEntities(ctx,"target(s)"), 0))
                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0.5f))
                                .executes(ctx -> execute(ctx, EntityArgumentType.getEntities(ctx,"target(s)"), FloatArgumentType.getFloat(ctx, "amount")))
                        )
                )
                .executes(ctx -> execute(ctx, null, 0))
                .requires(src -> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends Entity> targets, float amount) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();

        if(targets == null || (targets.size() == 1 && targets.contains(player))) {
            if(heal(player, amount)) {
                src.sendFeedback(() -> Text.translatable((amount < 0.5f) ? "commands.heal.success.self" : "commands.heal.success.hearts.self", Text.literal(String.valueOf(amount / 2)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("error.nothingChanged"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        ArrayList<Entity> list = new ArrayList<>();
        for(Entity i : targets) {
            if(!i.isLiving()) {
                continue;
            }
            LivingEntity livingEntity = (LivingEntity) i;
            if(heal(livingEntity, amount)) {
                list.add(i);
                if(commandFeedback && i.isPlayer() && i != player) {
                    ((ServerPlayerEntity) i).sendMessage(Text.translatable((amount < 0.5f) ? "commands.heal.success.self" : "commands.heal.success.hearts.self", Text.literal(String.valueOf(amount / 2)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success));
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
            src.sendFeedback(() -> Text.translatable((amount < 0.5f) ? "commands.heal.success.other" : "commands.heal.success.hearts.other", finalCount, Text.literal(String.valueOf(amount / 2)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }

    private static boolean heal(LivingEntity livingEntity, float amount) {
        if(livingEntity.getHealth() < livingEntity.getMaxHealth()) {
            livingEntity.setHealth((amount < 0.5f) ? livingEntity.getMaxHealth() : livingEntity.getHealth() + amount);
            return true;
        }

        return false;
    }
}