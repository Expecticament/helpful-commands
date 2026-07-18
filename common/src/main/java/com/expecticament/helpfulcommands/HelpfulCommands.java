package com.expecticament.helpfulcommands;

import com.expecticament.helpfulcommands.manager.ConfigManager;
import com.expecticament.helpfulcommands.manager.CooldownManager;
import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.manager.home.HomeManager;
import com.expecticament.helpfulcommands.manager.translation.TranslationManager;
import com.expecticament.helpfulcommands.manager.warp.WarpManager;
import com.expecticament.helpfulcommands.platform.Services;
import com.expecticament.helpfulcommands.util.ServerLevelUtil;
import net.minecraft.server.MinecraftServer;
import static com.expecticament.helpfulcommands.Constants.LOGGER;

public class HelpfulCommands {
    private static boolean isDedicatedServer;

    public static void init() {

    }

    public static void onServerStarting(MinecraftServer server) {
        isDedicatedServer = server.isDedicatedServer();

        LOGGER.info("H   H  CCCCC  | ");
        LOGGER.info("H   H  C      |  Helpful Commands");
        LOGGER.info("HHHHH  C      |  v{}", Services.PLATFORM.getModVersion());
        LOGGER.info("H   H  C      |  Platform: {}", Services.PLATFORM.getPlatform().toString());
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
        HomeManager.flush();
        WarpManager.flush();
        CooldownManager.removeExpired();
        CooldownManager.writeToDisk();
    }

    public static boolean isDedicatedServer() { return isDedicatedServer; }
}