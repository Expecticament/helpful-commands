package com.expecticament.helpful_commands;

import com.expecticament.helpful_commands.manager.warp.WarpManager;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.manager.*;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HelpfulCommands {
    public static final String MOD_ID = "helpful_commands";
    public static final String SHORT_MOD_ID = "hc";
    public static final String FOLDER_NAME = MOD_ID + "4";

    public enum Platform {
        FABRIC("Fabric"),
        NEO_FORGE("NeoForge");

        private final String displayName;

        Platform(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static Platform platform;
    private static String modVersion;
    private static boolean isDedicatedServer;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init(Platform platform, String modVersion) {
        HelpfulCommands.platform = platform;
        HelpfulCommands.modVersion = modVersion;
    }

    public static void onServerStarting(MinecraftServer server) {
        isDedicatedServer = server.isDedicatedServer();

        LOGGER.info("H   H  CCCCC  | ");
        LOGGER.info("H   H  C      |  Helpful Commands");
        LOGGER.info("HHHHH  C      |  v{}", modVersion);
        LOGGER.info("H   H  C      |  Platform: {}", platform.toString());
        LOGGER.info("H   H  CCCCC  | ");

        ConfigManager.initialize(server);
        StylingManager.initialize();

        int homes = HomeManager.initialize(server);
        LOGGER.info("Loaded {} home{}", homes, homes == 1 ? "" : "s");
        int warps = WarpManager.initialize(server);
        LOGGER.info("Loaded {} warp{}", warps, warps == 1 ? "" : "s");
        int cooldowns = CooldownManager.initialize(server);
        LOGGER.info("Loaded {} active cooldown{}", cooldowns, cooldowns == 1 ? "" : "s");
        TranslationManager.initialize();
        ServerLevelUtil.initialize(server);
    }

    public static void save(MinecraftServer minecraftServer) {
        ConfigManager.writeToDisk();
        HomeManager.writeToDisk();
        WarpManager.flush();
        CooldownManager.removeExpired();
        CooldownManager.writeToDisk();
    }

    public static Platform getPlatform() { return platform; }

    public static String getModVersion() { return modVersion; }

    public static boolean isDedicatedServer() { return isDedicatedServer; }
}
