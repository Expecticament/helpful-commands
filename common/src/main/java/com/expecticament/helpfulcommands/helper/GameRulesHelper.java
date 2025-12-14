package com.expecticament.helpfulcommands.helper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRulesHelper {

    private static <T> T getGamerule(ServerLevel serverLevel, GameRule<T> gameRule) {
        return serverLevel.getGameRules().get(gameRule);
    }

    public static boolean commandFeedbackEnabled(ServerLevel serverLevel) {
        return getGamerule(serverLevel, GameRules.SEND_COMMAND_FEEDBACK);
    }
}
