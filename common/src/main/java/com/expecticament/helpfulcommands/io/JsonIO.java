package com.expecticament.helpfulcommands.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class JsonIO<T> {

    private final GenericIO genericIO;
    private final Gson gson;
    private final Class<T> typeClass;

    public JsonIO(Path directoryPath, String fileName, Class<T> typeClass) {
        this.genericIO = new GenericIO(directoryPath, fileName, ".json");
        this.typeClass = typeClass;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void save(T data) {
        try {
            String json = gson.toJson(data);
            genericIO.save(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeToDisk() {
        genericIO.writeToDisk();
    }

    public void writeToDisk(T data) {
        try {
            String json = gson.toJson(data);
            genericIO.writeToDisk(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public T read() {
        try {
            byte[] bytes = genericIO.read();
            if (bytes == null) {
                return null;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            return gson.fromJson(json, typeClass);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
