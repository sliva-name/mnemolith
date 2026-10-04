package com.mnemolith.imprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.stream.Stream;

/**
 * Structure-site flags packed into one codec field so {@link ChunkMemory} stays within DFU's 16-arity group.
 * Reads legacy top-level {@code observatory} / {@code residue_seeded} booleans when {@code landmarks} is absent.
 */
public record Landmarks(boolean observatory, boolean hushChapel, boolean memoryField, boolean residueSeeded, boolean structureSeeded) {
    public static final Landmarks NONE = new Landmarks(false, false, false, false, false);

    public static final Codec<Landmarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("observatory", false).forGetter(Landmarks::observatory),
            Codec.BOOL.optionalFieldOf("hush_chapel", false).forGetter(Landmarks::hushChapel),
            Codec.BOOL.optionalFieldOf("memory_field", false).forGetter(Landmarks::memoryField),
            Codec.BOOL.optionalFieldOf("residue_seeded", false).forGetter(Landmarks::residueSeeded),
            Codec.BOOL.optionalFieldOf("structure_seeded", false).forGetter(Landmarks::structureSeeded)
    ).apply(instance, Landmarks::new));

    public static final MapCodec<Landmarks> FIELD = new MapCodec<>() {
        @Override
        public <T> DataResult<Landmarks> decode(DynamicOps<T> ops, MapLike<T> input) {
            T nested = input.get("landmarks");
            if (nested != null) {
                return CODEC.parse(ops, nested);
            }
            boolean observatory = false;
            boolean residue = false;
            T observatoryNode = input.get("observatory");
            if (observatoryNode != null) {
                observatory = Codec.BOOL.parse(ops, observatoryNode).result().orElse(false);
            }
            T residueNode = input.get("residue_seeded");
            if (residueNode != null) {
                residue = Codec.BOOL.parse(ops, residueNode).result().orElse(false);
            }
            return DataResult.success(new Landmarks(observatory, false, false, residue, false));
        }

        @Override
        public <T> RecordBuilder<T> encode(Landmarks value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            Landmarks marks = value == null ? NONE : value;
            prefix.add("landmarks", CODEC.encodeStart(ops, marks));
            // Legacy flat keys so older builds still see observatory / residue_seeded.
            prefix.add("observatory", ops.createBoolean(marks.observatory()));
            prefix.add("residue_seeded", ops.createBoolean(marks.residueSeeded()));
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(
                    ops.createString("landmarks"),
                    ops.createString("observatory"),
                    ops.createString("residue_seeded"));
        }
    };
}
