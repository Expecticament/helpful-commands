package com.expecticament.helpful_commands.manager;

import com.expecticament.helpful_commands.command.HelpfulCommandsCommand;
import com.expecticament.helpful_commands.command.abilities.FlyCommand;
import com.expecticament.helpful_commands.command.abilities.GodCommand;
import com.expecticament.helpful_commands.command.itemsAndInventory.*;
import com.expecticament.helpful_commands.command.main.HcCommand;
import com.expecticament.helpful_commands.command.movementAndTeleportation.*;
import com.expecticament.helpful_commands.command.playersAndEntities.*;
import com.expecticament.helpful_commands.command.social.CoinflipCommand;
import com.expecticament.helpful_commands.command.world.*;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ModCommandManager {
    public enum ModCommand {
        HC(CommandCategory.MAIN),

        FLY(CommandCategory.ABILITIES),
        GOD(CommandCategory.ABILITIES),

        DISENCHANT(CommandCategory.ITEMS_AND_INVENTORY),
        ECHEST(CommandCategory.ITEMS_AND_INVENTORY),
        INVSEE(CommandCategory.ITEMS_AND_INVENTORY),
        RENAME(CommandCategory.ITEMS_AND_INVENTORY),
        REPAIR(CommandCategory.ITEMS_AND_INVENTORY),
        SMELT(CommandCategory.ITEMS_AND_INVENTORY),

        DEATHPOS(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        DIMENSION(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        HOME(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        JUMP(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        SPAWN(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        TPR(CommandCategory.MOVEMENT_AND_TELEPORTATION),
        WARP(CommandCategory.MOVEMENT_AND_TELEPORTATION),

        COORDS(CommandCategory.PLAYERS_AND_ENTITIES),
        DMG(CommandCategory.PLAYERS_AND_ENTITIES),
        EXTINGUISH(CommandCategory.PLAYERS_AND_ENTITIES),
        FEED(CommandCategory.PLAYERS_AND_ENTITIES),
        GM(CommandCategory.PLAYERS_AND_ENTITIES),
        HAT(CommandCategory.PLAYERS_AND_ENTITIES),
        HEAL(CommandCategory.PLAYERS_AND_ENTITIES),
        IGNITE(CommandCategory.PLAYERS_AND_ENTITIES),

        COINFLIP(CommandCategory.SOCIAL),

        EXPLOSION(CommandCategory.WORLD, false),
        FIREBALL(CommandCategory.WORLD, false),
        KILLITEMS(CommandCategory.WORLD),
        LIGHTNING(CommandCategory.WORLD),
        DAY(CommandCategory.WORLD),
        NIGHT(CommandCategory.WORLD);

        private final String name;
        private final CommandCategory category;
        private final boolean defaultState;

        ModCommand(CommandCategory category) {
            this(category, true);
        }

        ModCommand(CommandCategory category, boolean defaultState) {
            this.name = this.name().toLowerCase();
            this.category = category;
            this.defaultState = defaultState;
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
    }

    public enum CommandCategory { MAIN, ABILITIES, ITEMS_AND_INVENTORY, MOVEMENT_AND_TELEPORTATION, PLAYERS_AND_ENTITIES, WORLD, SOCIAL, UNCATEGORIZED }

    private static List<HelpfulCommandsCommand> commands;
    private static LinkedHashMap<CommandCategory, List<HelpfulCommandsCommand>> commandsByCategory;

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection) {
        commands = new ArrayList<>();
        commandsByCategory = new LinkedHashMap<>();

        commands.add(new HcCommand(ModCommand.HC));

        commands.add(new FlyCommand(ModCommand.FLY));
        commands.add(new GodCommand(ModCommand.GOD));

        commands.add(new DisenchantCommand(ModCommand.DISENCHANT));
        commands.add(new EchestCommand(ModCommand.ECHEST));
        commands.add(new InvseeCommand(ModCommand.INVSEE));
        commands.add(new RenameCommand(ModCommand.RENAME));
        commands.add(new RepairCommand(ModCommand.REPAIR));
        commands.add(new SmeltCommand(ModCommand.SMELT));

        commands.add(new DeathposCommand(ModCommand.DEATHPOS));
        commands.add(new DimensionCommand(ModCommand.DIMENSION));
        commands.add(new HomeCommand(ModCommand.HOME));
        commands.add(new JumpCommand(ModCommand.JUMP));
        commands.add(new SpawnCommand(ModCommand.SPAWN));
        commands.add(new TprCommand(ModCommand.TPR));
        commands.add(new WarpCommand(ModCommand.WARP));

        commands.add(new CoordsCommand(ModCommand.COORDS));
        commands.add(new DmgCommand(ModCommand.DMG));
        commands.add(new ExtinguishCommand(ModCommand.EXTINGUISH));
        commands.add(new FeedCommand(ModCommand.FEED));
        commands.add(new GmCommand(ModCommand.GM));
        commands.add(new HatCommand(ModCommand.HAT));
        commands.add(new HealCommand(ModCommand.HEAL));
        commands.add(new IgniteCommand(ModCommand.IGNITE));

        commands.add(new CoinflipCommand(ModCommand.COINFLIP));

        commands.add(new TimeCommand(ModCommand.DAY, 1000, ModPermissions.Permission.COMMAND_DAY));
        commands.add(new TimeCommand(ModCommand.NIGHT, 13000, ModPermissions.Permission.COMMAND_NIGHT));
        commands.add(new ExplosionCommand(ModCommand.EXPLOSION));
        commands.add(new FireballCommand(ModCommand.FIREBALL));
        commands.add(new KillitemsCommand(ModCommand.KILLITEMS));
        commands.add(new LightningCommand(ModCommand.LIGHTNING));

        for (HelpfulCommandsCommand command : commands) {
            command.register(dispatcher, buildContext, selection);

            CommandCategory category = command.getModCommand().getCategory();
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
