package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.io.JsonIO;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfigManager {

    private static final String FILE_NAME = "config";

    private static JsonIO<HelpfulCommandsConfig> io;

    public static class HelpfulCommandsConfig {
        public final CommandConfig command = new CommandConfig();
        public final StylingConfig styling = new StylingConfig();
        private final Map<String, Object> fields = new HashMap<>();

        public HelpfulCommandsConfig() {
            for (var entry : DEFAULT_FIELDS.entrySet()) {
                fields.putIfAbsent(entry.getKey(), entry.getValue().defaultValue);
            }
        }

        public void writeField(String key, Object value) {
            fields.put(key, value);
            io.save(this);
        }

        @SuppressWarnings("unchecked")
        public <T> T readField(String key) {
            Object value = fields.get(key);

            ConfigFieldProperties properties = DEFAULT_FIELDS.get(key);
            if (properties == null) {
                return null;
            }

            if (value == null) {
                value = properties.defaultValue;
                fields.put(key, value);
            }

            switch (properties.valueType) {
                case Integer -> {
                    if (!(value instanceof Number num)) {
                        throw invalidType(key, "Integer", value);
                    }
                    int result = (int) Math.clamp(num.intValue(), properties.min.intValue(), properties.max.intValue());
                    return (T) Integer.valueOf(result);
                }

                case Double -> {
                    if (!(value instanceof Number num)) {
                        throw invalidType(key, "Double", value);
                    }
                    double result = Math.clamp(num.doubleValue(), properties.min, properties.max);
                    return (T) Double.valueOf(result);
                }

                case Boolean -> {
                    if (!(value instanceof Boolean bool)) {
                        throw invalidType(key, "Boolean", value);
                    }
                    return (T) bool;
                }
            }

            throw new IllegalStateException("Unknown valueType for field '" + key + "'");
        }

        private IllegalStateException invalidType(String key, String expected, Object actual) {
            return new IllegalStateException(
                    "Config field '" + key + "' expected type " + expected + " but found " + actual.getClass().getSimpleName()
            );
        }

        public boolean setCommandState(String commandName, boolean newState) {
            CommandConfigEntry entry = command.commands.getOrDefault(commandName, new CommandConfigEntry());
            if (entry.state == newState) {
                return false;
            }

            entry.state = newState;
            command.commands.put(commandName, entry);
            io.save(this);

            return true;
        }

        public boolean getCommandState(String commandName) {
            CommandConfigEntry entry = command.commands.getOrDefault(commandName, new CommandConfigEntry());
            return entry.state;
        }

        public void setStyle(String styleName) {
            styling.style = styleName;
            io.save(this);
        }

        public String getStyleName() {
            return styling.style;
        }
    }

    public static class CommandConfig {
        private final Map<String, CommandConfigEntry> commands = new HashMap<>();
    }

    private static class CommandConfigEntry {
        private boolean state;
    }

    public static class StylingConfig {
        private String style;
    }

    public static class ConfigFieldProperties {
        public enum ValueType { Double, Integer, Boolean }

        public final ValueType valueType;
        public final Object defaultValue;

        public final Double min;
        public final Double max;

        public ConfigFieldProperties(ValueType valueType, Object defaultValue) {
            this(valueType, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        public ConfigFieldProperties(ValueType valueType, Object defaultValue, double min) {
            this(valueType, defaultValue, min, Double.POSITIVE_INFINITY);
        }

        public ConfigFieldProperties(ValueType valueType, Object defaultValue, double min, double max) {
            this.valueType = valueType;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
        }
    }

    public static final Map<String, ConfigFieldProperties> DEFAULT_FIELDS = Map.ofEntries(
            Map.entry("killitemsMaxRange", new ConfigFieldProperties(ConfigFieldProperties.ValueType.Integer, 128, 1)),
            Map.entry("maxHomes", new ConfigFieldProperties(ConfigFieldProperties.ValueType.Integer, 5, 1))
    );

    public static void initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(HelpfulCommands.FOLDER_NAME), FILE_NAME, HelpfulCommandsConfig.class);

        HelpfulCommandsConfig config = readConfig();

        for (HelpfulCommandsCommand command : ModCommandManager.getCommandList()) {
            ModCommandManager.CommandData data = command.getCommandData();
            if (data.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                continue;
            }
            CommandConfigEntry entry = new CommandConfigEntry();
            entry.state = data.isEnabledByDefault();
            config.command.commands.putIfAbsent(data.getName(), entry);
        }

        io.save(config);
    }

    public static HelpfulCommandsConfig readConfig() {
        return Objects.requireNonNullElse(io.read(), new HelpfulCommandsConfig());
    }

    public static void writeToDisk() {
        io.writeToDisk();
    }
}
