package com.expecticament.helpfulcommands.neoforge;

import com.expecticament.helpfulcommands.manager.ModCommandManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import com.expecticament.helpfulcommands.HelpfulCommands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(HelpfulCommands.MOD_ID)
public final class HelpfulCommandsNeoForge {
    public HelpfulCommandsNeoForge() {
        String version = ModList.get()
                .getModContainerById(HelpfulCommands.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");

        // Run our common setup.
        HelpfulCommands.init(version);

        // Register commands
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Server events
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            HelpfulCommands.onServerStarting(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.LevelEvent.Save.class, event -> {
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
