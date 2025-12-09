package com.expecticament.helpfulcommands.command.abilities;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CMD_fly implements IHelpfulCommandsCommand {
    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment){
        dispatcher.register(CommandManager.literal(cmd.name)
            .then(CommandManager.argument("target(s)", EntityArgumentType.players())
                    .then(CommandManager.argument("state", BoolArgumentType.bool())
                            .executes(ctx -> execute(ctx, EntityArgumentType.getPlayers(ctx,"target(s)"), BoolArgumentType.getBool(ctx,"state")))
                    )
                    .executes(ctx -> execute(ctx, EntityArgumentType.getPlayers(ctx,"target(s)")))
            )
            .executes(CMD_fly::execute)
            .requires(src -> ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return execute(ctx, null, null);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends ServerPlayerEntity> targets) throws CommandSyntaxException {
        return execute(ctx, targets, null);
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends ServerPlayerEntity> targets, Boolean state) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity plr = src.getPlayer();

        if(targets == null || (targets.size() == 1 && targets.contains(plr))) {
            boolean result = toggleFlying(plr, state);
            if(result) {
                boolean newState = plr.getAbilities().allowFlying;
                Style style = newState ? HelpfulCommands.style.enabled : HelpfulCommands.style.disabled;
                src.sendFeedback(()-> Text.translatable("commands.fly.success.self." + newState).setStyle(style),true);

                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("error.nothingChanged"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        Map<Entity, Boolean> map = new HashMap<>();
        for(ServerPlayerEntity i : targets) {
            if(toggleFlying(i, state)) {
                boolean newState = i.getAbilities().allowFlying;

                map.put(i, newState);

                if(commandFeedback && i != plr) {
                    Style style = newState ? HelpfulCommands.style.enabled : HelpfulCommands.style.disabled;
                    i.sendMessage(Text.translatable("commands.fly.success.self." + newState).setStyle(style));
                }
            }
        }

        int affectedCount = map.size();

        if(affectedCount < 1) {
            src.sendError(Text.translatable("error.didntFindTargets"));
            return 0;
        }

        if(commandFeedback) {
            MutableText finalCount = Text.literal(String.valueOf(affectedCount)).setStyle(HelpfulCommands.style.primary
                    .withHoverEvent(ModCommandManager.targetMapToHoverEvent(map))
            );
            String translationKey = "commands.fly.success.other";
            if(state != null) {
                translationKey+="." + state;
            }
            final String finalTranslationKey = translationKey;
            src.sendFeedback(() -> Text.translatable(finalTranslationKey, finalCount).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }

    private static boolean toggleFlying(ServerPlayerEntity player, Boolean state) {
        PlayerAbilities abilities = player.getAbilities();
        boolean before = abilities.allowFlying;
        abilities.allowFlying = (state != null) ? state : !abilities.allowFlying;

        if(!abilities.allowFlying) {
            abilities.flying = false;
        }

        player.sendAbilitiesUpdate();

        return before != abilities.allowFlying;
    }
}