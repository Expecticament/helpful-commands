package com.expecticament.helpfulcommands.command.teleportation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.expecticament.helpfulcommands.util.IEntityDataSaver;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;

public class CMD_home implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
            .then(CommandManager.literal("set")
                    .executes(CMD_home::set)
            )
            .then(CommandManager.literal("get")
                    .executes(CMD_home::get)
            )
            .then(CommandManager.literal("tp")
                    .executes(CMD_home::teleport)
            )
            .executes(CMD_home::teleport)
            .requires(src->ModCommandManager.canUseCommand(src,cmd))
        );
    }

    private static int teleport(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        if(!src.isExecutedByPlayer()){
            src.sendError(Text.translatable("error.inGameOnly"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();
        TeleportationCommands.TeleportationPosition tpos = getTeleportationPosition(src, player);

        if(tpos.pos() != null) {
            if(tpos.world() == null){
                src.sendError(Text.translatable("error.unknownDimension"));
                return 0;
            }

            player.teleport(tpos.world(), tpos.pos().getX(), tpos.pos().getY(), tpos.pos().getZ(), new HashSet<>(), player.getYaw(), player.getPitch(), false);
            src.sendFeedback(()-> Text.translatable("commands.home.teleport.success").setStyle(HelpfulCommands.style.secondary
                    .withClickEvent(new ClickEvent.SuggestCommand("/tp " + tpos.pos().getX() + " " + tpos.pos().getY() + " " + tpos.pos().getZ()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("x: " + tpos.pos().getX() + "\ny: " + tpos.pos().getY() + "\nz: " + tpos.pos().getZ())))
            ),true);
        } else{
            src.sendError(Text.translatable("commands.home.error.noHomePos", Text.literal("/home set").setStyle(HelpfulCommands.style.primary
                    .withClickEvent(new ClickEvent.SuggestCommand("/home set"))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToSuggestThisCommand")))
            )));

            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        if(!src.isExecutedByPlayer()){
            src.sendError(Text.translatable("error.inGameOnly"));
            return 0;
        }

        ServerPlayerEntity plr = ctx.getSource().getPlayer();
        IEntityDataSaver iEntityDataSaver = ((IEntityDataSaver) plr);

        BlockPos pos = plr.getBlockPos();
        RegistryKey<World> dimensionKey = plr.getEntityWorld().getRegistryKey();
        String dimensionName = dimensionKey.getValue().toString();
        iEntityDataSaver.getPersistentData().putIntArray("homePosition", new int[]{ pos.getX(), pos.getY(), pos.getZ() });
        iEntityDataSaver.getPersistentData().putString("homeDimension", dimensionName);

        src.sendFeedback(()-> Text.translatable("commands.home.set.success").setStyle(HelpfulCommands.style.success),true);

        return Command.SINGLE_SUCCESS;
    }

    private static int get(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        if(!src.isExecutedByPlayer()){
            src.sendError(Text.translatable("error.inGameOnly"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();
        TeleportationCommands.TeleportationPosition tpos = getTeleportationPosition(src, player);

        if(tpos.pos() != null) {
            if(tpos.world() == null){
                src.sendError(Text.translatable("error.unknownDimension"));
                return 0;
            }
        } else{
            src.sendError(Text.translatable("commands.home.error.noHomePos", Text.literal("/home set").setStyle(HelpfulCommands.style.primary
                    .withClickEvent(new ClickEvent.SuggestCommand("/home set"))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToSuggestThisCommand")))
            )));

            return 0;
        }

        String dimensionName = tpos.world().getRegistryKey().getValue().toString();
        Style valueStyle = HelpfulCommands.style.tertiary;

        MutableText msg = Text.empty().setStyle(HelpfulCommands.style.primary);
        msg
                .append(Text.literal("« ").formatted(Formatting.BOLD))
                .append(Text.literal(src.getName()).formatted(Formatting.BOLD))
                .append(Text.literal(" || ").setStyle(HelpfulCommands.style.secondary))
                .append(Text.translatable("homePosition.title").setStyle(HelpfulCommands.style.secondary))
                .append(Text.literal(" »").formatted(Formatting.BOLD))
                .append("\n")
                .append(Text.literal("x: ").setStyle(HelpfulCommands.style.simpleText))
                .append(Text.literal(String.valueOf(tpos.pos().getX())).setStyle(valueStyle))
                .append("\n")
                .append(Text.literal("y: ").setStyle(HelpfulCommands.style.simpleText))
                .append(Text.literal(String.valueOf(tpos.pos().getY())).setStyle(valueStyle))
                .append("\n")
                .append(Text.literal("z: ").setStyle(HelpfulCommands.style.simpleText))
                .append(Text.literal(String.valueOf(tpos.pos().getZ())).setStyle(valueStyle))
                .append("\n")
                .append(Text.translatable("phrase.dimension").append(Text.literal(": ")).setStyle(HelpfulCommands.style.simpleText))
                .append(Text.literal(dimensionName).setStyle(valueStyle))
                .append("\n")
                .append("\n")
                .append(Text.literal("〚").setStyle(HelpfulCommands.style.secondary))
                .append(Text.translatable("phrase.teleport").setStyle(HelpfulCommands.style.secondary
                        .withClickEvent(new ClickEvent.RunCommand("/home"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToTeleport")))
                ))
                .append(Text.literal("〛").setStyle(HelpfulCommands.style.secondary))
        ;

        src.sendMessage(msg);

        return Command.SINGLE_SUCCESS;
    }

    private static TeleportationCommands.TeleportationPosition getTeleportationPosition(ServerCommandSource src, ServerPlayerEntity player) {
        IEntityDataSaver playerData = (IEntityDataSaver) player;

        int[] pos = (playerData.getPersistentData().getIntArray("homePosition")).orElse(null);
        String dimensionName = (playerData.getPersistentData().getString("homeDimension")).orElse("");

        ServerWorld dimension = null;
        BlockPos blockPos = null;

        if(pos != null && pos.length >= 3) {
            blockPos = new BlockPos(pos[0], pos[1], pos[2]);
        }

        for(RegistryKey<World> i : src.getWorldKeys()){
            if(i.getValue().toString().equals(dimensionName)){
                dimension = src.getServer().getWorld(i);
                break;
            }
        }

        return new TeleportationCommands.TeleportationPosition(dimension, blockPos);
    }
}