package com.expecticament.helpfulcommands.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRulesUtil {
    public static <T> T getRuleValue(ServerLevel serverLevel, GameRule<T> gameRule) {
        return serverLevel.getGameRules().get(gameRule);
    }

    public static boolean isCommandFeedbackEnabled(ServerLevel serverLevel) {
        return getRuleValue(serverLevel, GameRules.SEND_COMMAND_FEEDBACK);
    }
}
