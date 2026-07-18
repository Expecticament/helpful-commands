package com.expecticament.helpfulcommands.platform;

import com.expecticament.helpfulcommands.Constants;
import com.expecticament.helpfulcommands.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public Platform getPlatform() {
        return Platform.FABRIC;
    }

    @Override
    public String getModVersion() {
        try {
            return FabricLoader.getInstance().getModContainer(Constants.MOD_ID).orElseThrow().getMetadata().getVersion().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String getEnvironmentName() {
        return IPlatformHelper.super.getEnvironmentName();
    }
}
