package com.expecticament.helpful_commands.util;

import com.expecticament.helpful_commands.HelpfulCommands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class PlayerDataUtil {
    public static CompoundTag loadOfflinePlayerData(MinecraftServer server, UUID uuid) {
        Path playerDataPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
        if (!Files.exists(playerDataPath)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(playerDataPath)) {
            return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            HelpfulCommands.LOGGER.error("Failed to load offline player data for {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    public static void saveOfflinePlayerData(MinecraftServer server, UUID uuid, CompoundTag data) {
        Path playerDataPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
        try (OutputStream os = Files.newOutputStream(playerDataPath)) {
            NbtIo.writeCompressed(data, os);
        } catch (IOException e) {
            HelpfulCommands.LOGGER.error("Failed to save offline player data for {}: {}", uuid, e.getMessage());
        }
    }
}
