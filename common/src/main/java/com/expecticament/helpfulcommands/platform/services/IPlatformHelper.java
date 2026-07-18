package com.expecticament.helpfulcommands.platform.services;

public interface IPlatformHelper {
    enum Platform {
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

    /**
     * Gets the current platform.
     *
     * @return the current platform
     */
    Platform getPlatform();

    /**
     * Gets the mod version.
     *
     * @return the mod version
     */
    String getModVersion();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId the mod to check if it is loaded
     * @return {@code true} if the mod is loaded, {@code false} otherwise
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return {@code true} if in a development environment, {@code false} otherwise
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return the name of the environment type
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}