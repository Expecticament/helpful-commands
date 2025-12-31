package com.expecticament.helpfulcommands.helper;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.permission.PermissionHandlerProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class PermissionHelper {
    public static boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        return PermissionHandlerProvider.get().hasPermission(source, permission);
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
