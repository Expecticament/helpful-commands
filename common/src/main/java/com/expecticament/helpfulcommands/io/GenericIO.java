package com.expecticament.helpfulcommands.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class GenericIO {

    private final Path fullPath;
    private byte[] cachedData = null;

    public GenericIO(Path directoryPath, String fileName, String fileExtension) {
        fullPath = directoryPath.resolve(fileName + fileExtension);
    }

    public void save(byte[] data) {
        cachedData = data;
    }

    public void writeToDisk() {
        if (cachedData == null) {
            return;
        }
        writeToDisk(cachedData);
    }

    public void writeToDisk(byte[] data) {
        if (data == null) {
            return;
        }
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, data);
            cachedData = Arrays.copyOf(data, data.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] read() {
        if (cachedData != null) {
            return cachedData;
        }

        try {
            if (!Files.exists(fullPath)) {
                return null;
            }
            cachedData = Files.readAllBytes(fullPath);
            return cachedData;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}