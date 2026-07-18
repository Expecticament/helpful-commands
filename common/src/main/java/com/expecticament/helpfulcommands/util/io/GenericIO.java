package com.expecticament.helpfulcommands.util.io;

import com.expecticament.helpfulcommands.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A utility class for managing basic File I/O operations with a memory buffer.
 * This class allows for storing data in memory before writing it to disk.
 */
public class GenericIO {
    private final Path path;
    private byte[] buffer = null;

    /**
     * Creates a new GenericIO handler.
     *
     * @param directoryPath  The directory where the file is stored.
     * @param fileName       The name of the file (without extension).
     * @param fileExtension  The file extension (with or without ".").
     */
    public GenericIO(Path directoryPath, String fileName, String fileExtension) {
        String extension = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        path = directoryPath.resolve(fileName + extension);
    }

    /**
     * Updates the memory buffer without writing to disk.
     *
     * @param data The byte array to store in the buffer.
     */
    public void updateBuffer(byte[] data) {
        buffer = data;
    }

    /**
     * Returns the cached memory buffer if available;
     * otherwise, attempts to read the data from disk and cache it.
     *
     * @return The data as a byte array, or {@code null} if the file does not exist
     * or an error occurs during reading.
     */
    public byte[] getData() {
        if (buffer != null) {
            return buffer;
        }

        if (!Files.exists(path)) {
            return null;
        }

        try {
            buffer = Files.readAllBytes(path);
            return buffer;
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to read from {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Writes the current memory buffer to disk. Does nothing if the buffer is {@code null}.
     */
    public void flush() {
        if (buffer != null) {
            flush(buffer);
        }
    }

    /**
     * Writes the provided data directly to disk and updates the memory buffer.
     * Creates any necessary parent directories automatically.
     *
     * @param data The byte array to write to the file.
     */
    public void flush(byte[] data) {
        if (data == null) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            buffer = Arrays.copyOf(data, data.length);
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to flush to {}: {}", path, e.getMessage());
        }
    }

    /**
     * Returns the absolute path of the file handled by this instance.
     *
     * @return The {@link Path} pointing to the target file.
     */
    public Path getPath() {
        return path;
    }
}