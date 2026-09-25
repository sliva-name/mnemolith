package com.mnemolith.echo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which echoes exist, per owner, and the current generation of each. Saved with the server's global data.
 * <p>
 * An echo entity that finds itself missing here, or older than the stored generation, is a stale copy
 * (for example a chunk that was saved before a possession, then reloaded after a crash) and removes itself
 * without dropping anything. That is the anti-duplication rule for bodies that were not in the player file.
 */
public final class EchoRegistry extends SavedData {
    public record Entry(UUID echo, long generation) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("echo").forGetter(Entry::echo),
                Codec.LONG.fieldOf("generation").forGetter(Entry::generation))
                .apply(instance, Entry::new));
    }

    private static final Codec<Map<UUID, List<Entry>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC.listOf());
    public static final Codec<EchoRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MAP_CODEC.optionalFieldOf("owners", Map.of()).forGetter(registry -> registry.owners))
            .apply(instance, EchoRegistry::new));
    public static final SavedDataType<EchoRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "echoes"), EchoRegistry::new, CODEC);

    private final Map<UUID, List<Entry>> owners = new HashMap<>();

    public EchoRegistry() {}

    private EchoRegistry(Map<UUID, List<Entry>> saved) {
        saved.forEach((owner, entries) -> this.owners.put(owner, new ArrayList<>(entries)));
    }

    public static EchoRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public int count(@Nullable UUID owner) {
        if (owner == null) {
            return 0;
        }
        List<Entry> entries = this.owners.get(owner);
        return entries == null ? 0 : entries.size();
    }

    public List<Entry> entries(UUID owner) {
        return List.copyOf(this.owners.getOrDefault(owner, List.of()));
    }

    public @Nullable Entry find(@Nullable UUID owner, UUID echo) {
        if (owner == null) {
            return null;
        }
        for (Entry entry : this.owners.getOrDefault(owner, List.of())) {
            if (entry.echo().equals(echo)) {
                return entry;
            }
        }
        return null;
    }

    public boolean isCurrent(@Nullable UUID owner, UUID echo, long generation) {
        Entry entry = this.find(owner, echo);
        return entry != null && entry.generation() == generation;
    }

    /** Records a live body. Returns the generation it must carry. */
    public long put(UUID owner, UUID echo) {
        List<Entry> entries = this.owners.computeIfAbsent(owner, key -> new ArrayList<>());
        long generation = 0L;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).echo().equals(echo)) {
                generation = entries.get(i).generation() + 1L;
                entries.remove(i);
                break;
            }
        }
        entries.add(new Entry(echo, generation));
        this.setDirty();
        return generation;
    }

    /** Bumps the generation without a live body (the body is being possessed). */
    public void retire(UUID owner, UUID echo) {
        this.put(owner, echo);
    }

    public void remove(@Nullable UUID owner, UUID echo) {
        if (owner == null) {
            return;
        }
        List<Entry> entries = this.owners.get(owner);
        if (entries != null && entries.removeIf(entry -> entry.echo().equals(echo))) {
            if (entries.isEmpty()) {
                this.owners.remove(owner);
            }
            this.setDirty();
        }
    }

    public int forget(UUID owner) {
        List<Entry> removed = this.owners.remove(owner);
        this.setDirty();
        return removed == null ? 0 : removed.size();
    }
}
