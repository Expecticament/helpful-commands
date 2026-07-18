package com.expecticament.helpfulcommands.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;

public class PermissionHandlerFabricImpl implements PermissionHandler {

    @Override
    public boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        return Permissions.check(source, permission.getId(), permission.getDefaultRequiredPermission());
    }
}