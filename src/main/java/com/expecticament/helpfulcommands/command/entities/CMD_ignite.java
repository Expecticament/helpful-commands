package com.expecticament.helpfulcommands.command.entities;

import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collection;

public class CMD_ignite implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.argument("target(s)", EntityArgumentType.entities())
                        .then(CommandManager.argument("duration", IntegerArgumentType.integer(0))
                                .executes(ctx -> execute(ctx, EntityArgumentType.getEntities(ctx, "target(s)"), IntegerArgumentType.getInteger(ctx, "duration")))
                        )
                )
                .requires(src -> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends Entity> targets, int duration) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();

        if(targets == null || (targets.size() == 1 && targets.contains(player))) {
            if(ignite(player, duration)) {
                src.sendFeedback(() -> Text.translatable("commands.ignite.success.self", Text.literal(String.valueOf(duration)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("error.nothingChanged"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        ArrayList<Entity> list = new ArrayList<>();
        for(Entity i : targets) {
            if(ignite(i, duration)) {
                list.add(i);
                if(commandFeedback && i.isPlayer() && i != player) {
                    ((ServerPlayerEntity) i).sendMessage(Text.translatable("commands.ignite.success.self", Text.literal(String.valueOf(duration)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.tertiary));
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
            src.sendFeedback(() -> Text.translatable("commands.ignite.success.other", finalCount, Text.literal(String.valueOf(duration)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }

    private static boolean ignite(Entity entity, int duration) {
        if(entity.isPlayer()) {
            if(((ServerPlayerEntity) entity).isInCreativeMode()) {
                return false;
            }
        }

        if(entity.isFireImmune()) {
            return false;
        }

        entity.setOnFireFor(duration);

        return true;
    }
}