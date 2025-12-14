package com.expecticament.helpfulcommands.helper;

import com.expecticament.helpfulcommands.manager.StylingManager;
import com.expecticament.helpfulcommands.style.HelpfulCommandsStyle;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StylingHelper {

    public static Component getAffectedEntityNameText(Entity entity) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        return Component.literal(entity.getName().getString()).setStyle(textStyles.getPrimary());
    }

    public static Component getAffectedEntitiesNumberText(List<? extends Entity> entities) {
        Map<String, Integer> map = new HashMap<>();

        int affectedCount = 0;

        for (Entity entity : entities) {
            String name = entity.getName().getString();
            int count = 1;
            if (entity instanceof ItemEntity) {
                count = ((ItemEntity) entity).getItem().getCount();
            }
            map.put(name, entity.isAlwaysTicking() ? -1 : map.getOrDefault(name, 0) + count);
            affectedCount += count;
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        MutableComponent hoverText = Component.empty();

        boolean isFirst = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!isFirst) {
                hoverText.append("\n");
            }
            boolean isPlayer = entry.getValue() == -1;
            Style entryStyle = isPlayer ? textStyles.getSecondary() : Style.EMPTY;
            if (!isPlayer) {
                hoverText.append(entry.getValue() + "x ").setStyle(entryStyle);
            }
            hoverText.append(Component.literal(entry.getKey()).setStyle(entryStyle));
            isFirst = false;
        }

        return Component.literal(String.valueOf(affectedCount)).setStyle(textStyles.getPrimary().withHoverEvent(new HoverEvent.ShowText(hoverText)));
    }

    public static Component getAffectedEntitiesNumberText(Map<? extends Entity, String> affected) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        MutableComponent hoverText = Component.empty();
        boolean isFirst = true;
        for (Map.Entry<? extends Entity, String> entry : affected.entrySet()) {
            if (!isFirst) {
                hoverText.append("\n");
            }
            Style entryStyle = entry.getKey().isAlwaysTicking() ? textStyles.getSecondary() : Style.EMPTY;
            hoverText.append(Component.literal(entry.getKey().getName().getString()).setStyle(entryStyle)).append(": ").append(entry.getValue());
            isFirst = false;
        }
        HoverEvent hoverEvent = new HoverEvent.ShowText(hoverText);
        return Component.literal(String.valueOf(affected.size())).setStyle(StylingManager.getCurrentStyle().getTextStyles().getPrimary().withHoverEvent(hoverEvent));
    }

    public static Component getTitle(Component primaryTitle, Component secondaryTitle) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        HelpfulCommandsStyle.TextDecorators textDecorators = StylingManager.getCurrentStyle().getTextDecorators();
        return Component.literal("\n")
                .append(Component.literal(textDecorators.getTitlePrefix()).setStyle(textStyles.getTitlePrimary()))
                .append(Component.literal(primaryTitle.getString()).setStyle(textStyles.getTitlePrimary()))
                .append(Component.literal(textDecorators.getTitleSeparator()).setStyle(textStyles.getTitleSecondary()))
                .append(Component.literal(secondaryTitle.getString()).setStyle(textStyles.getTitleSecondary()))
                .append(Component.literal(textDecorators.getTitleSuffix()).setStyle(textStyles.getTitlePrimary()));
    }

    public static Component getButton(Component label, HoverEvent hoverEvent, ClickEvent clickEvent) {
        return getButton("", label, hoverEvent, clickEvent);
    }

    public static Component getButton(Component label, Style style) {
        return getButton("", label, style);
    }

    public static Component getButton(String logo, Component label, HoverEvent hoverEvent, ClickEvent clickEvent) {
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();
        return getButton(logo, label, textStyles.getButton().withHoverEvent(hoverEvent).withClickEvent(clickEvent));
    }

    public static Component getButton(String logo, Component label, Style style) {
        HelpfulCommandsStyle.TextDecorators textDecorators = StylingManager.getCurrentStyle().getTextDecorators();

        MutableComponent component = Component.empty().setStyle(style);
        component.append(textDecorators.getButtonPrefix());
        if (!logo.isEmpty()) {
            component
                    .append(logo)
                    .append(textDecorators.getButtonSeparator());
        }
        component
                .append(label)
                .append(textDecorators.getButtonSuffix());

        return component;
    }

    public static Component getPositionText(double x, double y, double z) {
        return getLocationText(x, y, z, "");
    }

    public static Component getLocationText(double x, double y, double z, String dimensionKey) {
        String format = "%.2f";
        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        MutableComponent component = Component.empty();
        component
                .append(Component.literal(String.format(format, x)).setStyle(textStyles.getAxisX()))
                .append(" ")
                .append(Component.literal(String.format(format, y)).setStyle(textStyles.getAxisY()))
                .append(" ")
                .append(Component.literal(String.format(format, z)).setStyle(textStyles.getAxisZ()));
        if (!dimensionKey.isEmpty()) {
            component
                    .append(Component.literal(" (").setStyle(textStyles.getSubtle()))
                    .append(Component.literal(dimensionKey).setStyle(textStyles.getPrimary()))
                    .append(Component.literal(")").setStyle(textStyles.getSubtle()));
        }

        return component;
    }

    public static Component getLocationText(GlobalPos globalPos) {
        BlockPos blockPos = globalPos.pos();
        return getLocationText(blockPos.getX(), blockPos.getY(), blockPos.getZ(), globalPos.dimension().identifier().toString());
    }

    public static Component getLocationText(Vec3 vec3, String dimensionKey) {
        return getLocationText(vec3.x(), vec3.y(), vec3.z(), dimensionKey);
    }

    public static Component getItemStackName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        HelpfulCommandsStyle.TextStyles textStyles = StylingManager.getCurrentStyle().getTextStyles();

        return Component.literal(itemStack.getCustomName() == null ? itemStack.getItemName().getString() : itemStack.getCustomName().getString()).setStyle(textStyles.getPrimary());
    }
}
