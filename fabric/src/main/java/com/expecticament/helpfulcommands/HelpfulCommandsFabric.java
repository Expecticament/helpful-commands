package com.expecticament.helpfulcommands;

import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.permission.PermissionHandlerFabricImpl;
import com.expecticament.helpfulcommands.permission.PermissionHandlerProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class HelpfulCommandsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        HelpfulCommands.init();

        // Permissions
        PermissionHandlerProvider.instance = new PermissionHandlerFabricImpl();

        // Register commands
        CommandRegistrationCallback.EVENT.register(ModCommandManager::registerCommands);

        // Server events
        ServerLifecycleEvents.SERVER_STARTING.register(HelpfulCommands::onServerStarting);
        ServerLifecycleEvents.BEFORE_SAVE.register((minecraftServer, b, b1) -> HelpfulCommands.save(minecraftServer));
    }
}
