package com.mnemolith.echo.storm;

import com.mnemolith.Mnemolith;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World flag for the Archive Guardian (B4): whether the shrine boss has been defeated once, and when the last
 * challenge started (cooldown). Stored with the server's global data.
 */
public final class ArchiveGuardianData extends SavedData {
    public static final Codec<ArchiveGuardianData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("defeated", false).forGetter(data -> data.defeated),
            Codec.LONG.optionalFieldOf("last_challenge_at", 0L).forGetter(data -> data.lastChallengeAt))
            .apply(instance, ArchiveGuardianData::new));
    public static final SavedDataType<ArchiveGuardianData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "archive_guardian"), ArchiveGuardianData::new, CODEC);

    private boolean defeated;
    private long lastChallengeAt;

    public ArchiveGuardianData() {}

    private ArchiveGuardianData(boolean defeated, long lastChallengeAt) {
        this.defeated = defeated;
        this.lastChallengeAt = lastChallengeAt;
    }

    public static ArchiveGuardianData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean defeated() {
        return this.defeated;
    }

    public void setDefeated(boolean defeated) {
        this.defeated = defeated;
        this.setDirty();
    }

    public long lastChallengeAt() {
        return this.lastChallengeAt;
    }

    public void setLastChallengeAt(long ticks) {
        this.lastChallengeAt = ticks;
        this.setDirty();
    }
}
