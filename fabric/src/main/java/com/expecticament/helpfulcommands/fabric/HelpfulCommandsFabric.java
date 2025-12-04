package com.expecticament.helpfulcommands.fabric;

import com.expecticament.helpfulcommands.manager.ModCommandManager;
import net.fabricmc.api.ModInitializer;

import com.expecticament.helpfulcommands.HelpfulCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class HelpfulCommandsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        String version;
        try {
            version = FabricLoader.getInstance().getModContainer(HelpfulCommands.MOD_ID).orElseThrow().getMetadata().getVersion().toString();
        } catch (Exception e) {
            version = "unknown";
        }
        // Run our common setup.
        HelpfulCommands.init(version);

        // Register commands
        CommandRegistrationCallback.EVENT.register(ModCommandManager::registerCommands);

        // Server events
        ServerLifecycleEvents.SERVER_STARTING.register(HelpfulCommands::onServerStarting);
        ServerLifecycleEvents.BEFORE_SAVE.register((minecraftServer, b, b1) -> HelpfulCommands.save(minecraftServer));
    }
}
