package com.mnemolith.echo;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.imprint.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Stage 3 echo upgrades a player has absorbed, saved on the player (kept through death) and synced to that player
 * for tooltips. {@code chorus} raises how many echoes one may own, {@code longTake} lengthens a recording,
 * {@code sturdy} gives echo bodies more health.
 */
public record EchoProgress(int chorus, int longTake, int sturdy) {
    public static final EchoProgress NONE = new EchoProgress(0, 0, 0);
    public static final MapCodec<EchoProgress> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("chorus", 0).forGetter(EchoProgress::chorus),
            Codec.INT.optionalFieldOf("long_take", 0).forGetter(EchoProgress::longTake),
            Codec.INT.optionalFieldOf("sturdy", 0).forGetter(EchoProgress::sturdy))
            .apply(instance, EchoProgress::new));
    public static final StreamCodec<ByteBuf, EchoProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EchoProgress::chorus,
            ByteBufCodecs.VAR_INT, EchoProgress::longTake,
            ByteBufCodecs.VAR_INT, EchoProgress::sturdy,
            EchoProgress::new);

    public enum Kind {
        CHORUS,
        LONG_TAKE,
        STURDY
    }

    public boolean isEmpty() {
        return this.chorus == 0 && this.longTake == 0 && this.sturdy == 0;
    }

    public int level(Kind kind) {
        return switch (kind) {
            case CHORUS -> this.chorus;
            case LONG_TAKE -> this.longTake;
            case STURDY -> this.sturdy;
        };
    }

    public EchoProgress with(Kind kind, int level) {
        return switch (kind) {
            case CHORUS -> new EchoProgress(level, this.longTake, this.sturdy);
            case LONG_TAKE -> new EchoProgress(this.chorus, level, this.sturdy);
            case STURDY -> new EchoProgress(this.chorus, this.longTake, level);
        };
    }

    /** Highest level of {@code kind} with the current config. */
    public static int maxLevel(Kind kind) {
        return switch (kind) {
            case CHORUS -> Math.max(0, CommonConfig.ECHO_MAX_PER_PLAYER_CAP.get() - CommonConfig.ECHO_MAX_PER_PLAYER.get());
            case LONG_TAKE -> CommonConfig.ECHO_RECORD_UPGRADE_MAX.get();
            case STURDY -> CommonConfig.ECHO_STURDY_UPGRADE_MAX.get();
        };
    }

    public static EchoProgress of(Player player) {
        return player.getData(ModAttachments.ECHO_PROGRESS.get());
    }

    /** Echoes this player may own at once: the base limit, raised by chorus up to the cap (never below the base). */
    public static int echoLimit(Player player) {
        int base = CommonConfig.ECHO_MAX_PER_PLAYER.get();
        int cap = CommonConfig.ECHO_MAX_PER_PLAYER_CAP.get();
        return Math.max(base, Math.min(cap, base + of(player).chorus()));
    }

    /** Frames of one recording for this player (at most {@link EchoRecording#MAX_FRAMES}, one minute). */
    public static int recordFrames(Player player) {
        int levels = Math.min(of(player).longTake(), CommonConfig.ECHO_RECORD_UPGRADE_MAX.get());
        int seconds = CommonConfig.ECHO_RECORD_SECONDS.get() + levels * CommonConfig.ECHO_RECORD_BONUS_SECONDS.get();
        return Math.min(EchoRecording.MAX_FRAMES, seconds * 20);
    }

    /** Extra max health of this player's echo bodies. */
    public static double bonusHealth(Player player) {
        return bonusHealth(of(player).sturdy());
    }

    public static double bonusHealth(int sturdyLevel) {
        return Math.min(sturdyLevel, CommonConfig.ECHO_STURDY_UPGRADE_MAX.get()) * CommonConfig.ECHO_STURDY_HEALTH_BONUS.get();
    }
}
