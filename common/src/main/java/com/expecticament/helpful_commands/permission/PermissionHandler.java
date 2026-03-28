package com.expecticament.helpful_commands.permission;

import net.minecraft.commands.CommandSourceStack;

public interface PermissionHandler {
    boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission);
}