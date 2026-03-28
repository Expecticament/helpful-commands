package com.expecticament.helpful_commands.helper;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class SoundHelper {
    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent((sound).location()));
        Vec3 vec3 = player.position();
        player.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, vec3.x(), vec3.y(), vec3.z(), volume, pitch, player.level().getRandom().nextLong()));
    }
}
