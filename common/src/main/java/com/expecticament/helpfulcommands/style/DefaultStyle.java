package com.expecticament.helpfulcommands.style;

public class DefaultStyle extends HelpfulCommandsStyle {
    @Override
    public String getDisplayName() {
        return "default";
    }

    @Override
    public TextStyles getTextStyles() {
        return new TextStyles() {};
    }

    @Override
    public TextDecorators getTextDecorators() {
        return new TextDecorators() {};
    }
}