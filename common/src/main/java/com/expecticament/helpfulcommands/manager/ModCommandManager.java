package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.command.main.*;
import com.expecticament.helpfulcommands.command.abilities.*;
import com.expecticament.helpfulcommands.command.itemsAndInventory.*;
import com.expecticament.helpfulcommands.command.movementAndTeleportation.*;
import com.expecticament.helpfulcommands.command.playersAndEntities.*;
import com.expecticament.helpfulcommands.command.social.*;
import com.expecticament.helpfulcommands.command.world.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ModCommandManager {
    public enum CommandCategory { MAIN, ABILITIES, ITEMS_AND_INVENTORY, MOVEMENT_AND_TELEPORTATION, PLAYERS_AND_ENTITIES, WORLD, SOCIAL, UNCATEGORIZED }

    private static List<HelpfulCommandsCommand> commands;
    private static LinkedHashMap<CommandCategory, List<HelpfulCommandsCommand>> commandsByCategory;

    public static class CommandData {
        private final String name;
        private final CommandCategory category;
        private final boolean defaultState;
        private final int defaultOpLevel;

        public CommandData(String name, CommandCategory category) {
            this(name, category, true, 2);
        }

        public CommandData(String name, CommandCategory category, boolean defaultState) {
            this(name, category, defaultState, 2);
        }

        public CommandData(String name, CommandCategory category, boolean defaultState, int defaultOpLevel) {
            this.name = name;
            this.category = category;
            this.defaultState = defaultState;
            this.defaultOpLevel = defaultOpLevel;
        }

        public String getName() {
            return name;
        }

        public CommandCategory getCategory() {
            return category;
        }

        public boolean isEnabledByDefault() {
            return defaultState;
        }

        public int getDefaultOpLevel() { return defaultOpLevel; }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection) {
        commands = new ArrayList<>();
        commandsByCategory = new LinkedHashMap<>();

        commands.add(new CMD_hc(new CommandData("hc", CommandCategory.MAIN)));

        commands.add(new CMD_fly(new CommandData("fly", CommandCategory.ABILITIES)));
        commands.add(new CMD_god(new CommandData("god", CommandCategory.ABILITIES)));

        commands.add(new CMD_invsee(new CommandData("invsee", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new CMD_rename(new CommandData("rename", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new CMD_repair(new CommandData("repair", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new CMD_smelt(new CommandData("smelt", CommandCategory.ITEMS_AND_INVENTORY)));

        commands.add(new CMD_deathpos(new CommandData("deathpos", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new CMD_dimension(new CommandData("dimension", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new CMD_home(new CommandData("home", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new CMD_spawn(new CommandData("spawn", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new CMD_warp(new CommandData("warp", CommandCategory.MOVEMENT_AND_TELEPORTATION, true, 0)));

        commands.add(new CMD_coords(new CommandData("coords", CommandCategory.PLAYERS_AND_ENTITIES, true, 0)));
        commands.add(new CMD_extinguish(new CommandData("extinguish", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new CMD_feed(new CommandData("feed", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new CMD_gm(new CommandData("gm", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new CMD_hat(new CommandData("hat", CommandCategory.PLAYERS_AND_ENTITIES, true, 0)));
        commands.add(new CMD_heal(new CommandData("heal", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new CMD_ignite(new CommandData("ignite", CommandCategory.PLAYERS_AND_ENTITIES)));

        commands.add(new CMD_coinflip(new CommandData("coinflip", CommandCategory.SOCIAL, true, 0)));

        commands.add(new TimeCommand(new CommandData("day", CommandCategory.WORLD), 1000));
        commands.add(new TimeCommand(new CommandData("night", CommandCategory.WORLD), 13000));
        commands.add(new CMD_killitems(new CommandData("killitems", CommandCategory.WORLD)));

        for (HelpfulCommandsCommand command : commands) {
            command.register(dispatcher, buildContext, selection);

            CommandCategory category = command.getCommandData().getCategory();
            commandsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(command);
        }
    }

    public static List<HelpfulCommandsCommand> getCommandList() {
        return commands;
    }

    public static LinkedHashMap<CommandCategory, List<HelpfulCommandsCommand>> getCommandListByCategory() {
        return commandsByCategory;
    }

    public static void sendCommandsToEveryone(CommandSourceStack src){
        MinecraftServer server = src.getServer();
        for(ServerPlayer i : server.getPlayerList().getPlayers()){
            server.getCommands().sendCommands(i);
        }
    }
}
