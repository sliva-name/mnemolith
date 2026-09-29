package com.mnemolith.client.audio;

import com.mnemolith.audio.ModSounds;
import com.mnemolith.entity.echo.ScarEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side Scar fight music. Storm gathering music is started from the server cue
 * ({@code ModSounds.MUSIC_STORM_GATHERING} played at storm start).
 */
public final class SituationalMusic {
    private static final int SCAR_RANGE = 48;
    private static SoundInstance current;
    private static int cooldown;

    private SituationalMusic() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SituationalMusic::onTick);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            stop(mc);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (!nearScar(mc)) {
            stop(mc);
            return;
        }
        if (current != null && mc.getSoundManager().isActive(current)) {
            return;
        }
        if (cooldown > 0) {
            return;
        }
        play(mc, ModSounds.MUSIC_SCAR_FIGHT.get());
    }

    private static boolean nearScar(Minecraft mc) {
        AABB box = mc.player.getBoundingBox().inflate(SCAR_RANGE);
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof ScarEntity && e.isAlive())) {
            if (mc.player.distanceToSqr(entity) <= (double) SCAR_RANGE * SCAR_RANGE) {
                return true;
            }
        }
        return false;
    }

    private static void play(Minecraft mc, SoundEvent event) {
        stop(mc);
        SoundInstance instance = new SimpleSoundInstance(
                event.location(),
                SoundSource.MUSIC,
                0.55F,
                1.0F,
                SoundInstance.createUnseededRandom(),
                true,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true);
        mc.getSoundManager().play(instance);
        current = instance;
        cooldown = 20 * 10;
    }

    private static void stop(Minecraft mc) {
        if (current != null) {
            mc.getSoundManager().stop(current);
            current = null;
        }
    }
}
