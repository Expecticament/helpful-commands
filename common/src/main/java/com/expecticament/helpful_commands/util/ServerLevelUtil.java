package com.expecticament.helpful_commands.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public class ServerLevelUtil {
    private static MinecraftServer server;

    public static class UnknownServerLevelException extends Exception {
        public UnknownServerLevelException(String serverLevelKey) {
            super("Unknown dimension: %s".formatted(serverLevelKey));
        }
    };

    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static ServerLevel getLevel(String levelLocation) throws UnknownServerLevelException {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (levelLocation.equals(serverLevel.dimension().identifier().toString())) {
                return serverLevel;
            }
        }

        throw new UnknownServerLevelException(levelLocation);
    }

    public static String getLevelLocation(ServerLevel serverLevel) {
        return serverLevel.dimension().identifier().toString();
    }
}
