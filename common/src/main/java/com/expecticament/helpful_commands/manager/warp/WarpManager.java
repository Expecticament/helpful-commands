package com.expecticament.helpful_commands.manager.warp;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.io.JsonIO;
import net.minecraft.core.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages server warps.
 *
 * <p>All mutating methods update an in-memory buffer. {@link #flush()}
 * writes the current memory buffer to disk.</p>
 */
public class WarpManager {

    /** JSON root wrapper. */
    private static class WarpsData {
        private final Map<String, Warp> entries = new HashMap<>();
    }

    private static final String FILE_NAME = "warps";
    private static JsonIO<WarpsData> io;

    /**
     * Initializes the warp manager and loads existing warps from disk.
     *
     * @param server the running Minecraft server
     * @return the number of warps loaded
     */
    public static int initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, WarpsData.class);
        WarpsData data = io.getData();
        return data == null ? 0 : data.entries.size();
    }

    /**
     * Writes the current memory buffer to disk.
     */
    public static void flush() {
        io.flush();
    }

    /**
     * Returns whether a warp with the given name exists.
     *
     * @param name the warp name to check
     * @return {@code true} if the warp exists and {@code false} otherwise
     */
    public static boolean exists(String name) {
        return getData().entries.containsKey(name);
    }

    /**
     * Returns the warp with the given name.
     *
     * @param name the warp name
     * @return the {@link Warp}
     * @throws WarpException.DoesntExist if no warp with that name exists
     */
    public static Warp getWarp(String name) throws WarpException.DoesntExist {
        Warp warp = getData().entries.get(name);
        if (warp != null) return warp;
        throw new WarpException.DoesntExist(name);
    }

    /**
     * Returns an unmodifiable view of all warps, keyed by name.
     *
     * @return all warps
     */
    public static Map<String, Warp> getWarps() {
        return Collections.unmodifiableMap(getData().entries);
    }

    /**
     * Creates a new warp.
     *
     * @param name        the unique warp name
     * @param description a short description of the warp
     * @param position    the warp's location
     * @param level       the dimension the warp is in
     * @throws WarpException.AlreadyExists if a warp with that name already exists
     */
    public static void createWarp(String name, String description, Position position, ServerLevel level) throws WarpException.AlreadyExists {
        WarpsData data = getData();
        if (data.entries.putIfAbsent(name, new Warp(description, position, level)) != null) {
            throw new WarpException.AlreadyExists(name);
        }
        io.updateBuffer(data);
    }

    /**
     * Removes an existing warp.
     *
     * @param name the warp name to remove
     * @throws WarpException.DoesntExist if no warp with that name exists
     */
    public static void removeWarp(String name) throws WarpException.DoesntExist {
        WarpsData data = getData();
        if (data.entries.remove(name) == null) {
            throw new WarpException.DoesntExist(name);
        }
        io.updateBuffer(data);
    }

    /**
     * Renames an existing warp.
     *
     * @param name    the current warp name
     * @param newName the desired new name
     * @throws WarpException.SameName      if {@code name} and {@code newName} are equal
     * @throws WarpException.DoesntExist   if no warp with {@code name} exists
     * @throws WarpException.AlreadyExists if a warp named {@code newName} already exists
     */
    public static void setWarpName(String name, String newName) throws WarpException.SameName, WarpException.DoesntExist, WarpException.AlreadyExists {
        if (name.equals(newName)) {
            throw new WarpException.SameName(newName);
        }

        WarpsData data = getData();
        if (!data.entries.containsKey(name)) {
            throw new WarpException.DoesntExist(name);
        }
        if (data.entries.containsKey(newName)) {
            throw new WarpException.AlreadyExists(newName);
        }

        data.entries.put(newName, data.entries.remove(name));
        io.updateBuffer(data);
    }

    /**
     * Updates the description of an existing warp.
     *
     * @param name           the warp name
     * @param newDescription the new description
     * @throws WarpException.DoesntExist if no warp with that name exists
     */
    public static void setWarpDescription(String name, String newDescription) throws WarpException.DoesntExist {
        Warp warp = getWarp(name);
        warp.description = newDescription;
        io.updateBuffer(getData());
    }

    /**
     * Updates the location of an existing warp.
     *
     * @param name        the warp name
     * @param newPosition the new position
     * @param newLevel    the new dimension
     * @throws WarpException.DoesntExist if no warp with that name exists
     */
    public static void setWarpLocation(String name, Position newPosition, ServerLevel newLevel) throws WarpException.DoesntExist {
        Warp warp = getWarp(name);
        warp.x = newPosition.x();
        warp.y = newPosition.y();
        warp.z = newPosition.z();
        warp.dimension = ServerLevelUtil.getLevelLocation(newLevel);
        io.updateBuffer(getData());
    }

    private static WarpsData getData() {
        return Objects.requireNonNullElse(io.getData(), new WarpsData());
    }
}