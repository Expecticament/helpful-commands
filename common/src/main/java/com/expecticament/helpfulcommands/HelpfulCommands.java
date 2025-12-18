package com.expecticament.helpfulcommands;

import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.manager.*;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HelpfulCommands {
    public static final String MOD_ID = "helpful_commands";
    public static final String FOLDER_NAME = MOD_ID + "4";

    private static String modVersion;

    private static boolean isDedicatedServer;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init(String version) {
        modVersion = version;
    }

    public static void onServerStarting(MinecraftServer server) {
        isDedicatedServer = server.isDedicatedServer();

        ConfigManager.initialize(server);
        StylingManager.initialize();
        HomeManager.initialize(server);
        WarpManager.initialize(server);
        TranslationManager.initialize();
        ServerLevelHelper.initialize(server);
        CooldownManager.initialize(server);

        LOGGER.info("");
        LOGGER.info("H   H  CCCCC  | ");
        LOGGER.info("H   H  C      |  Thank you for using");
        LOGGER.info("HHHHH  C      |  Helpful Commands");
        LOGGER.info("H   H  C      |  " + HelpfulCommands.modVersion);
        LOGGER.info("H   H  CCCCC  | ");
        LOGGER.info("");
    }

    public static void save(MinecraftServer minecraftServer) {
        ConfigManager.writeToDisk();
        HomeManager.writeToDisk();
        WarpManager.writeToDisk();
        CooldownManager.removeExpired();
        CooldownManager.writeToDisk();
    }

    public static String getModVersion() { return modVersion; }

    public static boolean isDedicatedServer() { return isDedicatedServer; }
}
