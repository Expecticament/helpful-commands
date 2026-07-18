package com.expecticament.helpfulcommands.platform.services;

import com.expecticament.helpfulcommands.platform.Platform;

public interface IPlatformHelper {

    /**
     * Gets the current platform.
     *
     * @return The current platform.
     */
    Platform getPlatform();

    /**
     * Gets the mod version.
     *
     * @return The mod version.
     */
    String getModVersion();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return {@code true} if the mod is loaded, {@code false} otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return {@code true} if in a development environment, {@code false} otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}