package com.expecticament.helpful_commands.manager.home;

import com.expecticament.helpful_commands.util.ServerLevelUtil;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;

public class Home {
    public double x, y, z;
    public String dimension;

    public Home(Position position, ServerLevel serverLevel) {
        x = position.x();
        y = position.y();
        z = position.z();
        this.dimension = ServerLevelUtil.getLevelLocation(serverLevel);
    }
}
