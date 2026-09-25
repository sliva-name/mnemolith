package com.mnemolith.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;

/** Portable copy of an imprint, stored on an imprint slip. */
public record ImprintCast(ImprintTag tag, int intensity, BlockPos origin, Optional<UUID> player, int contextHash, long writtenAt) {
    /** Same fields and names as {@link Imprint#CODEC}, so a saved slip still loads. */
    public static final Codec<ImprintCast> CODEC = Imprint.CODEC.xmap(ImprintCast::from, ImprintCast::toImprint);

    public static final StreamCodec<RegistryFriendlyByteBuf, ImprintCast> STREAM_CODEC = StreamCodec.composite(
            ImprintTag.STREAM_CODEC, ImprintCast::tag,
            ByteBufCodecs.VAR_INT, ImprintCast::intensity,
            BlockPos.STREAM_CODEC, ImprintCast::origin,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ImprintCast::player,
            ByteBufCodecs.VAR_INT, ImprintCast::contextHash,
            ByteBufCodecs.VAR_LONG, ImprintCast::writtenAt,
            ImprintCast::new);

    public static ImprintCast from(Imprint imprint) {
        return new ImprintCast(imprint.tag(), imprint.intensity(), imprint.origin(), imprint.player(), imprint.contextHash(), imprint.writtenAt());
    }

    public Imprint toImprint() {
        return new Imprint(this.tag, this.intensity, this.origin, this.player, this.contextHash, this.writtenAt);
    }
}
