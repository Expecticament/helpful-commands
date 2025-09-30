package com.expecticament.helpfulcommands.command.teleportation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashSet;

public class CMD_spawn implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    private enum SpawnType { Player, World };

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.literal("get")
                                                .executes(ctx-> get(ctx,SpawnType.Player, EntityArgumentType.getPlayer(ctx, "target")))
                                        )
                                        .then(CommandManager.literal("tp")
                                                .executes(ctx -> teleport(ctx, SpawnType.Player, EntityArgumentType.getPlayer(ctx, "target")))
                                        )
                                        .executes(ctx -> teleport(ctx, SpawnType.Player, EntityArgumentType.getPlayer(ctx, "target")))
                                )
                                .executes(ctx -> teleport(ctx, SpawnType.Player, null))
                        )
                        .then(CommandManager.literal("world")
                                .then(CommandManager.literal("get")
                                        .executes(ctx-> get(ctx,SpawnType.World, null))
                                )
                                .then(CommandManager.literal("tp")
                                        .executes(ctx -> teleport(ctx, SpawnType.World, null))
                                )
                                .executes(ctx -> teleport(ctx, SpawnType.World, null))
                        )
                .requires(src-> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int teleport(CommandContext<ServerCommandSource> ctx, SpawnType spawnType, @Nullable ServerPlayerEntity targetPlayer) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        if(!src.isExecutedByPlayer()) {
            src.sendError(Text.translatable("error.inGameOnly"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();
        if(targetPlayer == null) {
            targetPlayer = player;
        }

        String plrTrKey = ".";
        if(spawnType == SpawnType.Player) {
            plrTrKey = (targetPlayer == player) ? ".self." : ".other.";
        }

        TeleportationCommands.TeleportationPosition tpos = getTeleportationPosition(targetPlayer, spawnType);

        if(tpos.pos() == null) {
            src.sendError(Text.translatable(String.format("commands.spawn.%s%serror.notSet", spawnType.name().toLowerCase(), plrTrKey), Text.literal(targetPlayer.getName().getString()).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.error));
            return 0;
        }

        if(tpos.world() == null){
            src.sendError(Text.translatable("error.unknownDimension"));
            return 0;
        }

        player.teleport(tpos.world(), tpos.pos().getX(), tpos.pos().getY(), tpos.pos().getZ(), new HashSet<>(), player.getYaw(), player.getPitch(), false);
        final ServerPlayerEntity finalTarget = targetPlayer;
        final String finalPlrTrKey = plrTrKey;
        src.sendFeedback(()-> Text.translatable(String.format("commands.spawn.%s%ssuccess", spawnType.name().toLowerCase(), finalPlrTrKey), Text.literal(finalTarget.getName().getString()).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.secondary
                .withClickEvent(new ClickEvent.SuggestCommand("/tp " + tpos.pos().getX() + " " + tpos.pos().getY() + " " + tpos.pos().getZ()))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("x: " + tpos.pos().getX() + "\ny: " + tpos.pos().getY() + "\nz: " + tpos.pos().getZ())))
        ),true);

        return Command.SINGLE_SUCCESS;
    }

    private static int get(CommandContext<ServerCommandSource> ctx, SpawnType spawnType, @Nullable ServerPlayerEntity targetPlayer) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();

        if(targetPlayer == null) {
            targetPlayer = player;
        }

        String plrTrKey = ".";
        if(spawnType == SpawnType.Player) {
            plrTrKey = (targetPlayer == player) ? ".self." : ".other.";
        }

        TeleportationCommands.TeleportationPosition tpos = getTeleportationPosition(targetPlayer, spawnType);

        if(tpos.pos() == null) {
            src.sendError(Text.translatable(String.format("commands.spawn.%s%serror.notSet", spawnType.name().toLowerCase(), plrTrKey), Text.literal(targetPlayer.getName().getString()).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.error));
            return 0;
        }

        if(tpos.world() == null){
            src.sendError(Text.translatable("error.unknownDimension"));
            return 0;
        }

        Style valueStyle = HelpfulCommands.style.tertiary;
        MutableText targetName = switch (spawnType) {
            case Player -> Text.literal(targetPlayer.getName().getString());
            case World -> Text.translatable("phrase.world");
        };
        String runCmd = "/spawn " + spawnType.name().toLowerCase();
        if(spawnType == SpawnType.Player) {
            if(targetPlayer != player) {
                runCmd += " " + targetPlayer.getName().getString();
            }
        }

        MutableText msg = Text.empty().setStyle(HelpfulCommands.style.primary);
        msg
                .append(Text.literal("« ").formatted(Formatting.BOLD))
                .append(targetName.formatted(Formatting.BOLD))
                .append(Text.literal(" || ").setStyle(HelpfulCommands.style.secondary))
                .append(Text.translatable("spawnPoint.title").setStyle(HelpfulCommands.style.secondary))
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
                .append(Text.literal(tpos.world().getRegistryKey().getValue().toString()).setStyle(valueStyle))
                .append("\n")
                .append("\n")
                .append(Text.literal("〚").setStyle(HelpfulCommands.style.secondary))
                .append(Text.translatable("phrase.teleport").setStyle(HelpfulCommands.style.secondary
                        .withClickEvent(new ClickEvent.RunCommand(runCmd))
                        .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToTeleport")))
                ))
                .append(Text.literal("〛").setStyle(HelpfulCommands.style.secondary))
        ;

        src.sendMessage(msg);

        return Command.SINGLE_SUCCESS;
    }

    private static TeleportationCommands.TeleportationPosition getTeleportationPosition(ServerPlayerEntity player, SpawnType spawnType) {
        BlockPos pos = null;
        ServerWorld world = null;

        MinecraftServer server = player.getEntityWorld().getServer();

        switch(spawnType) {
            case Player:
                ServerPlayerEntity.Respawn respawn = player.getRespawn();
                if(respawn != null) {
                    world = regKeyToWorld(server, respawn.respawnData().getDimension());
                    pos = respawn.respawnData().getPos();
                }
                break;
            case World:
                WorldProperties.SpawnPoint spawnPoint = player.getEntityWorld().toServerWorld().getSpawnPoint();
                pos = spawnPoint.getPos();
                world = regKeyToWorld(server, spawnPoint.getDimension());
                break;
        }

        return new TeleportationCommands.TeleportationPosition(world, pos);
    }

    private static ServerWorld regKeyToWorld(MinecraftServer server, RegistryKey<World> registryKey) {
        String worldKey = registryKey.getValue().toString();

        for (ServerWorld world : server.getWorlds()) {
            if (worldKey.equals(world.getRegistryKey().getValue().toString())) {
                return world;
            }
        }

        return null;
    }
}