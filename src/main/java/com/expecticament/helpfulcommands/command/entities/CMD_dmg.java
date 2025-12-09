package com.expecticament.helpfulcommands.command.entities;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.rule.GameRules;

import java.util.ArrayList;
import java.util.Collection;

public class CMD_dmg implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(cmd.name)
                .requires(src-> ModCommandManager.canUseCommand(src, cmd))
                .then(CommandManager.argument("target(s)", EntityArgumentType.entities())
                        .then(CommandManager.argument("amount", FloatArgumentType.floatArg(0f))
                                .executes(context -> execute(context, EntityArgumentType.getEntities(context, "target(s)"), FloatArgumentType.getFloat(context, "amount"), context.getSource().getWorld().getDamageSources().generic()))
                                .then(CommandManager.argument("damageType", RegistryEntryReferenceArgumentType.registryEntry(registryAccess, RegistryKeys.DAMAGE_TYPE))
                                        .executes(context -> execute(context, EntityArgumentType.getEntities(context, "target(s)"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(RegistryEntryReferenceArgumentType.getRegistryEntry(context, "damageType", RegistryKeys.DAMAGE_TYPE))))
                                        .then(CommandManager.literal("at")
                                                .then(CommandManager.argument("location", Vec3ArgumentType.vec3())
                                                        .executes(context -> execute(context, EntityArgumentType.getEntities(context, "target(s)"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(RegistryEntryReferenceArgumentType.getRegistryEntry(context, "damageType", RegistryKeys.DAMAGE_TYPE), Vec3ArgumentType.getVec3(context, "location")))))
                                        )
                                        .then(CommandManager.literal("by")
                                                .then(CommandManager.argument("entity", EntityArgumentType.entity())
                                                        .executes(context -> execute(context, EntityArgumentType.getEntities(context, "target(s)"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(RegistryEntryReferenceArgumentType.getRegistryEntry(context, "damageType", RegistryKeys.DAMAGE_TYPE), EntityArgumentType.getEntity(context, "entity"))))
                                                        .then(CommandManager.literal("from")
                                                                .then(CommandManager.argument("cause", EntityArgumentType.entity())
                                                                        .executes(context -> execute(context, EntityArgumentType.getEntities(context, "target(s)"), FloatArgumentType.getFloat(context, "amount"), new DamageSource(RegistryEntryReferenceArgumentType.getRegistryEntry(context, "damageType", RegistryKeys.DAMAGE_TYPE), EntityArgumentType.getEntity(context, "entity"), EntityArgumentType.getEntity(context, "cause"))))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx, Collection<? extends Entity> targets, float amount, DamageSource damageSource) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        if(!src.isExecutedByPlayer() && targets == null){
            src.sendError(Text.translatable("error.specifyTargets"));
            return 0;
        }

        ServerPlayerEntity plr = src.getPlayer();
        ServerWorld world = src.getWorld();

        if(targets == null || (targets.size() == 1 && targets.contains(plr))) {
            if(plr.damage(world, damageSource, amount)) {
                src.sendFeedback(() -> Text.translatable("commands.dmg.success.self", Text.literal(String.valueOf(amount)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
                return Command.SINGLE_SUCCESS;
            } else {
                src.sendError(Text.translatable("commands.dmg.error.invulnerable.self"));
                return 0;
            }
        }

        boolean commandFeedback = src.getWorld().getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        ArrayList<Entity> list = new ArrayList<>();
        for(Entity i : targets) {
            if (i.damage(world, damageSource, amount)) {
                list.add(i);
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
            src.sendFeedback(() -> Text.translatable("commands.dmg.success.other", Text.literal(String.valueOf(amount)).setStyle(HelpfulCommands.style.primary), finalCount).setStyle(HelpfulCommands.style.success), true);
        }

        return affectedCount;
    }
}
