package com.mnemolith.recall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * One legendary offer per player, plus the choices that already closed a place.
 * Saved with the server beside {@link PlayAnchors}, not scanned from chunks.
 */
public final class Legends extends SavedData {
    /** A place that will not be offered again. */
    public record Spot(String dimension, BlockPos pos) {
        public static final Codec<Spot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Spot::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos)
        ).apply(instance, Spot::new));

        public Spot {
            pos = pos.immutable();
            dimension = dimension == null ? "" : dimension;
        }
    }

    /**
     * {@code spent} is a bit per {@link AnchorKind} ordinal. {@code sealed} is the gesture-log size at which an
     * echo's bearing was cleared, or -1. {@code soured} is set by leaving, and makes the next offer more likely to lie.
     */
    public record Sheet(Optional<Legend> current, int spent, boolean soured, int sealed, List<Spot> refused) {
        public static final Sheet EMPTY = new Sheet(Optional.empty(), 0, false, -1, List.of());
        public static final Codec<Sheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Legend.CODEC.optionalFieldOf("current").forGetter(Sheet::current),
                Codec.INT.optionalFieldOf("spent", 0).forGetter(Sheet::spent),
                Codec.BOOL.optionalFieldOf("soured", false).forGetter(Sheet::soured),
                Codec.INT.optionalFieldOf("sealed", -1).forGetter(Sheet::sealed),
                Spot.CODEC.listOf().optionalFieldOf("refused", List.of()).forGetter(Sheet::refused)
        ).apply(instance, Sheet::new));

        public Sheet {
            refused = List.copyOf(refused);
        }
    }

    private static final Codec<Map<UUID, Sheet>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Sheet.CODEC);

    public static final Codec<Legends> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MAP_CODEC.optionalFieldOf("owners", Map.of()).forGetter(saved -> saved.owners))
            .apply(instance, Legends::new));

    public static final SavedDataType<Legends> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "legends"), Legends::new, CODEC);

    private final Map<UUID, Sheet> owners = new HashMap<>();

    public Legends() {}

    private Legends(Map<UUID, Sheet> saved) {
        this.owners.putAll(saved);
    }

    public static Legends get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Sheet sheet(UUID owner) {
        return this.owners.getOrDefault(owner, Sheet.EMPTY);
    }

    public void put(UUID owner, Sheet sheet) {
        if (sheet.current().isEmpty() && sheet.spent() == 0 && !sheet.soured() && sheet.sealed() < 0 && sheet.refused().isEmpty()) {
            this.forget(owner);
            return;
        }
        this.owners.put(owner, sheet);
        this.setDirty();
    }

    public void forget(UUID owner) {
        if (this.owners.remove(owner) != null) {
            this.setDirty();
        }
    }

    public static List<Spot> trim(List<Spot> refused, Spot added, int cap) {
        List<Spot> next = new ArrayList<>(refused.size() + 1);
        next.addAll(refused);
        next.add(added);
        int limit = Math.max(1, cap);
        while (next.size() > limit) {
            next.remove(0);
        }
        return next;
    }
}
