package com.mnemolith.vault;

import com.mnemolith.imprint.Imprint;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The imprints an archive vault holds, on the block entity and on the item when the vault is broken and carried. */
public record VaultContents(List<Imprint> imprints) {
    public static final VaultContents EMPTY = new VaultContents(List.of());
    public static final Codec<VaultContents> CODEC = Codec.list(Imprint.CODEC, 0, 64).xmap(VaultContents::new, VaultContents::imprints);
    public static final StreamCodec<ByteBuf, VaultContents> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public VaultContents {
        imprints = List.copyOf(imprints);
    }

    public boolean isEmpty() {
        return this.imprints.isEmpty();
    }

    public int size() {
        return this.imprints.size();
    }
}
