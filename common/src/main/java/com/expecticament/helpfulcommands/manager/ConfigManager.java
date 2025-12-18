package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.io.JsonIO;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfigManager {
    private static final String FILE_NAME = "config";

    private static JsonIO<HelpfulCommandsConfig> io;

    public static class HelpfulCommandsConfig {
        public CommandConfig command = new CommandConfig();
        public StylingConfig styling = new StylingConfig();
        protected final EnumMap<CONFIG_FIELD, Object> fields = new EnumMap<>(CONFIG_FIELD.class);

        public HelpfulCommandsConfig() {
            for (CONFIG_FIELD field : CONFIG_FIELD.values()) {
                fields.putIfAbsent(field, field.properties().defaultValue);
            }
        }

        public void writeField(CONFIG_FIELD configField, Object value) {
            fields.put(configField, value);
            io.save(this);
        }

        public void writeField(String configField, Object value) {
            fields.put(CONFIG_FIELD.valueOf(configField.toUpperCase()), value);
            io.save(this);
        }

        public <T> T readField(String configField) {
            return readField(CONFIG_FIELD.valueOf(configField.toUpperCase()));
        }

        @SuppressWarnings("unchecked")
        public <T> T readField(CONFIG_FIELD configField) {
            Object value = fields.get(configField);
            ConfigFieldProperties properties = configField.properties();

            if (value == null) {
                value = properties.defaultValue;
                fields.put(configField, value);
            }

            switch (properties.valueType) {
                case Integer -> {
                    if (!(value instanceof Number num)) {
                        throw invalidType(configField, "Integer", value);
                    }
                    int result = (int) Math.clamp(num.intValue(), properties.min.intValue(), properties.max.intValue());
                    return (T) Integer.valueOf(result);
                }

                case Double -> {
                    if (!(value instanceof Number num)) {
                        throw invalidType(configField, "Double", value);
                    }
                    double result = Math.clamp(num.doubleValue(), properties.min, properties.max);
                    return (T) Double.valueOf(result);
                }

                case Boolean -> {
                    if (!(value instanceof Boolean bool)) {
                        throw invalidType(configField, "Boolean", value);
                    }
                    return (T) bool;
                }
            }

            throw new IllegalStateException("Unknown valueType for field '" + configField.name().toLowerCase() + "'");
        }

        private IllegalStateException invalidType(CONFIG_FIELD configField, String expected, Object actual) {
            return new IllegalStateException(
                    "Config field '" + configField.name().toLowerCase() + "' expected type " + expected + " but found " + actual.getClass().getSimpleName()
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

        private final ValueType valueType;
        private final Object defaultValue;

        private final Double min;
        private final Double max;

        private static ConfigFieldProperties dbl(double defaultValue) {
            return new ConfigFieldProperties(ValueType.Double, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        private static ConfigFieldProperties dbl(double defaultValue, double min) {
            return new ConfigFieldProperties(ValueType.Double, defaultValue, min, Double.POSITIVE_INFINITY);
        }

        private static ConfigFieldProperties dbl(double defaultValue, double min, double max) {
            return new ConfigFieldProperties(ValueType.Double, defaultValue, min, max);
        }

        private static ConfigFieldProperties integer(int defaultValue) {
            return new ConfigFieldProperties(ValueType.Integer, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        private static ConfigFieldProperties integer(int defaultValue, int min) {
            return new ConfigFieldProperties(ValueType.Integer, defaultValue, min, Integer.MAX_VALUE);
        }

        private static ConfigFieldProperties integer(int defaultValue, int min, int max) {
            return new ConfigFieldProperties(ValueType.Integer, defaultValue, min, max);
        }

        private static ConfigFieldProperties bool(boolean defaultValue) {
            return new ConfigFieldProperties(ValueType.Boolean, defaultValue);
        }

        private ConfigFieldProperties(ValueType valueType, Object defaultValue) {
            this(valueType, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        private ConfigFieldProperties(ValueType valueType, Object defaultValue, double min, double max) {
            this.valueType = valueType;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;

            switch (valueType) {
                case Double -> {
                    if (!(defaultValue instanceof Double)) {
                        throw new IllegalArgumentException("Default value must be Double");
                    }
                }
                case Integer -> {
                    if (!(defaultValue instanceof Integer)) {
                        throw new IllegalArgumentException("Default value must be Integer");
                    }
                }
                case Boolean -> {
                    if (!(defaultValue instanceof Boolean)) {
                        throw new IllegalArgumentException("Default value must be Boolean");
                    }
                }
            }
        }

        public ValueType getValueType() {
            return valueType;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public Double getMin() {
            return min;
        }

        public Double getMax() {
            return max;
        }
    }

    public enum CONFIG_FIELD {
        MAX_HOMES(ConfigFieldProperties.integer(5, 1)),
        HOME_TP_COOLDOWN(ConfigFieldProperties.integer(0, 0)),

        TPR_REQUEST_TIMEOUT(ConfigFieldProperties.integer(60, 1)),
        TPR_REQUEST_COOLDOWN_ON_ACCEPTED(ConfigFieldProperties.integer(0, 0)),
        TPR_REQUEST_COOLDOWN_ON_CANCEL(ConfigFieldProperties.integer(0, 0)),

        EXPLOSION_POWER_LIMIT(ConfigFieldProperties.integer(5, 1)),

        FIREBALL_POWER_LIMIT(ConfigFieldProperties.integer(5, 1)),

        KILLITEMS_MAX_RANGE(ConfigFieldProperties.integer(128, 1));

        private final ConfigFieldProperties properties;

        CONFIG_FIELD(ConfigFieldProperties properties) {
            this.properties = properties;
        }

        public ConfigFieldProperties properties() {
            return properties;
        }
    }

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
