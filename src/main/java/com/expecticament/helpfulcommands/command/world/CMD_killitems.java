package com.expecticament.helpfulcommands.command.world;

import com.expecticament.helpfulcommands.command.IHelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.ModCommandManager;
import com.expecticament.helpfulcommands.util.ConfigManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CMD_killitems implements IHelpfulCommandsCommand {

    public static ModCommandManager.ModCommand cmd;

    public static void init(ModCommandManager.ModCommand newData){
        cmd = newData;
    }

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(cmd.name)
                .then(CommandManager.argument("range", IntegerArgumentType.integer(0))
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "range")))
                        .then(CommandManager.argument("filter", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "range"), ItemStackArgumentType.getItemStackArgument(ctx, "filter").getItem().getDefaultStack()))
                        )
                )
                .executes(CMD_killitems::execute)
                .requires(src->ModCommandManager.canUseCommand(src, cmd))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return execute(ctx, 0, null);
    }
    private static int execute(CommandContext<ServerCommandSource> ctx, int range) throws CommandSyntaxException {
        return execute(ctx, range, null);
    }
    private static int execute(CommandContext<ServerCommandSource> ctx, int range, @Nullable ItemStack filter) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();

        int rangeConfigValue = (int) Double.parseDouble(ConfigManager.loadConfig(src.getServer()).fields.get("killitemsRangeLimit").toString());
        if(range > rangeConfigValue){
            src.sendError(Text.translatable("commands.killitems.error.rangeLimitExceeded", Text.literal(String.valueOf(range)).setStyle(HelpfulCommands.style.primary), Text.literal(String.valueOf(rangeConfigValue)).setStyle(HelpfulCommands.style.primary)));
            return -1;
        }
        if(range <= 0){
            range = Math.max((rangeConfigValue / 2), 1);
        }

        Map<String, Integer> entries = new HashMap<>();
        if(src.getServer().getPlayerManager().getCurrentPlayerCount() > 0) {
            for (ServerPlayerEntity i : src.getServer().getPlayerManager().getPlayerList()){
                entries.putAll(killItems(src, i.getBoundingBox().expand(range), filter));
            }
        } else{
            entries.putAll(killItems(src, new Box(src.getWorld().getSpawnPoint().getPos()).expand(range), filter));
        }

        int count = 0;

        String entryList = "";
        for(Map.Entry<String, Integer> i : entries.entrySet()){
            entryList += i.getValue() + "x " + i.getKey() + "\n";
            count += i.getValue();
        }
        if(!entryList.isEmpty()){
            entryList = entryList.substring(0, entryList.length() - 1);
        }

        if(count > 0) {
            MutableText finalCount = Text.literal(String.valueOf(count)).setStyle(HelpfulCommands.style.primary
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal(entryList)))
            );
            int finalRange = range;
            src.sendFeedback(() -> Text.translatable("commands.killitems.success", finalCount, Text.literal(String.valueOf(finalRange)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.success), true);
        } else{
            src.sendError(Text.translatable("commands.killitems.error.nothingFound", Text.literal(String.valueOf(range)).setStyle(HelpfulCommands.style.primary)).setStyle(HelpfulCommands.style.error));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static Map<String, Integer> killItems(ServerCommandSource src, Box b, @Nullable ItemStack filter) {
        Map<String, Integer> entries = new HashMap<>();

        ServerWorld world = src.getWorld();

        for(ItemEntity i : world.getEntitiesByType(EntityType.ITEM, b, entity -> (filter == null || entity.getStack().isOf(filter.getItem())))) {
            i.kill(world);
            String name = i.getName().getString();
            entries.put(name, entries.getOrDefault(name,0) + 1);
        }

        return entries;
    }
}