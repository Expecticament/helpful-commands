package com.expecticament.helpfulcommands.neoforge.permission;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.permission.ModPermissions;
import com.expecticament.helpfulcommands.permission.PermissionHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.HashMap;
import java.util.Map;

public class PermissionHandlerNeoForgeImpl implements PermissionHandler {
    private static final Map<String, PermissionNode<Boolean>> nodes = new HashMap<>();

    @Override
    public boolean hasPermission(CommandSourceStack source, ModPermissions.Permission permission) {
        ServerPlayer player = source.getPlayer();
        PermissionNode<Boolean> node = nodes.get(permission.getId());

        if (player == null || node == null) {
            return source.permissions().hasPermission(new Permission.HasCommandLevel(permission.getDefaultRequiredPermission()));
        }

        return PermissionAPI.getPermission(player, node);
    }

    public static void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        for (ModPermissions.Permission permission : ModPermissions.Permission.values()) {
            PermissionNode<Boolean> node = new PermissionNode<>(HelpfulCommands.MOD_ID, permission.getId(), PermissionTypes.BOOLEAN, (serverPlayer, uuid, ctx) -> {
                Permission perm = permissionLevelToPermission(permission.getDefaultRequiredPermission());

                if (serverPlayer == null || perm == null) {
                    return true;
                }

                return serverPlayer.permissions().hasPermission(perm);
            });

            nodes.put(permission.getId(), node);
        }

        nodes.values().forEach(event::addNodes);
    }

    private static Permission permissionLevelToPermission(PermissionLevel permissionLevel) {
        return switch (permissionLevel) {
            case MODERATORS -> Permissions.COMMANDS_MODERATOR;
            case GAMEMASTERS -> Permissions.COMMANDS_GAMEMASTER;
            case ADMINS -> Permissions.COMMANDS_ADMIN;
            case OWNERS -> Permissions.COMMANDS_OWNER;
            case ALL -> null;
        };
    }
}
