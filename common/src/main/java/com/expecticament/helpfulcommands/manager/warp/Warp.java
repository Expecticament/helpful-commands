package com.expecticament.helpfulcommands.manager.warp;

import com.expecticament.helpfulcommands.util.ServerLevelUtil;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;

public class Warp {
    public String description;
    public double x, y, z;
    public String dimension;

    public Warp(String description, Position position, ServerLevel level) {
        this.description = description;
        x = position.x();
        y = position.y();
        z = position.z();
        this.dimension = ServerLevelUtil.getLevelLocation(level);
    }
}
