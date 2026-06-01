package com.expecticament.helpful_commands.manager.home;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.expecticament.helpful_commands.manager.ConfigManager;
import com.expecticament.helpful_commands.util.PermissionsUtil;
import com.expecticament.helpful_commands.util.ServerLevelUtil;
import com.expecticament.helpful_commands.util.io.JsonIO;
import net.minecraft.core.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.*;

/**
 * Manages per-player homes.
 *
 * <p>All mutating methods update an in-memory buffer. {@link #flush()}
 * writes the current memory buffer to disk.</p>
 */
public class HomeManager {

    /** JSON root wrapper. */
    private static class HomesData {
        private final Map<UUID, Map<String, Home>> entries = new HashMap<>();
    }

    private static final String FILE_NAME = "homes";
    private static JsonIO<HomesData> io;

    /**
     * Initializes the home manager and loads existing homes from disk.
     *
     * @param server the running Minecraft server
     * @return the total number of homes loaded across all players
     */
    public static int initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, HomesData.class);
        HomesData data = io.getData();
        return data == null ? 0 : data.entries.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Writes the current memory buffer to disk.
     */
    public static void flush() {
        io.flush();
    }

    /**
     * Returns whether the player has a home with the given name.
     *
     * @param player the player to check
     * @param name   the home name to look up
     * @return {@code true} if the home exists and {@code false} otherwise
     */
    public static boolean hasHome(ServerPlayer player, String name) {
        return playerHomes(player).containsKey(name);
    }

    /**
     * Returns the named home belonging to the given player.
     *
     * @param player the owning player
     * @param name   the home name
     * @return the {@link Home}
     * @throws HomeException.DoesntExist if the player has no home with that name
     */
    public static Home getHome(ServerPlayer player, String name) throws HomeException.DoesntExist {
        Home home = playerHomes(player).get(name);
        if (home != null) return home;
        throw new HomeException.DoesntExist(player, name);
    }

    /**
     * Returns an unmodifiable view of all homes belonging to the given player.
     *
     * @param player the player whose homes to retrieve
     * @return the player's homes
     */
    public static Map<String, Home> getHomes(ServerPlayer player) {
        return Collections.unmodifiableMap(playerHomes(player));
    }

    /**
     * Creates a new home at the player's current position.
     *
     * @param player the owning player
     * @param name   the unique home name
     * @throws HomeException.LimitReached  if the player has reached their home limit
     * @throws HomeException.AlreadyExists if the player already has a home with that name
     */
    public static void createHome(ServerPlayer player, String name) throws HomeException.AlreadyExists, HomeException.LimitReached {
        HomesData data = getData();
        Map<String, Home> homes = playerHomes(player, data);

        int maxHomes = PermissionsUtil.getMetaOrElseConfigValue(player, ConfigManager.CONFIG_FIELD.MAX_HOMES);
        if (homes.size() >= maxHomes){
            throw new HomeException.LimitReached(player);
        }

        if (homes.putIfAbsent(name, new Home(player.position(), player.level())) != null) {
            throw new HomeException.AlreadyExists(player, name);
        }

        data.entries.put(player.getUUID(), homes);
        io.updateBuffer(data);
    }

    /**
     * Removes an existing home belonging to the given player.
     *
     * @param player the owning player
     * @param name   the name of the home to remove
     * @throws HomeException.DoesntExist if the player has no home with that name
     */
    public static void removeHome(ServerPlayer player, String name) throws HomeException.DoesntExist {
        HomesData data = getData();
        Map<String, Home> homes = playerHomes(player, data);

        if (homes.remove(name) == null) {
            throw new HomeException.DoesntExist(player, name);
        }

        data.entries.put(player.getUUID(), homes);
        io.updateBuffer(data);
    }

    /**
     * Renames an existing home belonging to the given player.
     *
     * @param player  the owning player
     * @param name    the current home name
     * @param newName the desired new name
     * @throws HomeException.SameName      if {@code name} and {@code newName} are equal
     * @throws HomeException.DoesntExist   if the player has no home named {@code name}
     * @throws HomeException.AlreadyExists if the player already has a home named {@code newName}
     */
    public static void setHomeName(ServerPlayer player, String name, String newName) throws HomeException.SameName, HomeException.DoesntExist, HomeException.AlreadyExists {
        if (name.equals(newName)){
            throw new HomeException.SameName(newName);
        }

        HomesData data = getData();
        Map<String, Home> homes = playerHomes(player, data);

        if (!homes.containsKey(name)) {
            throw new HomeException.DoesntExist(player, name);
        }
        if (homes.containsKey(newName)) {
            throw new HomeException.AlreadyExists(player, newName);
        }

        homes.put(newName, homes.remove(name));
        data.entries.put(player.getUUID(), homes);
        io.updateBuffer(data);
    }

    /**
     * Updates the location of an existing home belonging to the given player.
     *
     * @param player      the owning player
     * @param name        the home name
     * @param newPosition the new position
     * @param newLevel    the new dimension
     * @throws HomeException.DoesntExist if the player has no home with that name
     */
    public static void setHomeLocation(ServerPlayer player, String name, Position newPosition, ServerLevel newLevel) throws HomeException.DoesntExist {
        HomesData data = getData();
        Map<String, Home> homes = playerHomes(player, data);

        Home home = homes.get(name);
        if (home == null) {
            throw new HomeException.DoesntExist(player, name);
        }

        home.x = newPosition.x();
        home.y = newPosition.y();
        home.z = newPosition.z();
        home.dimension = ServerLevelUtil.getLevelLocation(newLevel);
        data.entries.put(player.getUUID(), homes);
        io.updateBuffer(data);
    }

    private static HomesData getData() {
        return Objects.requireNonNullElse(io.getData(), new HomesData());
    }

    private static Map<String, Home> playerHomes(ServerPlayer player) {
        return playerHomes(player, getData());
    }

    private static Map<String, Home> playerHomes(ServerPlayer player, HomesData data) {
        return Objects.requireNonNullElse(data.entries.get(player.getUUID()), new HashMap<>());
    }
}
