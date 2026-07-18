package com.expecticament.helpfulcommands.platform;

public enum Platform {
    FABRIC("Fabric"),
    NEO_FORGE("NeoForge");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
