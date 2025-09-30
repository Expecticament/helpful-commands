package com.expecticament.helpfulcommands.command.teleportation;

import com.expecticament.helpfulcommands.util.ConfigManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;

import java.util.HashSet;

public class CMD_jump implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd=newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
                        .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(1))
                                .executes(ctx->execute(ctx, DoubleArgumentType.getDouble(ctx,"distance")))
                                .then(CommandManager.argument("checkForBlocks",BoolArgumentType.bool())
                                        .executes(ctx->execute(ctx, DoubleArgumentType.getDouble(ctx, "distance"), BoolArgumentType.getBool(ctx, "checkForBlocks")))
                                )
                        )
                .executes(CMD_jump::execute)
                .requires(src->ModCommandManager.canUseCommand(src,cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return execute(ctx, -1, false);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, double distance) throws CommandSyntaxException {
        return execute(ctx, distance, false);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, double distance, boolean checkForBlocks) throws CommandSyntaxException {
        ServerCommandSource src=ctx.getSource();

        if(!src.isExecutedByPlayer()) {
            src.sendError(Text.translatable("error.inGameOnly"));
            return -1;
        }

        ServerPlayerEntity plr=src.getPlayer();
        double posX, posY, posZ;

        double distanceConfigValue = Double.parseDouble(ConfigManager.loadConfig(src.getServer()).fields.get("jumpDistanceLimit").toString());
        if(distanceConfigValue < 0) distanceConfigValue = 0;

        if(distance<0){
            HitResult hit = getHitResult(plr, plr.getViewDistance() * 24, distanceConfigValue);
            if(hit!=null && hit.getType()==HitResult.Type.BLOCK){
                posX = hit.getPos().getX();
                posY = hit.getPos().getY();
                posZ = hit.getPos().getZ();
            } else{
                return Command.SINGLE_SUCCESS;
            }
        } else{
            if(Double.compare(distanceConfigValue, 0)!=0){
                if(distance > distanceConfigValue){
                    src.sendError(Text.translatable("commands.jump.error.distanceLimitExceeded", Text.literal(String.valueOf(distance)).setStyle(HelpfulCommands.style.primary), Text.literal(String.valueOf(distanceConfigValue)).setStyle(HelpfulCommands.style.primary)));
                    return -1;
                }
            }

            if(checkForBlocks){
                HitResult hit = getHitResult(plr, distance, distanceConfigValue);
                if(hit!=null && hit.getType()==HitResult.Type.BLOCK){
                    posX = hit.getPos().getX();
                    posY = hit.getPos().getY();
                    posZ = hit.getPos().getZ();
                } else{
                    posX = plr.getX() + plr.getRotationVector().getX() * distance;
                    posY = (plr.getY() + plr.getEyeHeight(plr.getPose()) + plr.getRotationVector().getY() * distance) - 1;
                    posZ = plr.getZ() + plr.getRotationVector().getZ() * distance;
                }
            } else{
                posX = plr.getX() + plr.getRotationVector().getX() * distance;
                posY = (plr.getY() + plr.getEyeHeight(plr.getPose()) + plr.getRotationVector().getY() * distance) - 1;
                posZ = plr.getZ() + plr.getRotationVector().getZ() * distance;
            }
        }

        teleport(src,posX,posY,posZ);

        return Command.SINGLE_SUCCESS;
    }

    private static HitResult getHitResult(ServerPlayerEntity plr, double distance, double distanceConfigValue){
        distance = Double.compare(distanceConfigValue, 0)==0 ? distance : Math.clamp(distance, 1.0, distanceConfigValue);
        return plr.raycast(distance, 0, false);
    }

    private static void teleport(ServerCommandSource src, double posX, double posY, double posZ){
        ServerPlayerEntity plr=src.getPlayer();
        if(plr==null) return;

        plr.teleport(plr.getEntityWorld(), posX, posY, posZ, new HashSet<>(),plr.getYaw(), plr.getPitch(), false);
        src.sendFeedback(()->Text.translatable("commands.jump.success",Text.literal(String.format("%.2f",posX)).setStyle(HelpfulCommands.style.primary),Text.literal(String.format("%.2f",posY)).setStyle(HelpfulCommands.style.primary),Text.literal(String.format("%.2f",posZ)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.secondary
                .withClickEvent(new ClickEvent.RunCommand("/tp @s "+posX+" "+posY+" "+posZ))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("tooltips.clickToTeleportToCoordinates")))
        ),true);
    }
}