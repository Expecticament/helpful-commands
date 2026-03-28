package com.expecticament.helpful_commands.permission;

import com.expecticament.helpful_commands.helper.PermissionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;

public class PermissionHandlerNeoForgeImpl implements PermissionHandler {
    @Override
    public boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            return source.permissions().hasPermission(new Permission.HasCommandLevel(permission.getDefaultRequiredPermission()));
        }

        return PermissionHelper.hasPermissionLuckPerms(player, permission);
    }
}
