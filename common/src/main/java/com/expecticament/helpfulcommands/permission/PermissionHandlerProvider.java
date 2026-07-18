package com.expecticament.helpfulcommands.permission;

public class PermissionHandlerProvider {
    public static PermissionHandler instance;

    public static PermissionHandler get() {
        if (instance == null) {
            throw new IllegalStateException("PermissionHandler not initialized.");
        }
        return instance;
    }
}