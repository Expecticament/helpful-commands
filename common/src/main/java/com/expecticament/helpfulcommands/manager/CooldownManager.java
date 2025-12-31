package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.io.JsonIO;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.*;

public class CooldownManager {

    public enum CooldownType { HOME_TP, TPR_REQUEST }

    private static final String FILE_NAME = "cooldowns";

    private static JsonIO<Cooldowns> io;

    public static class Cooldowns {
        private final Map<UUID, EnumMap<CooldownType, Cooldown>> entries = new HashMap<>();
    }

    public static class Cooldown {
        private long expiresAt;

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        public long expiresIn() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }

    public static boolean applyCooldown(ServerPlayer player, CooldownType cooldownType, int durationSeconds) {
        return applyCooldown(player.getUUID(), cooldownType, durationSeconds);
    }

    public static boolean applyCooldown(UUID uuid, CooldownType cooldownType, int durationSeconds) {
        if (durationSeconds <= 0) {
            return false;
        }

        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());

        EnumMap<CooldownType, Cooldown> playerCooldowns = cooldowns.entries.computeIfAbsent(uuid, id -> new EnumMap<>(CooldownType.class));

        Cooldown existing = playerCooldowns.get(cooldownType);

        long newExpiresAt = System.currentTimeMillis() + durationSeconds * 1000L;

        if (existing != null && !existing.isExpired() && existing.expiresIn() >= newExpiresAt) {
            return false;
        }

        Cooldown cooldown = new Cooldown();
        cooldown.expiresAt = newExpiresAt;

        playerCooldowns.put(cooldownType, cooldown);

        io.save(cooldowns);

        return true;
    }

    public static long getRemainingCooldown(ServerPlayer player, CooldownType cooldownType) {
        return getRemainingCooldown(player.getUUID(), cooldownType);
    }

    public static long getRemainingCooldown(UUID uuid, CooldownType cooldownType) {
        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());

        EnumMap<CooldownType, Cooldown> playerCooldowns = cooldowns.entries.get(uuid);
        if (playerCooldowns == null) {
            return 0;
        }

        Cooldown cooldown = playerCooldowns.get(cooldownType);
        if (cooldown == null) {
            return 0;
        }

        if (cooldown.isExpired()) {
            playerCooldowns.remove(cooldownType);

            if (playerCooldowns.isEmpty()) {
                cooldowns.entries.remove(uuid);
            }

            io.save(cooldowns);
            return 0;
        }

        return cooldown.expiresIn();
    }

    public static boolean removeCooldown(ServerPlayer player, CooldownType cooldownType) {
        return removeCooldown(player.getUUID(), cooldownType);
    }

    public static boolean removeCooldown(UUID uuid, CooldownType cooldownType) {
        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());

        EnumMap<CooldownType, Cooldown> playerCooldowns = cooldowns.entries.get(uuid);
        if (playerCooldowns == null) {
            return false;
        }

        Cooldown cooldown = playerCooldowns.get(cooldownType);
        if (cooldown == null) {
            return false;
        }

        if (cooldown.isExpired()) {
            playerCooldowns.remove(cooldownType);

            if (playerCooldowns.isEmpty()) {
                cooldowns.entries.remove(uuid);
            }

            io.save(cooldowns);
            return false;
        }

        playerCooldowns.remove(cooldownType);

        io.save(cooldowns);

        return true;
    }

    public static int removeAllCooldowns(ServerPlayer player) {
        return removeAllCooldowns(player.getUUID());
    }

    public static int removeAllCooldowns(UUID uuid) {
        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());

        EnumMap<CooldownType, Cooldown> playerCooldowns = cooldowns.entries.remove(uuid);
        if (playerCooldowns == null || playerCooldowns.isEmpty()) {
            return 0;
        }

        io.save(cooldowns);

        return playerCooldowns.size();
    }

    public static int removeAllCooldowns() {
        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());

        int removed = cooldowns.entries.values()
                .stream()
                .mapToInt(Map::size)
                .sum();

        cooldowns.entries.clear();
        io.save(cooldowns);

        return removed;
    }

    public static void removeExpired() {
        Cooldowns cooldowns = Objects.requireNonNullElse(io.read(), new Cooldowns());
        boolean changed = false;

        Iterator<Map.Entry<UUID, EnumMap<CooldownType, Cooldown>>> playerIterator = cooldowns.entries.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<UUID, EnumMap<CooldownType, Cooldown>> playerEntry = playerIterator.next();
            EnumMap<CooldownType, Cooldown> playerCooldowns = playerEntry.getValue();

            playerCooldowns.entrySet().removeIf(e -> e.getValue().isExpired());

            if (playerCooldowns.isEmpty()) {
                playerIterator.remove();
            }

            changed = true;
        }

        if (changed) {
            io.save(cooldowns);
        }
    }

    public static int initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, Cooldowns.class);
        Cooldowns cooldowns = io.read();
        if (cooldowns == null) {
            return 0;
        }
        return cooldowns.entries.values()
                .stream()
                .mapToInt(Map::size)
                .sum();
    }

    public static void writeToDisk() {
        io.writeToDisk();
    }
}
