package com.expecticament.helpfulcommands;

import com.expecticament.helpfulcommands.manager.ModCommandManager;
import com.expecticament.helpfulcommands.permission.PermissionHandlerNeoForgeImpl;
import com.expecticament.helpfulcommands.permission.PermissionHandlerProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@Mod(HelpfulCommands.MOD_ID)
public final class HelpfulCommandsNeoForge {
    public HelpfulCommandsNeoForge() {
        String version = ModList.get()
                .getModContainerById(HelpfulCommands.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");

        IEventBus eventBus = NeoForge.EVENT_BUS;

        // Common init
        HelpfulCommands.init(HelpfulCommands.Platform.NEO_FORGE, version);

        // Permissions
        PermissionHandlerProvider.instance = new PermissionHandlerNeoForgeImpl();

        // Register commands
        eventBus.addListener(this::onRegisterCommands);

        // Server events
        eventBus.addListener((ServerAboutToStartEvent event) -> HelpfulCommands.onServerStarting(event.getServer()));
        eventBus.addListener(LevelEvent.Save.class, event -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                if (serverLevel.dimension() == Level.OVERWORLD) {
                    HelpfulCommands.save(serverLevel.getServer());
                }
            }
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommandManager.registerCommands(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }
}
