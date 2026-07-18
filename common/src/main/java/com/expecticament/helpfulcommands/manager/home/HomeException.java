package com.expecticament.helpfulcommands.manager.home;

import net.minecraft.server.level.ServerPlayer;

public class HomeException {
    public static class AlreadyExists extends RuntimeException {
        public AlreadyExists(ServerPlayer player, String name) {
            super("Player %s (%s) already has a home named '%s'.".formatted(player.getGameProfile().name(), player.getStringUUID(), name));
        }
    }

    public static class DoesntExist extends RuntimeException {
        public DoesntExist(ServerPlayer player, String name) {
            super("Player %s (%s) doesn't have a home named '%s'.".formatted(player.getGameProfile().name(), player.getStringUUID(), name));
        }
    }

    public static class SameName extends RuntimeException {
        public SameName(String name) {
            super("The home is already named '%s'.".formatted(name));
        }
    }

    public static class LimitReached extends RuntimeException {
        public LimitReached(ServerPlayer player) {
            super("Player %s (%s) can't create any more homes".formatted(player.getGameProfile().name(), player.getStringUUID()));
        }
    }
}
