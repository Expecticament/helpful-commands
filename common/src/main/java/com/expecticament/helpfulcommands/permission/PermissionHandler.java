package com.expecticament.helpfulcommands.permission;

import net.minecraft.commands.CommandSourceStack;

public interface PermissionHandler {
    boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission);
}