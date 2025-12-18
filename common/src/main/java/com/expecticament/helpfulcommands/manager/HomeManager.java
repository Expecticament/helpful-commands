package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.helper.ServerLevelHelper;
import com.expecticament.helpfulcommands.io.JsonIO;
import net.minecraft.core.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class HomeManager {

    public static class Home {
        public double x, y, z;
        public String dimension;

        public Home(Position position, ServerLevel serverLevel) {
            x = position.x();
            y = position.y();
            z = position.z();
            this.dimension = ServerLevelHelper.getLevelLocation(serverLevel);
        }
    }

    private static class Homes {
        private final Map<UUID, Map<String, Home>> entries = new HashMap<>();
    }

    private static final String FILE_NAME = "homes";

    private static JsonIO<Homes> io;

    public static class HomeAlreadyExistsException extends RuntimeException {
        public HomeAlreadyExistsException(ServerPlayer player, String homeName) {
            super("Player %s (%s) already has a home with this name: %s".formatted(player.getName().getString(), player.getStringUUID(), homeName));
        }
    }

    public static class HomeDoesntExistException extends RuntimeException {
        public HomeDoesntExistException(ServerPlayer player, String homeName) {
            super("Player %s (%s) doesn't have a home with this name: %s".formatted(player.getName().getString(), player.getStringUUID(), homeName));
        }
    }

    public static class HomeLimitExceededException extends RuntimeException {
        public HomeLimitExceededException(ServerPlayer player) {
            super("Player %s (%s) can't create any more homes".formatted(player.getName().getString(), player.getStringUUID()));
        }
    }

    public static class SameHomeNameProvided extends RuntimeException {
        public SameHomeNameProvided(String newName) {
            super("The new home name is the same as the old one: %s".formatted(newName));
        }
    }

    public static void initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, Homes.class);
    }

    public static void addHome(ServerPlayer player, String homeName) throws HomeAlreadyExistsException, HomeLimitExceededException {
        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);

        ConfigManager.HelpfulCommandsConfig config = ConfigManager.readConfig();
        int maxHomes = config.readField(ConfigManager.CONFIG_FIELD.MAX_HOMES);

        if (playerHomes.size() >= maxHomes) {
            throw new HomeLimitExceededException(player);
        }

        if (playerHomes.putIfAbsent(homeName, new Home(player.position(), player.level())) == null) {
            homes.entries.put(player.getUUID(), playerHomes);
            io.save(homes);
            return;
        }

        throw new HomeAlreadyExistsException(player, homeName);
    }

    public static void removeHome(ServerPlayer player, String homeName) throws HomeDoesntExistException {
        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);

        if (playerHomes.remove(homeName) != null) {
            homes.entries.put(player.getUUID(), playerHomes);
            io.save(homes);
            return;
        }

        throw new HomeDoesntExistException(player, homeName);
    }

    public static void editHomeName(ServerPlayer player, String homeName, String newName) throws SameHomeNameProvided, HomeDoesntExistException, HomeAlreadyExistsException {
        if (homeName.equals(newName)) {
            throw new SameHomeNameProvided(newName);
        }

        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);

        if (playerHomes.containsKey(newName)) {
            throw new HomeAlreadyExistsException(player, newName);
        }

        Home prev = playerHomes.remove(homeName);

        if (prev != null) {
            playerHomes.put(newName, prev);
            homes.entries.put(player.getUUID(), playerHomes);
            io.save(homes);
            return;
        }

        throw new HomeDoesntExistException(player, homeName);
    }

    public static void editHomeLocation(ServerPlayer player, String homeName, Position newPosition, ServerLevel newServerLevel) throws HomeDoesntExistException {
        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);

        Home home = playerHomes.get(homeName);

        if (home != null) {
            home.x = newPosition.x();
            home.y = newPosition.y();
            home.z = newPosition.z();
            home.dimension = ServerLevelHelper.getLevelLocation(newServerLevel);
            playerHomes.put(homeName, home);
            homes.entries.put(player.getUUID(), playerHomes);
            io.save(homes);
            return;
        }

        throw new HomeDoesntExistException(player, homeName);
    }

    public static boolean hasHome(ServerPlayer player, String homeName) {
        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);
        return playerHomes.containsKey(homeName);
    }

    public static Home getHome(ServerPlayer player, String homeName) throws HomeDoesntExistException {
        Homes homes = Objects.requireNonNullElse(io.read(), new Homes());
        Map<String, Home> playerHomes = getHomesForPlayer(player, homes);

        Home home = playerHomes.get(homeName);
        if (home != null) {
            return home;
        }

        throw new HomeDoesntExistException(player, homeName);
    }

    public static Map<String, Home> getHomesForPlayer(ServerPlayer player) {
        return getHomesForPlayer(player, Objects.requireNonNullElse(io.read(), new Homes()));
    }

    private static Map<String, Home> getHomesForPlayer(ServerPlayer player, Homes homes) {
        return Objects.requireNonNullElse(homes.entries.get(player.getUUID()), new HashMap<>());
    }

    public static void writeToDisk() {
        io.writeToDisk();
    }
}
