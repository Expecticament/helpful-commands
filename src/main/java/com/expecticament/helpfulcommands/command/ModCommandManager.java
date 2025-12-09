package com.expecticament.helpfulcommands.command;

import com.expecticament.helpfulcommands.command.main.*;
import com.expecticament.helpfulcommands.command.abilities.*;
import com.expecticament.helpfulcommands.command.entities.*;
import com.expecticament.helpfulcommands.command.social.CMD_coinflip;
import com.expecticament.helpfulcommands.command.teleportation.*;
import com.expecticament.helpfulcommands.command.time.*;
import com.expecticament.helpfulcommands.command.utility.*;
import com.expecticament.helpfulcommands.command.world.*;
import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.util.ConfigManager;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.*;

public class ModCommandManager{

    public enum ModCommandCategory { Main, Abilities, Entities, Teleportation, Time, World, Utility , Social, Uncategorized }
    public static Map<ModCommandCategory, LinkedList<ModCommand>> commandListByCategory = new LinkedHashMap<>();

    public static class ModCommand {
        public String name = "command";
        public ModCommandCategory category = ModCommandCategory.Uncategorized; // Commands in the "Main" category are ALWAYS available to everyone
        public boolean defaultState = true; // Whether the command is enabled by default
        public boolean defaultPublic = false; // Public commands can be used by anyone, regardless of their permissions or OP level
        Runnable register;
    }
    public static final List<ModCommand> commands = new ArrayList<>(){{
        add(new ModCommand(){{
            name = "hc";
            category = ModCommandCategory.Main;
            defaultPublic = true;
            register = ()-> { CMD_hc.init(this); CommandRegistrationCallback.EVENT.register(CMD_hc::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "fly";
            category = ModCommandCategory.Abilities;
            register = ()-> { CMD_fly.init(this); CommandRegistrationCallback.EVENT.register(CMD_fly::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "god";
            category = ModCommandCategory.Abilities;
            register = ()-> { CMD_god.init(this); CommandRegistrationCallback.EVENT.register(CMD_god::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "dmg";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_dmg.init(this); CommandRegistrationCallback.EVENT.register(CMD_dmg::registerCommand); };
            defaultState = false;
        }});
        add(new ModCommand(){{
            name = "extinguish";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_extinguish.init(this); CommandRegistrationCallback.EVENT.register(CMD_extinguish::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "feed";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_feed.init(this); CommandRegistrationCallback.EVENT.register(CMD_feed::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "gm";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_gm.init(this); CommandRegistrationCallback.EVENT.register(CMD_gm::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "hat";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_hat.init(this); CommandRegistrationCallback.EVENT.register(CMD_hat::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "heal";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_heal.init(this); CommandRegistrationCallback.EVENT.register(CMD_heal::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "ignite";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_ignite.init(this); CommandRegistrationCallback.EVENT.register(CMD_ignite::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "invsee";
            category = ModCommandCategory.Entities;
            register = ()-> { CMD_invsee.init(this); CommandRegistrationCallback.EVENT.register(CMD_invsee::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "back";
            category = ModCommandCategory.Teleportation;
            register = ()-> { CMD_back.init(this); CommandRegistrationCallback.EVENT.register(CMD_back::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "dimension";
            category = ModCommandCategory.Teleportation;
            register = ()-> { CMD_dimension.init(this); CommandRegistrationCallback.EVENT.register(CMD_dimension::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "home";
            category = ModCommandCategory.Teleportation;
            register = ()-> { CMD_home.init(this); CommandRegistrationCallback.EVENT.register(CMD_home::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "jump";
            category = ModCommandCategory.Teleportation;
            register = ()-> { CMD_jump.init(this); CommandRegistrationCallback.EVENT.register(CMD_jump::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "spawn";
            category = ModCommandCategory.Teleportation;
            register = ()-> { CMD_spawn.init(this); CommandRegistrationCallback.EVENT.register(CMD_spawn::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "day";
            category = ModCommandCategory.Time;
            register = ()-> { CMD_day.init(this); CommandRegistrationCallback.EVENT.register(CMD_day::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "night";
            category = ModCommandCategory.Time;
            register = ()-> { CMD_night.init(this); CommandRegistrationCallback.EVENT.register(CMD_night::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "explosion";
            category = ModCommandCategory.World;
            defaultState = false;
            register = ()-> { CMD_explosion.init(this); CommandRegistrationCallback.EVENT.register(CMD_explosion::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "killitems";
            category = ModCommandCategory.World;
            register = ()-> { CMD_killitems.init(this); CommandRegistrationCallback.EVENT.register(CMD_killitems::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "lightning";
            category = ModCommandCategory.World;
            register = ()-> { CMD_lightning.init(this); CommandRegistrationCallback.EVENT.register(CMD_lightning::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "rename";
            category = ModCommandCategory.Utility;
            register = ()-> { CMD_rename.init(this); CommandRegistrationCallback.EVENT.register(CMD_rename::registerCommand); };
        }});
        add(new ModCommand(){{
            name = "repair";
            category = ModCommandCategory.Utility;
            register = ()-> { CMD_repair.init(this); CommandRegistrationCallback.EVENT.register(CMD_repair::registerCommand); };
        }});

        add(new ModCommand(){{
            name = "coinflip";
            category = ModCommandCategory.Social;
            defaultPublic = true;
            register = ()-> { CMD_coinflip.init(this); CommandRegistrationCallback.EVENT.register(CMD_coinflip::registerCommand); };
        }});
    }};

    public static void registerCommands() {
        for(ModCommandCategory i : ModCommandCategory.values()){
            commandListByCategory.put(i, new LinkedList<>());
        }
        for(ModCommand i : commands){
            i.register.run();
            LinkedList<ModCommand> list = commandListByCategory.get(i.category);
            list.add(i);
            commandListByCategory.put(i.category, list);
        }

        HelpfulCommands.LOGGER.info("");
        HelpfulCommands.LOGGER.info("H   H  CCCCC  | ");
        HelpfulCommands.LOGGER.info("H   H  C      |  Thank you for using");
        HelpfulCommands.LOGGER.info("HHHHH  C      |  " + HelpfulCommands.modName);
        HelpfulCommands.LOGGER.info("H   H  C      |  " + HelpfulCommands.modVersion);
        HelpfulCommands.LOGGER.info("H   H  CCCCC  | ");
        HelpfulCommands.LOGGER.info("<- Finished command registration ->");
        int count = 0;
        for(Map.Entry<ModCommandCategory,LinkedList<ModCommand>> i : commandListByCategory.entrySet()){
            String character = "|-";
            if(count == 0){
                character = "/-";
            }
            if(count == commandListByCategory.size() - 1){
                character = "\\-";
            }

            String str = character + i.getKey().name() + ": ";
            for(ModCommand j : i.getValue()){
                str += "/" + j.name + ", ";
            }
            if(i.getValue().isEmpty()){
                str += "(none)";
            } else{
                str = str.substring(0, str.length() - 2);
            }
            HelpfulCommands.LOGGER.info(str);
            ++count;
        }

        HelpfulCommands.LOGGER.info("");
    }

    public static Boolean canUseCommand(ServerCommandSource src, ModCommand cmd){
        return getCantUseCommandReason(src, cmd) == null;
    }
    public static MutableText getCantUseCommandReason(ServerCommandSource src, ModCommand cmd){
        MinecraftServer server = src.getServer();
        if (server == null) {
            return Text.empty();
        }
        Map<String, ConfigManager.ModConfigCommandEntry> cmdProperties = ConfigManager.loadConfig(server).commands;

        if(Permissions.check(src,HelpfulCommands.modID + ".command." + cmd.category.toString().toLowerCase() + "." + cmd.name, HelpfulCommands.defaultCommandLevel)){
            // LuckPerms will not detect mod's command permissions without this empty check. I don't know any better workaround :)
        }
        // Same goes for the next 2 empty checks. Under certain circumstances LuckPerms won't register ".config.*" without this (manageCommand should actually be fine because of the real check below, but better be safe than sorry)
        if(Permissions.check(src,HelpfulCommands.modID + ".config.manageCommand")){}
        if(Permissions.check(src,HelpfulCommands.modID + ".config.manageField")){}

        if(cmd.category != ModCommandCategory.Main && !Permissions.check(src,HelpfulCommands.modID + ".config.manageCommand")){ // Anyone can always use commands from the Main category. Players who have permissions to manage commands should also be able to use all of them.
            if(!cmdProperties.get(cmd.name).isPublic){
                // Command is not public, check player's permissions
                if(!Permissions.check(src, HelpfulCommands.modID + ".command." + cmd.category.toString().toLowerCase() + "." + cmd.name, HelpfulCommands.defaultCommandLevel)) {
                    return Text.translatable("error.notAllowedToUseCommand", Text.literal("/" + cmd.name).setStyle(HelpfulCommands.style.tertiary)).setStyle(HelpfulCommands.style.error);
                }
            }
        }

        // Check if command is disabled
        if(cmd.category != ModCommandCategory.Main){
            if(!cmdProperties.get(cmd.name).isEnabled){
                return Text.translatable("error.commandDisabled", Text.literal("/" + cmd.name).setStyle(HelpfulCommands.style.tertiary)).setStyle(HelpfulCommands.style.error);
            }
        }

        return null;
    }

    public static void sendCommandTreeToEveryone(ServerCommandSource src){
        MinecraftServer server = src.getServer();
        for(ServerPlayerEntity i : server.getPlayerManager().getPlayerList()){
            server.getCommandManager().sendCommandTree(i);
        }
    }

    public static HoverEvent targetMapToHoverEvent(Map<Entity, Boolean> map) {
        MutableText text = Text.empty();

        int count = 0;
        for(Map.Entry<Entity, Boolean> i : map.entrySet()) {
            String name = i.getKey().getDisplayName().getString();
            Style entryStyle = i.getValue() ? HelpfulCommands.style.enabled : HelpfulCommands.style.disabled;

            text.append(Text.literal(name).setStyle(entryStyle));

            if(count != map.size() - 1) {
                text.append("\n");
            }

            count++;
        }

        return new HoverEvent.ShowText(text);

    }

    public static HoverEvent targetListToHoverEvent(List<Entity> list) {
        MutableText text = Text.empty();

        int count = 0;
        for(Entity i : list) {
            String name = i.getDisplayName().getString();
            Style entryStyle = HelpfulCommands.style.simpleText;

            text.append(Text.literal(name).setStyle(entryStyle));

            if(count != list.size() - 1) {
                text.append("\n");
            }

            count++;
        }

        return new HoverEvent.ShowText(text);
    }
}