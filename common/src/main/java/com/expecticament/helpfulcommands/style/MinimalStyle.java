package com.expecticament.helpfulcommands.style;

import net.minecraft.network.chat.Style;

public class MinimalStyle extends HelpfulCommandsStyle {
    @Override
    public String getDisplayName() {
        return "minimal";
    }

    @Override
    public TextStyles getTextStyles() {
        return new TextStyles() {
            @Override
            public Style getSuccess() {
                return Style.EMPTY;
            }

            @Override
            public Style getWarning() {
                return Style.EMPTY;
            }

            @Override
            public Style getAffectedNeutral() {
                return Style.EMPTY;
            }

            @Override
            public Style getAffectedPositive() {
                return Style.EMPTY;
            }

            @Override
            public Style getAffectedNegative() {
                return Style.EMPTY;
            }
        };
    }

    @Override
    public TextDecorators getTextDecorators() {
        return new TextDecorators() {};
    }
}