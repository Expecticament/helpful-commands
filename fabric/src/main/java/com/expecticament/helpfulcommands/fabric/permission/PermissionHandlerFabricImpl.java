package com.expecticament.helpfulcommands.fabric.permission;

import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.permission.PermissionHandler;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;

public class PermissionHandlerFabricImpl implements PermissionHandler {
    @Override
    public boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        return Permissions.check(source, permission.getId(), permission.getDefaultRequiredPermission());
    }
}