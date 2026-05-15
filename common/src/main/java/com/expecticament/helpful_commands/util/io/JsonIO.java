package com.expecticament.helpful_commands.util.io;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A utility for handling JSON serialization and deserialization.
 * This class wraps {@link GenericIO} to provide buffered I/O operations for JSON data.
 *
 * @param <T> The type of data to be handled by this IO instance.
 */
public class JsonIO<T> {
    private final GenericIO genericIO;
    private final Gson gson;
    private final Class<T> typeClass;

    /**
     * Creates a new JsonIO handler using default GSON settings.
     *
     * @param directoryPath The directory where the JSON file is stored.
     * @param fileName      The name of the file (without extension).
     * @param typeClass     The class type of the data being handled.
     */
    public JsonIO(Path directoryPath, String fileName, Class<T> typeClass) {
        this(directoryPath, fileName, typeClass, null);
    }

    /**
     * Creates a new JsonIO handler with a custom GSON configuration.
     * <p>
     * Use the {@code builderConfig} consumer to register type adapters,
     * set exclusion strategies, or modify other GSON settings.
     *
     * @param directoryPath The directory where the JSON file is stored.
     * @param fileName      The name of the file (without extension).
     * @param typeClass     The class type of the data being handled.
     * @param builderConfig A {@link java.util.function.Consumer} that configures the {@link GsonBuilder}.
     * Can be {@code null} to use default settings.
     */
    public JsonIO(Path directoryPath, String fileName, Class<T> typeClass, Consumer<GsonBuilder> builderConfig) {
        this.genericIO = new GenericIO(directoryPath, fileName, ".json");
        this.typeClass = typeClass;

        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();

        if (builderConfig != null) {
            builderConfig.accept(builder);
        }

        this.gson = builder.create();
    }

    /**
     * Serializes the provided data to JSON and updates the memory buffer
     * without writing to disk.
     *
     * @param data The object to serialize and store in the buffer.
     */
    public void updateBuffer(T data) {
        try {
            String json = gson.toJson(data);
            genericIO.updateBuffer(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            HelpfulCommands.LOGGER.error("Failed to update JSON buffer for {}: {}", genericIO.getPath(), e.getMessage());
        }
    }

    /**
     * Writes the current memory buffer to disk as a JSON file.
     */
    public void flush() {
        genericIO.flush();
    }

    /**
     * Serializes the provided data, writes it to disk and updates the buffer.
     *
     * @param data The object to serialize and write.
     */
    public void flush(T data) {
        try {
            String json = gson.toJson(data);
            genericIO.flush(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            HelpfulCommands.LOGGER.error("Failed to flush JSON to {}: {}", genericIO.getPath(), e.getMessage());
        }
    }

    /**
     * Reads data from the buffer or disk and deserializes it from JSON.
     *
     * @return The deserialized object, or {@code null} if the data could not be read or parsed.
     */
    public T getData() {
        try {
            byte[] bytes = genericIO.getData();
            if (bytes == null) {
                return null;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            return gson.fromJson(json, typeClass);
        } catch (Exception e) {
            HelpfulCommands.LOGGER.error("Failed to parse JSON from {}: {}", genericIO.getPath(), e.getMessage());
            return null;
        }
    }
}
