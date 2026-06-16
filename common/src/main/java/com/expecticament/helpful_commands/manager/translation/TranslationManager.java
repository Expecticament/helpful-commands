package com.expecticament.helpful_commands.manager.translation;

import com.expecticament.helpful_commands.HelpfulCommands;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles server-side translations by reading the mod's language files.
 *
 * <p>Language files are loaded from {@code assets/helpful_commands/lang/}
 * and cached after the first load.
 */
public class TranslationManager {
    private static final Gson GSON = new Gson();
    private static final String FALLBACK_LANG = "en_us";
    private static final Map<String, Map<String, String>> TRANSLATION_CACHE = new HashMap<>();

    /**
     * Looks up a translation key for the given language code.
     * Falls back to {@value #FALLBACK_LANG} if the key is missing from the
     * requested language, and returns {@code key} itself if it can't be found anywhere.
     *
     * @param lang the language code (e.g. {@code "en_us"})
     * @param key  the translation key to look up
     * @return the translated string, or {@code key} if not found anywhere
     */
    public static String translate(String lang, String key) {
        String translated = loadLanguage(lang).get(key);
        if (translated != null) return translated;

        String fallback = loadLanguage(FALLBACK_LANG).get(key);
        if (fallback != null) return fallback;

        return key;
    }

    /**
     * Looks up a translation key using the given player's client language.
     *
     * @param player the player whose language to use
     * @param key    the translation key to look up
     * @return the translated string, or {@code key} if not found anywhere
     */
    public static String translate(ServerPlayer player, String key) {
        return translate(player.clientInformation().language().toLowerCase(), key);
    }

    /**
     * Looks up a translation key using the language of the player associated
     * with the given source, or {@value #FALLBACK_LANG} if the source is not
     * a player (e.g. console).
     *
     * @param source the command source
     * @param key    the translation key to look up
     * @return the translated string, or {@code key} if not found anywhere
     */
    public static String translate(CommandSourceStack source, String key) {
        ServerPlayer player = source.getPlayer();
        return player != null ? translate(player, key) : translate(FALLBACK_LANG, key);
    }

    /**
     * Returns the language code used as a fallback.
     *
     * @return the fallback language code
     */
    public static String getFallbackLang() {
        return FALLBACK_LANG;
    }

    /**
     * Clears the translation cache and loads the fallback language file.
     */
    public static void initialize() {
        TRANSLATION_CACHE.clear();
        loadLanguage(FALLBACK_LANG);
    }

    /**
     * Returns the translation map for the given language, loading and caching
     * it from disk if not already loaded. If the language file doesn't exist
     * or can't be read, falls back to {@value #FALLBACK_LANG}. Returns an
     * empty map if even the fallback fails.
     *
     * @param lang the language code to load
     * @return the translation map for that language
     */
    private static Map<String, String> loadLanguage(String lang) {
        Map<String, String> cached = TRANSLATION_CACHE.get(lang);
        if (cached != null) return cached;

        String path = "/assets/%s/lang/%s.json".formatted(HelpfulCommands.MOD_ID, lang);
        try (InputStream stream = HelpfulCommands.class.getResourceAsStream(path);
             Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            Map<String, String> translations = new HashMap<>();
            json.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    translations.put(entry.getKey(), entry.getValue().getAsString());
                }
            });

            TRANSLATION_CACHE.put(lang, translations);
            return translations;

        } catch (Exception e) {
            return lang.equals(FALLBACK_LANG) ? new HashMap<>() : loadLanguage(FALLBACK_LANG);
        }
    }
}
