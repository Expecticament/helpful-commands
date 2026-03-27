package com.expecticament.helpfulcommands.permission;

import com.expecticament.helpfulcommands.helper.PermissionHelper;
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
