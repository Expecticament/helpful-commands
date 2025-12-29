package com.expecticament.helpfulcommands.manager;

import java.util.*;

import com.expecticament.helpfulcommands.style.*;

public class StylingManager {
    private static final List<HelpfulCommandsStyle> styleList = new ArrayList<>();
    private static HelpfulCommandsStyle currentStyle;

    public static class StyleDoesntExistException extends Exception {
        public StyleDoesntExistException(String styleName) {
            super("%s: style doesn't exist!".formatted(styleName));
        }
    };

    public static void initialize() {
        DefaultStyle defaultStyle = new DefaultStyle();
        styleList.add(defaultStyle);

        try {
            setCurrentStyle(defaultStyle.getDisplayName());
        } catch (StyleDoesntExistException ignored) {}
    }

    public static HelpfulCommandsStyle getStyle(String styleName) throws StyleDoesntExistException {
        for (HelpfulCommandsStyle style : styleList) {
            if (style.getDisplayName().equals(styleName)) {
                return style;
            }
        }

        throw new StyleDoesntExistException(styleName);
    }

    public static HelpfulCommandsStyle getCurrentStyle() {
        return currentStyle;
    }

    public static HelpfulCommandsStyle getStyleByName(String styleName) {
        for (HelpfulCommandsStyle style : styleList) {
            if (style.getDisplayName().equals(styleName)) {
                return style;
            }
        }

        return null;
    }

    public static List<HelpfulCommandsStyle> getAllStyles() {
        return new ArrayList<>(styleList);
    }

    public static void setCurrentStyle(String styleName) throws StyleDoesntExistException {
        currentStyle = getStyle(styleName);
    }
}