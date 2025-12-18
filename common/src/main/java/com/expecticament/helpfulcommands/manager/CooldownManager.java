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
        Cooldowns data = Objects.requireNonNullElse(io.read(), new Cooldowns());

        EnumMap<CooldownType, Cooldown> playerCooldowns = data.entries.get(uuid);

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
                data.entries.remove(uuid);
            }

            io.save(data);
            return 0;
        }

        return cooldown.expiresIn();
    }

    public static void removeExpired() {
        Cooldowns data = Objects.requireNonNullElse(io.read(), new Cooldowns());
        boolean changed = false;

        Iterator<Map.Entry<UUID, EnumMap<CooldownType, Cooldown>>> playerIterator = data.entries.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<UUID, EnumMap<CooldownType, Cooldown>> playerEntry = playerIterator.next();
            EnumMap<CooldownType, Cooldown> cooldowns = playerEntry.getValue();

            cooldowns.entrySet().removeIf(e -> e.getValue().isExpired());

            if (cooldowns.isEmpty()) {
                playerIterator.remove();
            }

            changed = true;
        }

        if (changed) {
            io.save(data);
        }
    }

    public static void initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, Cooldowns.class);
    }

    public static void writeToDisk() {
        io.writeToDisk();
    }
}
