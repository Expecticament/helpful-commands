package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.Constants;
import com.expecticament.helpfulcommands.command.HelpfulCommandsCommand;
import com.expecticament.helpfulcommands.util.io.JsonIO;
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
        protected final EnumMap<CONFIG_FIELD, Object> fields = new EnumMap<>(CONFIG_FIELD.class);

        public HelpfulCommandsConfig() {
            for (CONFIG_FIELD field : CONFIG_FIELD.values()) {
                fields.putIfAbsent(field, field.properties().defaultValue);
            }
        }

        public void writeField(CONFIG_FIELD configField, Object value) {
            fields.put(configField, value);
            io.updateBuffer(this);
        }

        public void writeField(String configField, Object value) {
            fields.put(CONFIG_FIELD.valueOf(configField.toUpperCase()), value);
            io.updateBuffer(this);
        }

        public <T> T readField(String configField) {
            return readField(CONFIG_FIELD.valueOf(configField.toUpperCase()));
        }

        public <T> T readField(CONFIG_FIELD configField) {
            return validateFieldValue(configField, fields.get(configField));
        }

        @SuppressWarnings("unchecked")
        public <T> T validateFieldValue(CONFIG_FIELD configField, Object value) {
            ConfigFieldProperties properties = configField.properties();

            if (value == null) {
                value = properties.defaultValue;
            }

            return switch (properties.valueType) {
                case Integer -> {
                    int intValue;

                    if (value instanceof Integer i) {
                        intValue = i;
                    } else if (value instanceof Number n) {
                        intValue = n.intValue();
                    } else {
                        intValue = Integer.parseInt(value.toString());
                    }

                    intValue = Math.clamp(intValue, properties.min.intValue(), properties.max.intValue());

                    yield (T) Integer.valueOf(intValue);
                }
                case Double -> {
                    double doubleValue;

                    if (value instanceof Double d) {
                        doubleValue = d;
                    } else if (value instanceof Number n) {
                        doubleValue = n.doubleValue();
                    } else {
                        doubleValue = Double.parseDouble(value.toString());
                    }

                    doubleValue = Math.clamp(doubleValue, properties.min, properties.max);

                    yield (T) Double.valueOf(doubleValue);
                }
                case Boolean -> {
                    boolean boolValue;

                    if (value instanceof Boolean b) {
                        boolValue = b;
                    } else {
                        boolValue = Boolean.parseBoolean(value.toString());
                    }

                    yield (T) Boolean.valueOf(boolValue);
                }
            };
        }

        public boolean setCommandState(String commandName, boolean newState) {
            CommandConfigEntry entry = command.commands.getOrDefault(commandName, new CommandConfigEntry());
            if (entry.state == newState) {
                return false;
            }

            entry.state = newState;
            command.commands.put(commandName, entry);
            io.updateBuffer(this);

            return true;
        }

        public boolean getCommandState(String commandName) {
            CommandConfigEntry entry = command.commands.getOrDefault(commandName, new CommandConfigEntry());
            return entry.state;
        }
    }

    public static class CommandConfig {
        private final Map<String, CommandConfigEntry> commands = new HashMap<>();
    }

    private static class CommandConfigEntry {
        private boolean state;
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
        MAX_HOMES(ConfigFieldProperties.integer(5, 1), true),
        HOME_TP_COOLDOWN(ConfigFieldProperties.integer(0, 0), true),

        TPR_REQUEST_TIMEOUT(ConfigFieldProperties.integer(60, 1), false),
        TPR_REQUEST_COOLDOWN_ON_ACCEPTED(ConfigFieldProperties.integer(0, 0), true),
        TPR_REQUEST_COOLDOWN_ON_CANCEL(ConfigFieldProperties.integer(0, 0), true),

        EXPLOSION_POWER_LIMIT(ConfigFieldProperties.integer(5, 1), true),

        FIREBALL_POWER_LIMIT(ConfigFieldProperties.integer(5, 1), true),

        JUMP_DISTANCE_LIMIT(ConfigFieldProperties.dbl(2048, 0.1), true),

        KILLITEMS_MAX_RANGE(ConfigFieldProperties.integer(256, 1), true);

        private final ConfigFieldProperties properties;
        private final boolean lpMetaSupport;

        CONFIG_FIELD(ConfigFieldProperties properties, boolean lpMetaSupport) {
            this.properties = properties;
            this.lpMetaSupport = lpMetaSupport;
        }

        public ConfigFieldProperties properties() {
            return properties;
        }

        public boolean lpMetaSupport() {
            return lpMetaSupport;
        }
    }

    public static void initialize(MinecraftServer server) {
        io = new JsonIO<>(server.getWorldPath(LevelResource.ROOT).resolve(Constants.FOLDER_NAME), FILE_NAME, HelpfulCommandsConfig.class, builder -> {
            builder.registerTypeAdapter(HelpfulCommandsConfig.class, new CustomConfigDeserializer());
        });

        HelpfulCommandsConfig config = readConfig();

        for (HelpfulCommandsCommand command : ModCommandManager.getCommandList()) {
            ModCommandManager.ModCommand modCommand = command.getModCommand();
            if (modCommand.getCategory() == ModCommandManager.CommandCategory.MAIN) {
                continue;
            }
            CommandConfigEntry entry = new CommandConfigEntry();
            entry.state = modCommand.isEnabledByDefault();
            config.command.commands.putIfAbsent(modCommand.getName(), entry);
        }

        io.updateBuffer(config);
    }

    public static HelpfulCommandsConfig readConfig() {
        return Objects.requireNonNullElse(io.getData(), new HelpfulCommandsConfig());
    }

    public static void writeToDisk() {
        io.flush();
    }
}
