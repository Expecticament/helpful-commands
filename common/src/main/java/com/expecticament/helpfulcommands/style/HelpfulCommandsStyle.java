package com.expecticament.helpfulcommands.style;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

public abstract class HelpfulCommandsStyle {

    public abstract String getDisplayName();
    public String getDescriptionTranslationKey() {
        return "helpful_commands.styling.style.%s.description".formatted(getDisplayName());
    }

    public abstract TextStyles getTextStyles();
    public abstract TextDecorators getTextDecorators();

    public static abstract class TextStyles {
        public Style getSuccess() {
            return Style.EMPTY.withColor(ChatFormatting.GREEN);
        }

        public Style getWarning() {
            return Style.EMPTY.withColor(ChatFormatting.YELLOW);
        }

        public Style getPrimary() {
            return Style.EMPTY.withColor(ChatFormatting.GOLD);
        }

        public Style getSecondary() {
            return Style.EMPTY.withColor(ChatFormatting.AQUA);
        }

        public Style getTertiary() {
            return Style.EMPTY.withColor(ChatFormatting.YELLOW);
        }

        public Style getSubtle() {
            return Style.EMPTY.withColor(ChatFormatting.GRAY);
        }

        public Style getDangerousAction() {
            return Style.EMPTY.withColor(ChatFormatting.RED);
        }

        public Style getButton() {
            return getPrimary();
        }

        public Style getTitlePrimary() {
            return getPrimary().withBold(true);
        }

        public Style getTitleSecondary() {
            return getSecondary();
        }

        public Style getAffectedNeutral() {
            return Style.EMPTY.withColor(ChatFormatting.YELLOW);
        }

        public Style getAffectedPositive() {
            return Style.EMPTY.withColor(ChatFormatting.GREEN);
        }

        public Style getAffectedNegative() {
            return Style.EMPTY.withColor(ChatFormatting.RED);
        }

        public Style getAvailable() {
            return Style.EMPTY.withColor(ChatFormatting.GREEN);
        }

        public Style getUnavailable() {
            return Style.EMPTY.withColor(ChatFormatting.RED);
        }

        public Style getEnabled() {
            return Style.EMPTY.withColor(ChatFormatting.GREEN);
        }

        public Style getDisabled() {
            return Style.EMPTY.withColor(ChatFormatting.RED);
        }

        public Style getAxisX() {
            return Style.EMPTY.withColor(ChatFormatting.RED);
        }

        public Style getAxisY() {
            return Style.EMPTY.withColor(ChatFormatting.GREEN);
        }

        public Style getAxisZ() {
            return Style.EMPTY.withColor(ChatFormatting.BLUE);
        }
    }

    public abstract static class TextDecorators {
        public String getTitlePrefix() {
            return "« ";
        }

        public String getTitleSeparator() {
            return " || ";
        }

        public String getTitleSuffix() {
            return " »";
        }

        public String getBulletPoint() {
            return "• ";
        }

        public String getSeparator() {
            return " | ";
        }

        public String getButtonPrefix() {
            return "[";
        }

        public String getButtonSuffix() {
            return "]";
        }

        public String getButtonSeparator() {
            return " ";
        }

        public String getCross() {
            return "❌";
        }

        public String getCheckmark() {
            return "✔";
        }

        public String getRemove() {
            return "\uD83D\uDDD1";
        }

        public String getEdit() {
            return "\uD83D\uDD8D";
        }

        public String getReset() {
            return "⬅";
        }

        public String getTeleport() {
            return "➡";
        }

        public String getCategoryStartingChar() {
            return "┏";
        }

        public String getCategoryTrailingChar() {
            return "┠› ";
        }

        public String getCategoryEndingChar() {
            return "┗› ";
        }
    }
}
