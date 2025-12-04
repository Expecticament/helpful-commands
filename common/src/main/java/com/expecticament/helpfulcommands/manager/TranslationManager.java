package com.expecticament.helpfulcommands.manager;

import com.expecticament.helpfulcommands.HelpfulCommands;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TranslationManager {
    private static final Gson GSON = new Gson();

    private static final String FALLBACK_LANG = "en_us";

    private static final Map<String, Map<String, String>> CACHED_TRANSLATIONS = new HashMap<>();

    public static class DeprecatedTextBuilder {
        private final MutableComponent component;
        private final String language;

        @Deprecated
        public DeprecatedTextBuilder(ServerPlayer player) {
            this.component = Component.literal("");
            this.language = player != null ? player.clientInformation().language().toLowerCase() : FALLBACK_LANG;
        }

        @Deprecated
        public DeprecatedTextBuilder(CommandSourceStack commandSourceStack) {
            this(commandSourceStack.getPlayer());
        }

        public DeprecatedTextBuilder appendLiteral(String literalText) {
            return appendLiteral(literalText, Style.EMPTY);
        }

        public DeprecatedTextBuilder appendLiteral(String literalText, Style style) {
            component.append(Component.literal(literalText).setStyle(style));
            return this;
        }

        public DeprecatedTextBuilder appendTranslatable(String translationKey) {
            return appendTranslatable(translationKey, Style.EMPTY);
        }

        public DeprecatedTextBuilder appendTranslatable(String translationKey, Style style) {
            String translated = translate(language, translationKey);
            component.append(Component.literal(translated).setStyle(style));
            return this;
        }

        public DeprecatedTextBuilder appendComponent(Component component) {
            this.component.append(component);
            return this;
        }

        public DeprecatedTextBuilder appendWhitespace() {
            return appendLiteral(" ", Style.EMPTY);
        }

        public DeprecatedTextBuilder appendNewline() {
            return appendLiteral("\n", Style.EMPTY);
        }

        public DeprecatedTextBuilder setStyle(Style style) {
            component.setStyle(style);
            return this;
        }

        public MutableComponent getComponent() {
            return component;
        }
    }

    public static class TextBuilder {
        private final MutableComponent component;
        private final String language;

        public TextBuilder(ServerPlayer player) {
            this.component = Component.literal("");
            this.language = player != null ? player.clientInformation().language().toLowerCase() : FALLBACK_LANG;
        }

        public TextBuilder(CommandSourceStack commandSourceStack) {
            this(commandSourceStack.getPlayer());
        }

        public TextBuilder appendLiteral(String literalText) {
            component.append(Component.literal(literalText));
            return this;
        }

        public TextBuilder appendTranslatable(String translationKey, Component... components) {
            String translated = translate(language, translationKey);
            String[] split = translated.split("%s", -1);
            int count = 0;

            for (String str : split) {
                component.append(Component.literal(str));
                if (components != null && count < components.length) {
                    component.append(components[count]);
                    count++;
                }
            }

            return this;
        }

        public TextBuilder appendComponent(Component component) {
            this.component.append(component);
            return this;
        }

        public TextBuilder appendWhitespace() {
            return appendLiteral(" ");
        }

        public TextBuilder appendNewline() {
            return appendLiteral("\n");
        }

        public TextBuilder setStyle(Style style) {
            component.setStyle(style);
            return this;
        }

        public MutableComponent getComponent() {
            return component;
        }
    }

    public static String translate(CommandSourceStack commandSourceStack, String key) {
        ServerPlayer player = commandSourceStack.getPlayer();
        String lang = player != null ? player.clientInformation().language().toLowerCase() : FALLBACK_LANG;
        return translate(lang, key);
    }

    public static String translate(ServerPlayer player, String key) {
        String lang = player.clientInformation().language().toLowerCase();
        return translate(lang, key);
    }

    public static String translate(String lang, String key) {
        Map<String, String> langMap = readLanguageFile(lang);
        if (langMap != null) {
            String result = langMap.get(key);
            if (result != null) {
                return result;
            }
        }

        String fallback = readLanguageFile(FALLBACK_LANG).get(key);
        if (fallback != null) {
            return fallback;
        }

        return key;
    }

    public static void initialize() {
        CACHED_TRANSLATIONS.clear();
        readLanguageFile(FALLBACK_LANG);
    }

    public static Map<String, String> readLanguageFile(String lang) {
        if (CACHED_TRANSLATIONS.containsKey(lang)) {
            return CACHED_TRANSLATIONS.get(lang);
        }

        try {
            String path = "/assets/" + HelpfulCommands.MOD_ID + "/lang/" + lang + ".json";
            InputStream inputStream = HelpfulCommands.class.getResourceAsStream(path);
            Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            Map<String, String> translations = new HashMap<>();
            json.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    translations.put(entry.getKey(), entry.getValue().getAsString());
                }
            });

            CACHED_TRANSLATIONS.put(lang, translations);

            return translations;
        } catch (Exception e) {
            return lang.equals(FALLBACK_LANG) ? new HashMap<>() : readLanguageFile(FALLBACK_LANG);
        }
    }
}
