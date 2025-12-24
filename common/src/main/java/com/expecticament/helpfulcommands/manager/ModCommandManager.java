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

        public CommandData(String name, CommandCategory category, int defaultOpLevel) {
            this(name, category, true, defaultOpLevel);
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

        commands.add(new HcCommand(new CommandData("hc", CommandCategory.MAIN)));

        commands.add(new FlyCommand(new CommandData("fly", CommandCategory.ABILITIES)));
        commands.add(new GodCommand(new CommandData("god", CommandCategory.ABILITIES)));

        commands.add(new InvseeCommand(new CommandData("invsee", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new RenameCommand(new CommandData("rename", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new RepairCommand(new CommandData("repair", CommandCategory.ITEMS_AND_INVENTORY)));
        commands.add(new SmeltCommand(new CommandData("smelt", CommandCategory.ITEMS_AND_INVENTORY)));

        commands.add(new DeathposCommand(new CommandData("deathpos", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new DimensionCommand(new CommandData("dimension", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new HomeCommand(new CommandData("home", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new JumpCommand(new CommandData("jump", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new SpawnCommand(new CommandData("spawn", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new TprCommand(new CommandData("tpr", CommandCategory.MOVEMENT_AND_TELEPORTATION)));
        commands.add(new WarpCommand(new CommandData("warp", CommandCategory.MOVEMENT_AND_TELEPORTATION, 0)));

        commands.add(new CoordsCommand(new CommandData("coords", CommandCategory.PLAYERS_AND_ENTITIES, 0)));
        commands.add(new DmgCommand(new CommandData("dmg", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new ExtinguishCommand(new CommandData("extinguish", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new FeedCommand(new CommandData("feed", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new GmCommand(new CommandData("gm", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new HatCommand(new CommandData("hat", CommandCategory.PLAYERS_AND_ENTITIES, 0)));
        commands.add(new HealCommand(new CommandData("heal", CommandCategory.PLAYERS_AND_ENTITIES)));
        commands.add(new IgniteCommand(new CommandData("ignite", CommandCategory.PLAYERS_AND_ENTITIES)));

        commands.add(new CoinflipCommand(new CommandData("coinflip", CommandCategory.SOCIAL, 0)));

        commands.add(new TimeCommand(new CommandData("day", CommandCategory.WORLD), 1000));
        commands.add(new TimeCommand(new CommandData("night", CommandCategory.WORLD), 13000));
        commands.add(new FireballCommand(new CommandData("fireball", CommandCategory.WORLD, false)));
        commands.add(new KillitemsCommand(new CommandData("killitems", CommandCategory.WORLD)));

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
