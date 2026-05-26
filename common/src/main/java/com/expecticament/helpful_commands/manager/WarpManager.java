package com.expecticament.helpful_commands.manager;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.io.JsonIO;
import net.minecraft.core.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WarpManager {

    public static class Warp {
        public String description;
        public double x, y, z;
        public String dimension;

        public Warp(String description, Position position, ServerLevel serverLevel) {
            this.description = description;
            x = position.x();
            y = position.y();
            z = position.z();
            this.dimension = ServerLevelUtil.getLevelLocation(serverLevel);
        }
    }

    private static class Warps {
        private final Map<String, Warp> entries = new HashMap<>();
    }

    private static final String FILE_NAME = "warps";

    private static JsonIO<Warps> io;

    public static class WarpAlreadyExistsException extends RuntimeException {
        public WarpAlreadyExistsException(String warpName) {
            super("Warp with this name already exists: %s".formatted(warpName));
        }
    }

    public static class WarpDoesntExistException extends RuntimeException {
        public WarpDoesntExistException(String warpName) {
            super("Warp with this name doesn't exist: %s".formatted(warpName));
        }
    }

    public static class SameWarpNameProvided extends RuntimeException {
        public SameWarpNameProvided(String newName) {
            super("The new warp name is the same as the old one: %s".formatted(newName));
        }
    }

    public static void addWarp(String warpName, String description, Position position, ServerLevel serverLevel) throws WarpAlreadyExistsException {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        if (warps.entries.putIfAbsent(warpName, new Warp(description, position, serverLevel)) == null) {
            io.updateBuffer(warps);
            return;
        }

        throw new WarpAlreadyExistsException(warpName);
    }

    public static void removeWarp(String warpName) throws WarpDoesntExistException {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        if (warps.entries.remove(warpName) != null) {
            io.updateBuffer(warps);
            return;
        }

        throw new WarpDoesntExistException(warpName);
    }

    public static void editWarpName(String warpName, String newName) throws SameWarpNameProvided, WarpDoesntExistException, WarpAlreadyExistsException {
        if (warpName.equals(newName)) {
            throw new SameWarpNameProvided(newName);
        }

        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        if (!warps.entries.containsKey(warpName)) {
            throw new WarpDoesntExistException(warpName);
        }

        if (warps.entries.containsKey(newName)) {
            throw new WarpAlreadyExistsException(newName);
        }

        Warp warp = warps.entries.remove(warpName);
        warps.entries.put(newName, warp);
        io.updateBuffer(warps);
    }

    public static void editWarpDescription(String warpName, String newDescription) throws WarpDoesntExistException {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        Warp warp = warps.entries.get(warpName);
        if (warp != null) {
            warp.description = newDescription;
            io.updateBuffer(warps);
            return;
        }

        throw new WarpDoesntExistException(warpName);
    }

    public static void editWarpLocation(String warpName, Position newPosition, ServerLevel newServerLevel) throws WarpDoesntExistException {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        Warp warp = warps.entries.get(warpName);
        if (warp != null) {
            warp.x = newPosition.x();
            warp.y = newPosition.y();
            warp.z = newPosition.z();
            warp.dimension = ServerLevelUtil.getLevelLocation(newServerLevel);
            io.updateBuffer(warps);
            return;
        }

        throw new WarpDoesntExistException(warpName);
    }

    public static boolean exists(String warpName) {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());
        return warps.entries.containsKey(warpName);
    }

    public static Warp getWarp(String warpName) throws WarpDoesntExistException {
        Warps warps = Objects.requireNonNullElse(io.getData(), new Warps());

        Warp warp = warps.entries.get(warpName);
        if (warp != null) {
            return warp;
        }

        throw new WarpDoesntExistException(warpName);
    }

    public static Map<String, Warp> getWarps() {
        return Objects.requireNonNullElse(io.getData(), new Warps()).entries;
    }

    public static int initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, Warps.class);
        Warps warps = io.getData();
        if (warps == null) {
            return 0;
        }
        return warps.entries.size();
    }

    public static void writeToDisk() {
        io.flush();
    }
}