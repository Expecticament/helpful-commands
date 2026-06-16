package com.expecticament.helpful_commands.manager.translation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds a {@link MutableComponent}, with support for server-side translations.
 */
public class ComponentBuilder {
    private final String lang;
    private final MutableComponent component;

    /**
     * Creates a builder for the given language code.
     *
     * @param lang the language code (e.g. {@code "en_us"})
     */
    public ComponentBuilder(String lang) {
        this.lang = lang;
        this.component = Component.literal("");
    }

    /**
     * Creates a builder localized to the given player's language,
     * or the fallback language if the player is {@code null}
     *
     * @param player the player whose language to use
     */
    public ComponentBuilder(ServerPlayer player) {
        this(resolveLang(player));
    }

    /**
     * Creates a builder localized to the player associated with the given source,
     * or the fallback language if the source is not a player (e.g. console).
     *
     * @param source the command source
     */
    public ComponentBuilder(CommandSourceStack source) {
        this(resolveLang(source.getPlayer()));
    }

    private static String resolveLang(ServerPlayer player) {
        return player != null ? player.clientInformation().language().toLowerCase() : TranslationManager.getFallbackLang();
    }

    /**
     * Appends a raw, untranslated string.
     *
     * @param text the literal text to append
     * @return this builder
     */
    public ComponentBuilder appendLiteral(String text) {
        component.append(Component.literal(text));
        return this;
    }

    /**
     * Appends a translated string, inserting {@link Component}s in place of {@code %s} placeholders.
     *
     * <p>Extra components beyond the number of placeholders are ignored.
     * If fewer components are provided, the remaining placeholders are removed.</p>
     *
     * @param key        the translation key
     * @param components the components to insert into each {@code %s} placeholder, in order
     * @return this builder
     */
    public ComponentBuilder appendTranslatable(String key, Component... components) {
        String translated = TranslationManager.translate(lang, key);
        String[] parts = translated.split("%s", -1);
        int maxComponents = parts.length - 1;

        for (int i = 0; i < parts.length; i++) {
            component.append(Component.literal(parts[i]));
            if (components != null && i < maxComponents && i < components.length) {
                component.append(components[i]);
            }
        }

        return this;
    }

    /**
     * Appends an existing {@link Component} directly.
     *
     * @param other the component to append
     * @return this builder
     */
    public ComponentBuilder appendComponent(Component other) {
        this.component.append(other);
        return this;
    }

    /**
     * Appends a single space.
     *
     * @return this builder
     */
    public ComponentBuilder appendWhitespace() {
        return appendLiteral(" ");
    }

    /**
     * Appends a newline character.
     *
     * @return this builder
     */
    public ComponentBuilder appendNewline() {
        return appendLiteral("\n");
    }

    /**
     * Applies a {@link Style} to the entire component.
     *
     * @param style the style to apply
     * @return this builder
     */
    public ComponentBuilder setStyle(Style style) {
        component.setStyle(style);
        return this;
    }

    /**
     * Returns the assembled component.
     *
     * @return the built {@link MutableComponent}
     */
    public MutableComponent build() {
        return component;
    }
}
