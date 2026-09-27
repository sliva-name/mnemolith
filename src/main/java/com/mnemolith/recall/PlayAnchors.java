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
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Traces this world's play left behind, per player. Saved with the server, not scanned from chunks.
 * A sticky kind is kept once. Fracture, gesture traces and recall traces age out and yield to the cap first.
 */
public final class PlayAnchors extends SavedData {
    private static final Codec<List<Anchor>> LIST_CODEC = Anchor.CODEC.listOf();
    private static final Codec<Map<UUID, List<Anchor>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, LIST_CODEC);

    public static final Codec<PlayAnchors> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MAP_CODEC.optionalFieldOf("owners", Map.of()).forGetter(saved -> saved.owners))
            .apply(instance, PlayAnchors::new));

    public static final SavedDataType<PlayAnchors> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "play_anchors"), PlayAnchors::new, CODEC);

    private final Map<UUID, List<Anchor>> owners = new HashMap<>();

    public PlayAnchors() {}

    private PlayAnchors(Map<UUID, List<Anchor>> saved) {
        saved.forEach((owner, anchors) -> this.owners.put(owner, new ArrayList<>(anchors)));
    }

    public static PlayAnchors get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public int size(UUID owner) {
        List<Anchor> list = this.owners.get(owner);
        return list == null ? 0 : list.size();
    }

    public int count(UUID owner, AnchorKind kind) {
        int count = 0;
        for (Anchor anchor : this.owners.getOrDefault(owner, List.of())) {
            if (anchor.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    public int loudPressure(UUID owner) {
        for (Anchor anchor : this.owners.getOrDefault(owner, List.of())) {
            if (anchor.kind() == AnchorKind.LOUD) {
                return anchor.pressure();
            }
        }
        return -1;
    }

    public boolean has(UUID owner, AnchorKind kind) {
        return this.count(owner, kind) > 0;
    }

    /**
     * Adds {@code anchor} unless a sticky kind is already kept, a fracture is already noted in that chunk,
     * a trace of the same kind is already within two blocks, or a non-sticky anchor is already past {@code maxAge}.
     * A louder {@link AnchorKind#LOUD} replaces the previous one.
     */
    public boolean add(Anchor anchor, long now, int cap, long maxAge) {
        if (!anchor.kind().sticky() && maxAge > 0L && now - anchor.gameTime() > maxAge) {
            return false;
        }
        List<Anchor> list = this.owners.computeIfAbsent(anchor.owner(), id -> new ArrayList<>());
        this.purge(list, now, maxAge);
        if (anchor.kind() == AnchorKind.LOUD) {
            Anchor previous = this.findKind(list, AnchorKind.LOUD);
            if (previous != null && anchor.pressure() <= previous.pressure()) {
                return false;
            }
            if (previous != null) {
                list.remove(previous);
            }
        } else if (anchor.kind().sticky() && this.findKind(list, anchor.kind()) != null) {
            return false;
        } else if (anchor.kind() == AnchorKind.FRACTURE && this.inChunk(list, anchor)) {
            return false;
        } else if ((anchor.kind() == AnchorKind.FLASH || anchor.kind() == AnchorKind.RECALL) && this.near(list, anchor)) {
            return false;
        }
        list.add(anchor);
        this.trim(list, cap);
        if (list.isEmpty()) {
            this.owners.remove(anchor.owner());
        }
        this.setDirty();
        return true;
    }

    public Optional<Anchor> nearest(UUID owner, String dimension, BlockPos pos, double maxSqr, long now, long maxAge) {
        List<Anchor> list = this.owners.get(owner);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        if (this.purge(list, now, maxAge)) {
            if (list.isEmpty()) {
                this.owners.remove(owner);
            }
            this.setDirty();
        }
        Anchor best = null;
        double bestDistance = maxSqr;
        for (Anchor anchor : list) {
            if (!dimension.equals(anchor.dimension())) {
                continue;
            }
            double distance = anchor.pos().distSqr(pos);
            if (distance > bestDistance) {
                continue;
            }
            best = anchor;
            bestDistance = distance;
        }
        return Optional.ofNullable(best);
    }

    /** Nearest anchor inside {@code blocks}, ignoring the two-block read radius. Used for the footstep mark. */
    public Optional<Anchor> within(UUID owner, String dimension, BlockPos pos, double blocks, long now, long maxAge) {
        return this.nearest(owner, dimension, pos, blocks * blocks, now, maxAge);
    }

    public void forget(UUID owner) {
        if (this.owners.remove(owner) != null) {
            this.setDirty();
        }
    }

    private boolean purge(List<Anchor> list, long now, long maxAge) {
        if (maxAge <= 0L) {
            return false;
        }
        return list.removeIf(anchor -> !anchor.kind().sticky() && now - anchor.gameTime() > maxAge);
    }

    private void trim(List<Anchor> list, int cap) {
        int limit = Math.max(1, cap);
        while (list.size() > limit) {
            int drop = this.oldest(list, false);
            if (drop < 0) {
                drop = this.oldest(list, true);
            }
            if (drop < 0) {
                return;
            }
            list.remove(drop);
        }
    }

    /** Oldest index, or -1. {@code sticky} selects which group is eligible. */
    private int oldest(List<Anchor> list, boolean sticky) {
        int best = -1;
        long time = Long.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Anchor anchor = list.get(i);
            if (anchor.kind().sticky() != sticky) {
                continue;
            }
            if (anchor.gameTime() < time) {
                time = anchor.gameTime();
                best = i;
            }
        }
        return best;
    }

    private Anchor findKind(List<Anchor> list, AnchorKind kind) {
        for (Anchor anchor : list) {
            if (anchor.kind() == kind) {
                return anchor;
            }
        }
        return null;
    }

    private boolean inChunk(List<Anchor> list, Anchor candidate) {
        int chunkX = candidate.pos().getX() >> 4;
        int chunkZ = candidate.pos().getZ() >> 4;
        for (Anchor anchor : list) {
            if (anchor.kind() != AnchorKind.FRACTURE || !anchor.dimension().equals(candidate.dimension())) {
                continue;
            }
            if ((anchor.pos().getX() >> 4) == chunkX && (anchor.pos().getZ() >> 4) == chunkZ) {
                return true;
            }
        }
        return false;
    }

    private boolean near(List<Anchor> list, Anchor candidate) {
        for (Anchor anchor : list) {
            if (anchor.kind() != candidate.kind() || !anchor.dimension().equals(candidate.dimension())) {
                continue;
            }
            if (anchor.pos().distSqr(candidate.pos()) <= 4.0D) {
                return true;
            }
        }
        return false;
    }
}
