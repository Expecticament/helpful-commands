package com.expecticament.helpfulcommands.platform;

import com.expecticament.helpfulcommands.Constants;
import com.expecticament.helpfulcommands.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public Platform getPlatform() {
        return Platform.NEO_FORGE;
    }

    @Override
    public String getModVersion() {
        return ModList.get()
                .getModContainerById(Constants.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public String getEnvironmentName() {
        return IPlatformHelper.super.getEnvironmentName();
    }
}