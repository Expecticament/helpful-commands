package com.expecticament.helpfulcommands.helper;

import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;

public class PermissionHelper {
    public static boolean hasPermission(CommandSourceStack source, String permission, int defaultOpLevel) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(defaultOpLevel)));
            }

            return net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class).getPermissionData(player).checkPermission(permission).asBoolean();
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(defaultOpLevel)));
        }
    }

    public static boolean canConfigure(CommandSourceStack source) {
        return canConfigure(source, "");
    }

    public static boolean canConfigure(CommandSourceStack source, String configPermission) {
        int configOpLevel = 3;

        ServerPlayer player = source.getPlayer();

        if (!HelpfulCommands.isDedicatedServer() && player != null) {
            return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(configOpLevel))) && source.getServer().isSingleplayerOwner(new NameAndId(player.getGameProfile()));
        }

        if (configPermission.isEmpty()) {
            return hasPermission(source, "helpful_commands.config", configOpLevel) || hasPermission(source, "helpful_commands.config.command", configOpLevel) || hasPermission(source, "helpful_commands.config.command.state", configOpLevel) || hasPermission(source, "helpful_commands.config.field", configOpLevel) || hasPermission(source, "helpful_commands.config.styling", configOpLevel);
        } else {
            return hasPermission(source, "helpful_commands.config." + configPermission, configOpLevel);
        }
    }
}
