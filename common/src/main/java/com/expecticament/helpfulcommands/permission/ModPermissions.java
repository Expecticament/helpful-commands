package com.expecticament.helpfulcommands.permission;

import com.expecticament.helpfulcommands.HelpfulCommands;
import net.minecraft.server.permissions.PermissionLevel;

public class ModPermissions {
    public enum Permission {
        COMMAND_HC_CONFIG(PermissionLevel.ADMINS), COMMAND_HC_CONFIG_COMMAND(PermissionLevel.ADMINS), COMMAND_HC_CONFIG_COMMAND_STATE(PermissionLevel.ADMINS), COMMAND_HC_CONFIG_FIELD(PermissionLevel.ADMINS), COMMAND_HC_COOLDOWNS(PermissionLevel.ADMINS),
        COMMAND_FLY(PermissionLevel.GAMEMASTERS), COMMAND_FLY_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_GOD(PermissionLevel.GAMEMASTERS), COMMAND_GOD_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_INVSEE(PermissionLevel.GAMEMASTERS),
        COMMAND_RENAME(PermissionLevel.GAMEMASTERS), COMMAND_RENAME_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_REPAIR(PermissionLevel.GAMEMASTERS), COMMAND_REPAIR_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_SMELT(PermissionLevel.GAMEMASTERS), COMMAND_SMELT_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_DEATHPOS(PermissionLevel.GAMEMASTERS), COMMAND_DEATHPOS_TP(PermissionLevel.GAMEMASTERS), COMMAND_DEATHPOS_TP_OTHER(PermissionLevel.GAMEMASTERS), COMMAND_DEATHPOS_QUERY_OTHER(PermissionLevel.GAMEMASTERS),
        COMMAND_DIMENSION(PermissionLevel.GAMEMASTERS), COMMAND_DIMENSION_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_HOME(PermissionLevel.GAMEMASTERS), COMMAND_HOME_TP(PermissionLevel.GAMEMASTERS),
        COMMAND_JUMP(PermissionLevel.GAMEMASTERS),
        COMMAND_SPAWN(PermissionLevel.GAMEMASTERS), COMMAND_SPAWN_PLAYER_TP(PermissionLevel.GAMEMASTERS), COMMAND_SPAWN_PLAYER_TP_OTHER(PermissionLevel.GAMEMASTERS), COMMAND_SPAWN_PLAYER_QUERY_OTHER(PermissionLevel.GAMEMASTERS), COMMAND_SPAWN_WORLD(PermissionLevel.GAMEMASTERS), COMMAND_SPAWN_WORLD_TP(PermissionLevel.GAMEMASTERS),
        COMMAND_TPR(PermissionLevel.GAMEMASTERS), COMMAND_TPR_REQUEST(PermissionLevel.GAMEMASTERS),
        COMMAND_WARP(PermissionLevel.GAMEMASTERS), COMMAND_WARP_TP(PermissionLevel.GAMEMASTERS), COMMAND_WARP_TP_OTHERS(PermissionLevel.GAMEMASTERS), COMMAND_WARP_MANAGE(PermissionLevel.ADMINS),
        COMMAND_COORDS(PermissionLevel.ALL), COMMAND_COORDS_BROADCAST(PermissionLevel.GAMEMASTERS), COMMAND_COORDS_BROADCAST_OTHER(PermissionLevel.GAMEMASTERS), COMMAND_COORDS_SHARE(PermissionLevel.ALL), COMMAND_COORDS_QUERY_OTHER(PermissionLevel.ADMINS),
        COMMAND_DMG(PermissionLevel.GAMEMASTERS),
        COMMAND_EXTINGUISH(PermissionLevel.GAMEMASTERS), COMMAND_EXTINGUISH_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_FEED(PermissionLevel.GAMEMASTERS), COMMAND_FEED_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_GM(PermissionLevel.GAMEMASTERS), COMMAND_GM_A(PermissionLevel.GAMEMASTERS), COMMAND_GM_C(PermissionLevel.GAMEMASTERS), COMMAND_GM_S(PermissionLevel.GAMEMASTERS), COMMAND_GM_SP(PermissionLevel.GAMEMASTERS), COMMAND_GM_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_HAT(PermissionLevel.ALL), COMMAND_HAT_OTHERS(PermissionLevel.GAMEMASTERS), COMMAND_HAT_ITEM(PermissionLevel.GAMEMASTERS),
        COMMAND_HEAL(PermissionLevel.GAMEMASTERS), COMMAND_HEAL_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_IGNITE(PermissionLevel.GAMEMASTERS), COMMAND_IGNITE_OTHERS(PermissionLevel.GAMEMASTERS),
        COMMAND_COINFLIP(PermissionLevel.ALL),
        COMMAND_EXPLOSION(PermissionLevel.GAMEMASTERS),
        COMMAND_FIREBALL(PermissionLevel.GAMEMASTERS),
        COMMAND_KILLITEMS(PermissionLevel.GAMEMASTERS),
        COMMAND_LIGHTNING(PermissionLevel.GAMEMASTERS),
        COMMAND_DAY(PermissionLevel.GAMEMASTERS),
        COMMAND_NIGHT(PermissionLevel.GAMEMASTERS);

        private final String id;
        private final PermissionLevel defaultRequiredPermissionLevel;

        Permission(PermissionLevel defaultRequiredPermissionLevel) {
            String permName = this.name().toLowerCase().replaceAll("_", ".");
            if (HelpfulCommands.getPlatform().equals(HelpfulCommands.Platform.NEO_FORGE)) {
                this.id = permName;
            } else {
                this.id = "%s.%s".formatted(HelpfulCommands.MOD_ID, permName);
            }
            this.defaultRequiredPermissionLevel = defaultRequiredPermissionLevel;
        }

        public String getId() {
            return id;
        }

        public PermissionLevel getDefaultRequiredPermission() {
            return defaultRequiredPermissionLevel;
        }
    }
}
