package com.mnemolith.network;

import java.util.List;

import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.imprint.ImprintTag;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server values the client UI needs. Common config is not synced by NeoForge, and datapack drum formulas only
 * load on the server, so without this a dedicated-server client would use its own local numbers and the
 * built-in formula list.
 */
public record ServerTuningPayload(int possessRange, int mineMaxRadius, List<CompositionRecipe> formulas) implements CustomPacketPayload {
    public static final Type<ServerTuningPayload> TYPE = PayloadIds.type("server_tuning");

    private static final int MAX_FORMULAS = 256;
    private static final int MAX_TAGS = 16;

    private static final StreamCodec<ByteBuf, CompositionRecipe> FORMULA_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CompositionRecipe::id,
            ImprintTag.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TAGS)), CompositionRecipe::tags,
            ImprintTag.STREAM_CODEC, CompositionRecipe::product,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), CompositionRecipe::resultItem,
            CompositionRecipe::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerTuningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerTuningPayload::possessRange,
            ByteBufCodecs.VAR_INT, ServerTuningPayload::mineMaxRadius,
            FORMULA_CODEC.apply(ByteBufCodecs.list(MAX_FORMULAS)), ServerTuningPayload::formulas,
            ServerTuningPayload::new);

    public ServerTuningPayload {
        formulas = List.copyOf(formulas.size() > MAX_FORMULAS ? formulas.subList(0, MAX_FORMULAS) : formulas);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
