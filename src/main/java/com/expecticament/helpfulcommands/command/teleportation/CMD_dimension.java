package com.expecticament.helpfulcommands.command.teleportation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.rule.GameRules;

import java.util.*;

public class CMD_dimension implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.literal("switch")
                        .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                                .then(CommandManager.argument("target(s)", EntityArgumentType.entities())
                                        .executes(ctx -> switchDimension(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension"), EntityArgumentType.getEntities(ctx, "target(s)")))
                                )
                                .executes(ctx -> switchDimension(ctx, DimensionArgumentType.getDimensionArgument(ctx, "dimension"), null))
                        )
                )
                .then(CommandManager.literal("get")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .executes(ctx -> getDimension(ctx, EntityArgumentType.getPlayer(ctx, "target")))
                        )
                        .executes(ctx -> getDimension(ctx, null))
                )
                .requires(src->ModCommandManager.canUseCommand(src,cmd))
        );
    }

    private static int getDimension(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && target == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();

        if(target == null) {
            target = player;
        }

        String dimensionName = target.getEntityWorld().getRegistryKey().getValue().toString();

        if(target == player) {
            src.sendMessage(Text.translatable("commands.dimension.get.success.self", Text.literal(dimensionName).setStyle(HelpfulCommands.style.primary
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToTeleportToDimension")))
                    .withClickEvent(new ClickEvent.RunCommand("/dimension switch " + dimensionName))
            )).setStyle(HelpfulCommands.style.secondary));
        } else {
            src.sendMessage(Text.translatable("commands.dimension.get.success.other", Text.literal(target.getName().getString()).setStyle(HelpfulCommands.style.primary), Text.literal(dimensionName).setStyle(HelpfulCommands.style.primary
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToTeleportToDimension")))
                    .withClickEvent(new ClickEvent.RunCommand("/dimension switch " + dimensionName))
            )).setStyle(HelpfulCommands.style.secondary));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int switchDimension(CommandContext<ServerCommandSource> ctx, ServerWorld dimension, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null) {
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();
        String dimensionName = dimension.getRegistryKey().getValue().toString();

        if(targets == null || (targets.size() == 1 && targets.contains(player))) {
            if(switchDimensionForTarget(player, dimension)) {
                Style style = HelpfulCommands.style.success;
                src.sendFeedback(()-> Text.translatable("commands.dimension.success.self", Text.literal(dimensionName).setStyle(HelpfulCommands.style.primary)).setStyle(style),true);

                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("commands.dimension.error.alreadyInDimension"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        ArrayList<Entity> list = new ArrayList<>();
        for(Entity i : targets) {
            if(switchDimensionForTarget(i, dimension)) {
                list.add(i);

                if(commandFeedback && i.isPlayer() && i != ((Entity) player)) {
                    Style style = HelpfulCommands.style.tertiary;
                    ((ServerPlayerEntity) i).sendMessage(Text.translatable("commands.dimension.success.self", Text.literal(dimensionName).setStyle(HelpfulCommands.style.primary)).setStyle(style));
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
            src.sendFeedback(() -> Text.translatable("commands.dimension.success.other", Text.literal(dimensionName).setStyle(HelpfulCommands.style.primary), finalCount).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }

    private static boolean switchDimensionForTarget(Entity target, ServerWorld dimension) {
        if(target.getEntityWorld() == dimension) {
            return false;
        }

        BlockPos blockPos = target.getBlockPos();
        return target.teleport(dimension, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new HashSet<>(), target.getYaw(), target.getPitch(), false);
    }
}