package com.expecticament.helpfulcommands.manager;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class TpRequestsManager {

    public static class PendingRequestExistsException extends RuntimeException {
        public PendingRequestExistsException(ServerPlayer from, ServerPlayer to) {
            super("%s already has a pending tp request to %s!".formatted(from.getName().getString(), to.getName().getString()));
        }
    }

    private final static Map<UUID, Request> sentRequests = new HashMap<>();
    private final static Map<UUID, List<Request>> receivedRequests = new HashMap<>();

    public static class Request {
        private final UUID from;
        private final UUID to;
        private final long expiresAt;

        private Request(UUID from, UUID to, long timeout) {
            this.from = from;
            this.to = to;
            this.expiresAt = System.currentTimeMillis() + timeout;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long expiresIn() {
            return expiresAt - System.currentTimeMillis();
        }

        public UUID getFrom() {
            return from;
        }
        public UUID getTo() {
            return to;
        }
    }

    public static void newRequest(ServerPlayer from, ServerPlayer to, long timeout) throws PendingRequestExistsException {
        UUID fromUUID = from.getUUID();
        UUID toUUID = to.getUUID();

        removeExpired();

        if (sentRequests.containsKey(fromUUID)) {
            throw new PendingRequestExistsException(from, to);
        }

        Request request = new Request(fromUUID, toUUID, timeout);

        sentRequests.put(fromUUID, request);

        receivedRequests
                .computeIfAbsent(toUUID, uuid -> new ArrayList<>())
                .add(request);
    }

    public static boolean removeRequest(ServerPlayer from) {
        UUID fromUUID = from.getUUID();

        Request request = sentRequests.remove(fromUUID);
        if (request == null) {
            return false;
        }

        UUID toUUID = request.getTo();

        List<Request> received = receivedRequests.get(toUUID);
        if (received != null) {
            received.removeIf(r -> r.getFrom().equals(fromUUID));
            if (received.isEmpty()) {
                receivedRequests.remove(toUUID);
            }
        }

        return true;
    }

    private static void removeExpired() {
        Iterator<Map.Entry<UUID, Request>> iterator = sentRequests.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Request> entry = iterator.next();
            Request request = entry.getValue();

            if (request.isExpired()) {
                iterator.remove();

                UUID toUUID = request.getTo();
                List<Request> received = receivedRequests.get(toUUID);
                if (received != null) {
                    received.removeIf(r -> r.getFrom().equals(request.getFrom()));
                    if (received.isEmpty()) {
                        receivedRequests.remove(toUUID);
                    }
                }
            }
        }
    }

    public static Request getSentRequest(ServerPlayer from) {
        return getSentRequest(from.getUUID());
    }

    public static Request getSentRequest(UUID from) {
        removeExpired();

        return sentRequests.get(from);
    }

    public static Request getSentRequest(ServerPlayer from, ServerPlayer to) {
        return getSentRequest(from.getUUID(), to.getUUID());
    }

    public static Request getSentRequest(UUID from, UUID to) {
        removeExpired();

        Request request = sentRequests.get(from);
        if (request != null && request.getTo().equals(to)) {
            return request;
        }

        return null;
    }

    public static boolean hasSentRequest(ServerPlayer player) {
        return hasSentRequest(player.getUUID());
    }

    public static boolean hasSentRequest(UUID uuid) {
        Request request = sentRequests.get(uuid);
        if (request == null) {
            return false;
        }

        if (request.isExpired()) {
            sentRequests.remove(uuid);
            return false;
        }

        return true;
    }

    public static List<Request> getReceivedRequests(ServerPlayer player) {
        return getReceivedRequests(player.getUUID());
    }

    public static List<Request> getReceivedRequests(UUID uuid) {
        removeExpired();

        List<Request> list = receivedRequests.getOrDefault(uuid, Collections.emptyList());

        return new ArrayList<>(list);
    }

    public static boolean hasReceivedRequests(ServerPlayer player) {
        return hasReceivedRequests(player.getUUID());
    }

    public static boolean hasReceivedRequests(UUID uuid) {
        List<Request> requests = receivedRequests.get(uuid);
        if (requests == null) {
            return false;
        }

        requests.removeIf(Request::isExpired);

        return !requests.isEmpty();
    }
}
