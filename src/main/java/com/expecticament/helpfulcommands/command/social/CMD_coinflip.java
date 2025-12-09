package com.expecticament.helpfulcommands.command.social;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class CMD_coinflip implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.literal("heads")
                        .executes(ctx -> execute(ctx, 0))
                )
                .then(CommandManager.literal("tails")
                        .executes(ctx -> execute(ctx, 1))
                )
                .requires(src-> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, int choice) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer()){
            src.sendError(Text.translatable("error.inGameOnly"));
            return -1;
        }

        Random random = new Random();
        int result = random.nextBoolean() ? 0 : 1;
        boolean won = (result == choice);
        ServerPlayerEntity plr = src.getPlayer();

        plr.sendMessage(Text.translatable("commands.coinflip.output.self", Text.translatable((result == 0) ? "commands.coinflip.heads" : "commands.coinflip.tails").setStyle(HelpfulCommands.style.primary), Text.translatable(won ? "commands.coinflip.won" : "commands.coinflip.lost").setStyle(won ? HelpfulCommands.style.enabled : HelpfulCommands.style.disabled)));

        Vec3d vec3 = plr.getEntityPos();
        RegistryEntry<SoundEvent> registryEntry = RegistryEntry.of(SoundEvent.of((won ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_WANDERING_TRADER_NO).id()));
        plr.networkHandler.sendPacket(new PlaySoundS2CPacket(registryEntry, SoundCategory.PLAYERS, vec3.getX(), vec3.getY(), vec3.getZ(), 1, 1, plr.getEntityWorld().getRandom().nextLong()));

        for(ServerPlayerEntity i : src.getServer().getPlayerManager().getPlayerList()) {
            if(i != plr) {
                i.sendMessage(Text.translatable("commands.coinflip.output", Text.literal(src.getDisplayName().getString()).setStyle(HelpfulCommands.style.primary), Text.translatable((result == 0) ? "commands.coinflip.heads" : "commands.coinflip.tails").setStyle(HelpfulCommands.style.primary), Text.translatable(won ? "commands.coinflip.won" : "commands.coinflip.lost").setStyle(won ? HelpfulCommands.style.enabled : HelpfulCommands.style.disabled)));
            }
        }

        return Command.SINGLE_SUCCESS;
    }
}