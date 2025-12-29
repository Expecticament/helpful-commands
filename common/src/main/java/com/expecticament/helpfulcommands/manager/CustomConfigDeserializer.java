package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Map;

public class CustomConfigDeserializer implements JsonDeserializer<ConfigManager.HelpfulCommandsConfig> {

    @Override
    public ConfigManager.HelpfulCommandsConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        ConfigManager.HelpfulCommandsConfig config = new ConfigManager.HelpfulCommandsConfig();

        JsonElement commandElem = obj.get("command");
        if (commandElem != null) {
            config.command = context.deserialize(commandElem, ConfigManager.CommandConfig.class);
        }

        JsonElement fieldsElem = obj.get("fields");
        if (fieldsElem != null && fieldsElem.isJsonObject()) {
            JsonObject fieldsObj = fieldsElem.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : fieldsObj.entrySet()) {
                String keyStr = entry.getKey().toUpperCase();
                try {
                    ConfigManager.CONFIG_FIELD key = ConfigManager.CONFIG_FIELD.valueOf(keyStr);
                    Object value = context.deserialize(entry.getValue(), Object.class);
                    config.fields.put(key, value);
                } catch (IllegalArgumentException e) {
                    HelpfulCommands.LOGGER.warn("Ignoring unknown config field: " + keyStr);
                }
            }
        }

        return config;
    }
}