package com.expecticament.helpful_commands.helper;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.expecticament.helpful_commands.manager.ConfigManager;
import com.expecticament.helpful_commands.permission.ModPermissions;
import com.expecticament.helpful_commands.permission.PermissionHandlerProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;

public class PermissionHelper {
    public static boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        return PermissionHandlerProvider.get().hasPermission(source, permission);
    }

    public static boolean hasPermissionLuckPerms(ServerPlayer player, ModPermissions.Permission permission) {
        boolean hasPermission = player.permissions().hasPermission(new Permission.HasCommandLevel(permission.getDefaultRequiredPermission()));
        try {
            net.luckperms.api.util.Tristate result = net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class).getPermissionData(player).checkPermission(permission.getId());
            if (result == net.luckperms.api.util.Tristate.UNDEFINED) {
                return hasPermission;
            }
            return result == net.luckperms.api.util.Tristate.TRUE;
        } catch (Exception | NoClassDefFoundError e) {
            return hasPermission;
        }
    }

    public static <T> T getMetaOrElseConfigValue(CommandSourceStack source, ConfigManager.CONFIG_FIELD field) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return getMetaOrElseConfigValue(player, field);
        } catch (CommandSyntaxException e) {
            return ConfigManager.readConfig().readField(field);
        }
    }

    public static <T> T getMetaOrElseConfigValue(ServerPlayer player, ConfigManager.CONFIG_FIELD field) {
        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        try {
            if (!field.lpMetaSupport()) {
                return config.readField(field);
            }

            String meta = net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class).getMetaData(player).getMetaValue(HelpfulCommands.SHORT_MOD_ID + "_" + field.toString().toLowerCase());
            if (meta == null) {
                return config.readField(field);
            }

            return config.validateFieldValue(field, meta);
        } catch (Exception | NoClassDefFoundError e) {
            return config.readField(field);
        }
    }

    public static boolean isLuckPermsAvailable() {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            return true;
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return false;
        }
    }
}
